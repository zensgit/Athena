#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

ECM_UI_URL="${ECM_UI_URL:-http://localhost:5500}"
PW_WORKERS="${PW_WORKERS:-1}"
PW_PROJECT="${PW_PROJECT:-chromium}"
PHASE5_USE_EXISTING_UI="${PHASE5_USE_EXISTING_UI:-0}"
PHASE5_RECOVERY_GUARD_STRICT="${PHASE5_RECOVERY_GUARD_STRICT:-0}"

echo "phase5_regression: start"
echo "ECM_UI_URL=${ECM_UI_URL}"
echo "PW_PROJECT=${PW_PROJECT} PW_WORKERS=${PW_WORKERS}"
echo "PHASE5_USE_EXISTING_UI=${PHASE5_USE_EXISTING_UI}"
echo "PHASE5_RECOVERY_GUARD_STRICT=${PHASE5_RECOVERY_GUARD_STRICT}"

strip_ansi_file() {
  local log_file="$1"
  sed -E $'s/\x1B\\[[0-9;]*[[:alpha:]]//g' "${log_file}" | tr -d '\r'
}

print_playwright_failure_summary() {
  local log_file="$1"
  local failure_lines=()

  mapfile -t failure_lines < <(
    strip_ansi_file "${log_file}" \
      | rg "^[[:space:]]*\\[[^]]+\\][[:space:]]+›[[:space:]]+e2e/" \
      | sed -E 's/^[[:space:]]*\[[^]]+\][[:space:]]+›[[:space:]]+//'
  )

  if [[ "${#failure_lines[@]}" -gt 0 ]]; then
    echo "phase5_regression: failed specs summary"
    local line
    for line in "${failure_lines[@]}"; do
      echo " - ${line}"
    done
    return
  fi

  local first_error
  first_error="$(strip_ansi_file "${log_file}" | rg -m1 "(^Error:|^error:|error: )" || true)"
  if [[ -n "${first_error}" ]]; then
    echo "phase5_regression: first error => ${first_error}"
  fi
}

print_playwright_timing_summary() {
  local log_file="$1"
  node - "${log_file}" "${PHASE5_RECOVERY_GUARD_STRICT}" <<'NODE'
const fs = require('fs');

const logFile = process.argv[2];
const strictMode = process.argv[3] || '0';
if (!logFile || !fs.existsSync(logFile)) {
  process.exit(0);
}

const content = fs.readFileSync(logFile, 'utf8').replace(/\r/g, '');
const lines = content.split('\n');
const specLinePattern = /^\s*[✓✘]\s+\d+\s+\[[^\]]+\]\s+›\s+(e2e\/[^:]+):\d+:\d+\s+›\s+(.+)\s+\(([\d.]+)(ms|s|m)\)\s*$/;

const tests = [];
for (const line of lines) {
  const match = line.match(specLinePattern);
  if (!match) continue;
  const [, spec, title, rawDuration, unit] = match;
  let durationSec = Number.parseFloat(rawDuration);
  if (!Number.isFinite(durationSec)) continue;
  if (unit === 'ms') durationSec /= 1000;
  if (unit === 'm') durationSec *= 60;
  tests.push({ spec, title, durationSec });
}

if (tests.length === 0) {
  process.exit(0);
}

tests.sort((left, right) => right.durationSec - left.durationSec);
console.log('phase5_regression: duration hotspots (top 5)');
for (const item of tests.slice(0, 5)) {
  console.log(` - ${item.spec} :: ${item.title} (${item.durationSec.toFixed(1)}s)`);
}

const riskCandidates = tests
  .map((item) => {
    let score = 0;
    if (item.durationSec >= 12) score += 3;
    else if (item.durationSec >= 8) score += 2;
    else if (item.durationSec >= 5) score += 1;

    const normalized = `${item.spec} ${item.title}`.toLowerCase();
    if (/watchdog|auth|session|mail automation|trigger fetch|search suggestions/.test(normalized)) {
      score += 1;
    }
    return { ...item, score };
  })
  .filter((item) => item.score > 0)
  .sort((left, right) => right.score - left.score || right.durationSec - left.durationSec);

if (riskCandidates.length > 0) {
  console.log('phase5_regression: flaky-risk candidates (heuristic)');
  for (const item of riskCandidates.slice(0, 3)) {
    console.log(` - score ${item.score}: ${item.spec} :: ${item.title} (${item.durationSec.toFixed(1)}s)`);
  }
} else {
  console.log('phase5_regression: flaky-risk candidates (heuristic): none');
}

const retrySignals = lines.filter((line) => /retry #\d+/i.test(line)).length;
if (retrySignals > 0) {
  console.log(`phase5_regression: retry signals observed in run: ${retrySignals}`);
}

const startupSlaLinePattern = /^\s*startup_sla:[a-z_]+_ms=[0-9]+:threshold_ms=[0-9]+\s*$/;
const startupSlaLines = lines.filter((line) => startupSlaLinePattern.test(line));
if (startupSlaLines.length > 0) {
  const parsePositiveInt = (rawValue) => {
    const value = Number.parseInt(rawValue ?? '', 10);
    return Number.isFinite(value) && value > 0 ? value : null;
  };
  const baselineOverrides = {
    login_visible:
      parsePositiveInt(process.env.ECM_STARTUP_SLA_BASELINE_LOGIN_VISIBLE_MS)
      ?? parsePositiveInt(process.env.ECM_STARTUP_SLA_BASELINE_LOGIN_MS),
    browse_visible:
      parsePositiveInt(process.env.ECM_STARTUP_SLA_BASELINE_BROWSE_VISIBLE_MS)
      ?? parsePositiveInt(process.env.ECM_STARTUP_SLA_BASELINE_BROWSE_MS),
  };
  const defaultBaselines = {
    login_visible: 1500,
    browse_visible: 1800,
  };
  const resolveBaselineMs = (name, thresholdMs) => {
    const override = baselineOverrides[name];
    if (override && override > 0) {
      return override;
    }
    const baseline = defaultBaselines[name];
    if (baseline && baseline > 0) {
      return baseline;
    }
    return Math.max(500, Math.floor(thresholdMs * 0.2));
  };

  console.log('phase5_regression: startup SLA samples');
  for (const line of startupSlaLines) {
    console.log(` - ${line.trim()}`);
  }

  const startupSlaPattern = /startup_sla:([a-z_]+)_ms=([0-9]+):threshold_ms=([0-9]+)/;
  const startupSlaStatus = [];
  for (const line of startupSlaLines) {
    const match = line.match(startupSlaPattern);
    if (!match) continue;
    const [, name, elapsedRaw, thresholdRaw] = match;
    const elapsedMs = Number.parseInt(elapsedRaw, 10);
    const thresholdMs = Number.parseInt(thresholdRaw, 10);
    if (!Number.isFinite(elapsedMs) || !Number.isFinite(thresholdMs) || thresholdMs <= 0) {
      continue;
    }
    const ratio = elapsedMs / thresholdMs;
    const isWarn = elapsedMs > thresholdMs || ratio >= 0.8;
    const baselineMs = resolveBaselineMs(name, thresholdMs);
    const driftDeltaMs = elapsedMs - baselineMs;
    const driftRatio = baselineMs > 0 ? elapsedMs / baselineMs : 1;
    const driftWarn = driftDeltaMs >= 700 || driftRatio >= 1.35;
    startupSlaStatus.push({
      name,
      elapsedMs,
      thresholdMs,
      ratio,
      level: isWarn ? 'WARN' : 'OK',
      baselineMs,
      driftDeltaMs,
      driftRatio,
      driftLevel: driftWarn ? 'WARN' : 'OK',
    });
  }

  if (startupSlaStatus.length > 0) {
    console.log('phase5_regression: startup SLA status');
    for (const sample of startupSlaStatus) {
      console.log(
        ` - ${sample.level} ${sample.name}: ${sample.elapsedMs}ms / ${sample.thresholdMs}ms (${(sample.ratio * 100).toFixed(1)}%)`
      );
    }
    const warnCount = startupSlaStatus.filter((item) => item.level === 'WARN').length;
    console.log(`phase5_regression: startup SLA warning count: ${warnCount}`);

    console.log('phase5_regression: startup SLA drift vs baseline');
    for (const sample of startupSlaStatus) {
      const driftSign = sample.driftDeltaMs >= 0 ? '+' : '';
      console.log(
        ` - ${sample.driftLevel} ${sample.name}: ${sample.elapsedMs}ms vs baseline ${sample.baselineMs}ms (${driftSign}${sample.driftDeltaMs}ms, ${(sample.driftRatio * 100).toFixed(1)}%)`
      );
    }
    const driftWarnCount = startupSlaStatus.filter((item) => item.driftLevel === 'WARN').length;
    console.log(`phase5_regression: startup SLA drift warning count: ${driftWarnCount}`);
  }
}

const recoveryEventPattern = /^\s*recovery_event:([a-z0-9_]+)\s*$/i;
const recoveryEvents = [];
for (const line of lines) {
  const match = line.match(recoveryEventPattern);
  if (!match) continue;
  recoveryEvents.push(match[1].toLowerCase());
}

const recoverySummary = new Map();
for (const eventName of recoveryEvents) {
  recoverySummary.set(eventName, (recoverySummary.get(eventName) ?? 0) + 1);
}

console.log('phase5_regression: recovery events');
if (recoverySummary.size === 0) {
  console.log(' - (none)');
} else {
  const sorted = Array.from(recoverySummary.entries()).sort((left, right) => left[0].localeCompare(right[0]));
  for (const [eventName, count] of sorted) {
    console.log(` - ${eventName}: ${count}`);
  }
}

const expectedEvents = [
  'auth_session_transient_retry_success',
  'auth_session_terminal_redirect_login',
  'search_recoverable_error_alert_shown',
  'search_recoverable_retry_success',
  'app_error_noise_resize_observer_ignored',
  'app_error_noise_abort_rejection_ignored',
  'route_fallback_unauth_login_visible',
  'route_fallback_auth_browse_visible',
  'filebrowser_watchdog_alert_shown',
  'filebrowser_watchdog_retry_recovered',
  'folder_tree_watchdog_alert_shown',
  'folder_tree_watchdog_retry_recovered',
  'auth_storage_restricted_browse_recovered',
  'auth_storage_restricted_login_notice_visible',
  'auth_boot_watchdog_alert_shown',
  'auth_boot_watchdog_continue_login',
  'app_error_overlay_shown',
  'app_error_back_to_login',
  'chunk_load_hint_shown',
  'chunk_load_reload_cache_bust',
  'startup_fallback_overlay_shown',
  'startup_fallback_reload_cache_bust',
  'startup_fallback_back_to_login',
  'startup_fallback_not_shown_normal',
];
const expectedEventSet = new Set(expectedEvents);
const missingEvents = expectedEvents.filter((eventName) => !recoverySummary.has(eventName));
const unexpectedEvents = Array.from(recoverySummary.keys()).filter((eventName) => !expectedEventSet.has(eventName));

console.log('phase5_regression: recovery guard status');
if (missingEvents.length === 0) {
  console.log(' - OK all expected recovery events observed');
} else {
  for (const missing of missingEvents) {
    console.log(` - WARN missing event: ${missing}`);
  }
}
if (unexpectedEvents.length > 0) {
  for (const unexpected of unexpectedEvents.sort((left, right) => left.localeCompare(right))) {
    console.log(` - WARN unexpected event: ${unexpected}`);
  }
}
const guardWarningCount = missingEvents.length + unexpectedEvents.length;
console.log(`phase5_regression: recovery guard warning count: ${guardWarningCount}`);
if (strictMode === '1' && guardWarningCount > 0) {
  console.log('phase5_regression: strict recovery guard failed');
  process.exit(2);
}
NODE
}

run_with_tee() {
  local log_file="$1"
  shift

  set +e
  "$@" 2>&1 | tee "${log_file}"
  local cmd_rc="${PIPESTATUS[0]:-1}"
  local tee_rc="${PIPESTATUS[1]:-0}"
  set -e

  if [[ "${cmd_rc}" -ne 0 ]]; then
    return "${cmd_rc}"
  fi
  if [[ "${tee_rc}" -ne 0 ]]; then
    return "${tee_rc}"
  fi
  return 0
}

extract_host() {
  local url="$1"
  printf '%s' "$url" | sed -E 's#^https?://([^/:]+).*#\1#'
}

extract_port() {
  local url="$1"
  local parsed_port
  parsed_port="$(printf '%s' "$url" | sed -nE 's#^https?://[^/:]+:([0-9]+).*$#\1#p')"
  if [[ -n "${parsed_port}" ]]; then
    printf '%s' "${parsed_port}"
    return
  fi
  if [[ "$url" == https://* ]]; then
    printf '443'
  else
    printf '80'
  fi
}

EFFECTIVE_ECM_UI_URL="${ECM_UI_URL}"
srv_pid=""
srv_log=""

start_ephemeral_static_server() {
  if ! command -v npx >/dev/null 2>&1; then
    return 1
  fi
  srv_log="/tmp/phase5-regression.http.$$.$RANDOM.log"
  npx serve -s build -l 0 >"${srv_log}" 2>&1 &
  srv_pid=$!

  local discovered_url=""
  for _ in $(seq 1 60); do
    if [[ -f "${srv_log}" ]]; then
      discovered_url="$(sed -nE 's#.*(http://localhost:[0-9]+).*#\1#p' "${srv_log}" | tail -n 1)"
    fi
    if [[ -n "${discovered_url}" ]] && curl -fsS --max-time 1 "${discovered_url}" >/dev/null 2>&1; then
      EFFECTIVE_ECM_UI_URL="${discovered_url}"
      return 0
    fi
    if ! kill -0 "${srv_pid}" >/dev/null 2>&1; then
      break
    fi
    sleep 0.25
  done
  return 1
}

# This gate is intentionally "mocked-first" so it can run without Docker/backend.
PHASE5_SPECS=(
  "e2e/admin-preview-diagnostics.mock.spec.ts"
  "e2e/permissions-dialog-presets.mock.spec.ts"
  "e2e/admin-audit-filter-export.mock.spec.ts"
  "e2e/version-history-paging-major-only.mock.spec.ts"
  "e2e/app-error-boundary-recovery.mock.spec.ts"
  "e2e/app-error-boundary-noise-filter.mock.spec.ts"
  "e2e/app-error-boundary-chunk-load-recovery.mock.spec.ts"
  "e2e/route-fallback-no-blank.mock.spec.ts"
  "e2e/startup-visibility-sla.mock.spec.ts"
  "e2e/search-suggestions-save-search.mock.spec.ts"
  "e2e/settings-session-actions.mock.spec.ts"
  "e2e/auth-session-recovery.mock.spec.ts"
  "e2e/auth-storage-restricted-recovery.mock.spec.ts"
  "e2e/auth-boot-watchdog-recovery.mock.spec.ts"
  "e2e/bootstrap-startup-fallback.mock.spec.ts"
  "e2e/folder-tree-root-watchdog.mock.spec.ts"
  "e2e/filebrowser-loading-watchdog.mock.spec.ts"
  "e2e/mail-automation-trigger-fetch.mock.spec.ts"
  "e2e/mail-automation-diagnostics-export.mock.spec.ts"
  "e2e/mail-automation-processed-management.mock.spec.ts"
  "e2e/mail-automation-phase6-p1.mock.spec.ts"
)

if [[ ! -d "ecm-frontend" ]]; then
  echo "error: missing ecm-frontend/"
  exit 1
fi

cd ecm-frontend

echo "phase5_regression: build frontend"
npm run build

echo "phase5_regression: ensure static server reachable"
ui_host="$(extract_host "${ECM_UI_URL}")"
ui_port="$(extract_port "${ECM_UI_URL}")"

if [[ "${PHASE5_USE_EXISTING_UI}" != "1" ]] && [[ "${ui_host}" == "localhost" || "${ui_host}" == "127.0.0.1" ]]; then
  echo "phase5_regression: starting dedicated static SPA server (ephemeral port)"
  if ! start_ephemeral_static_server; then
    echo "error: failed to start dedicated static SPA server"
    if [[ -n "${srv_log}" && -f "${srv_log}" ]]; then
      echo "phase5_regression: server log tail"
      tail -n 40 "${srv_log}" || true
    fi
    exit 1
  fi
  trap 'if [[ -n "${srv_pid:-}" ]]; then kill "${srv_pid}" >/dev/null 2>&1 || true; fi' EXIT
  echo "phase5_regression: using dedicated server ${EFFECTIVE_ECM_UI_URL}"
elif ! curl -fsS --max-time 3 "${ECM_UI_URL}" >/dev/null 2>&1; then
  case "${ui_host}" in
    localhost|127.0.0.1)
      echo "phase5_regression: starting static SPA server on :${ui_port}"
      # Prefer `serve -s` for SPA routing; fallback to python for environments without Node package resolution.
      if command -v npx >/dev/null 2>&1; then
        npx serve -s build -l "${ui_port}" >/tmp/phase5-regression.http.log 2>&1 &
        srv_pid=$!
      else
        python3 -m http.server "${ui_port}" --directory build >/tmp/phase5-regression.http.log 2>&1 &
        srv_pid=$!
      fi
      trap 'if [[ -n "${srv_pid:-}" ]]; then kill "${srv_pid}" >/dev/null 2>&1 || true; fi' EXIT

      for _ in $(seq 1 30); do
        if curl -fsS --max-time 1 "${ECM_UI_URL}" >/dev/null 2>&1; then
          break
        fi
        sleep 0.3
      done
      ;;
    *)
      echo "error: ECM_UI_URL not reachable: ${ECM_UI_URL}"
      echo "hint: start a server and set ECM_UI_URL accordingly"
      exit 1
      ;;
  esac
fi

echo "phase5_regression: check e2e target"
ALLOW_STATIC=1 ../scripts/check-e2e-target.sh "${EFFECTIVE_ECM_UI_URL}" || true

echo "phase5_regression: run playwright specs"
playwright_log="$(mktemp "/tmp/phase5-regression.playwright.XXXXXX")"
playwright_rc=0
run_with_tee "${playwright_log}" \
  env ECM_UI_URL="${EFFECTIVE_ECM_UI_URL}" \
  npx playwright test \
  "${PHASE5_SPECS[@]}" \
  --project="${PW_PROJECT}" --workers="${PW_WORKERS}" || playwright_rc=$?
print_playwright_timing_summary "${playwright_log}"
if [[ "${playwright_rc}" -eq 0 ]]; then
  echo "phase5_regression: ok"
else
  print_playwright_failure_summary "${playwright_log}"
  echo "phase5_regression: playwright log => ${playwright_log}"
  exit "${playwright_rc}"
fi
