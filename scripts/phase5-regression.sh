#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

ECM_UI_URL="${ECM_UI_URL:-http://localhost:5500}"
PW_WORKERS="${PW_WORKERS:-1}"
PW_PROJECT="${PW_PROJECT:-chromium}"
PHASE5_USE_EXISTING_UI="${PHASE5_USE_EXISTING_UI:-0}"
PHASE5_RECOVERY_GUARD_STRICT="${PHASE5_RECOVERY_GUARD_STRICT:-0}"
PHASE5_RECOVERY_EVENTS_FILE="${PHASE5_RECOVERY_EVENTS_FILE:-e2e/recovery-events.expected.txt}"
PHASE5_RECOVERY_REGISTRY_STRICT="${PHASE5_RECOVERY_REGISTRY_STRICT:-${PHASE5_RECOVERY_GUARD_STRICT}}"
PHASE5_VALIDATE_RECOVERY_REGISTRY_ONLY="${PHASE5_VALIDATE_RECOVERY_REGISTRY_ONLY:-0}"
PHASE5_RECOVERY_REGISTRY_SYNC="${PHASE5_RECOVERY_REGISTRY_SYNC:-0}"
PHASE5_REGRESSION_SUMMARY_JSON="${PHASE5_REGRESSION_SUMMARY_JSON:-}"
PHASE5_STRICT_HOTSPOT_DURATION_SEC_THRESHOLD="${PHASE5_STRICT_HOTSPOT_DURATION_SEC_THRESHOLD:-0}"
PHASE5_STRICT_FLAKY_RISK_SCORE_THRESHOLD="${PHASE5_STRICT_FLAKY_RISK_SCORE_THRESHOLD:-0}"
PHASE5_RUN_START_EPOCH="$(date +%s)"
PHASE5_RUN_START_ISO="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
PHASE5_ANALYSIS_JSON_FILE=""
PHASE5_PLAYWRIGHT_LOG=""
EFFECTIVE_ECM_UI_URL="${ECM_UI_URL}"
srv_pid=""
srv_log=""

echo "phase5_regression: start"
echo "ECM_UI_URL=${ECM_UI_URL}"
echo "PW_PROJECT=${PW_PROJECT} PW_WORKERS=${PW_WORKERS}"
echo "PHASE5_USE_EXISTING_UI=${PHASE5_USE_EXISTING_UI}"
echo "PHASE5_RECOVERY_GUARD_STRICT=${PHASE5_RECOVERY_GUARD_STRICT}"
echo "PHASE5_RECOVERY_EVENTS_FILE=${PHASE5_RECOVERY_EVENTS_FILE}"
echo "PHASE5_RECOVERY_REGISTRY_STRICT=${PHASE5_RECOVERY_REGISTRY_STRICT}"
echo "PHASE5_VALIDATE_RECOVERY_REGISTRY_ONLY=${PHASE5_VALIDATE_RECOVERY_REGISTRY_ONLY}"
echo "PHASE5_RECOVERY_REGISTRY_SYNC=${PHASE5_RECOVERY_REGISTRY_SYNC}"
echo "PHASE5_REGRESSION_SUMMARY_JSON=${PHASE5_REGRESSION_SUMMARY_JSON:-"(unset)"}"
echo "PHASE5_STRICT_HOTSPOT_DURATION_SEC_THRESHOLD=${PHASE5_STRICT_HOTSPOT_DURATION_SEC_THRESHOLD}"
echo "PHASE5_STRICT_FLAKY_RISK_SCORE_THRESHOLD=${PHASE5_STRICT_FLAKY_RISK_SCORE_THRESHOLD}"

strip_ansi_file() {
  local log_file="$1"
  sed -E $'s/\x1B\\[[0-9;]*[[:alpha:]]//g' "${log_file}" | tr -d '\r'
}

write_phase5_summary_artifact() {
  local exit_code="$1"
  if [[ -z "${PHASE5_REGRESSION_SUMMARY_JSON}" ]]; then
    return 0
  fi

  local summary_dir
  summary_dir="$(dirname "${PHASE5_REGRESSION_SUMMARY_JSON}")"
  if [[ "${summary_dir}" != "." ]]; then
    mkdir -p "${summary_dir}"
  fi

  local run_end_epoch
  local run_end_iso
  local run_duration_seconds
  run_end_epoch="$(date +%s)"
  run_end_iso="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  run_duration_seconds=$((run_end_epoch - PHASE5_RUN_START_EPOCH))

  local spec_count="0"
  if declare -p PHASE5_SPECS >/dev/null 2>&1; then
    spec_count="${#PHASE5_SPECS[@]}"
  fi

  PHASE5_SUMMARY_EXIT_CODE="${exit_code}" \
  PHASE5_RUN_START_EPOCH="${PHASE5_RUN_START_EPOCH}" \
  PHASE5_RUN_START_ISO="${PHASE5_RUN_START_ISO}" \
  PHASE5_RUN_END_EPOCH="${run_end_epoch}" \
  PHASE5_RUN_END_ISO="${run_end_iso}" \
  PHASE5_RUN_DURATION_SECONDS="${run_duration_seconds}" \
  PHASE5_SPEC_COUNT="${spec_count}" \
  PHASE5_EFFECTIVE_ECM_UI_URL="${EFFECTIVE_ECM_UI_URL}" \
  PHASE5_PLAYWRIGHT_LOG_PATH="${PHASE5_PLAYWRIGHT_LOG}" \
  PHASE5_RECOVERY_GUARD_STRICT="${PHASE5_RECOVERY_GUARD_STRICT}" \
  PHASE5_STRICT_HOTSPOT_DURATION_SEC_THRESHOLD="${PHASE5_STRICT_HOTSPOT_DURATION_SEC_THRESHOLD}" \
  PHASE5_STRICT_FLAKY_RISK_SCORE_THRESHOLD="${PHASE5_STRICT_FLAKY_RISK_SCORE_THRESHOLD}" \
  PHASE5_VALIDATE_RECOVERY_REGISTRY_ONLY="${PHASE5_VALIDATE_RECOVERY_REGISTRY_ONLY}" \
  PHASE5_USE_EXISTING_UI="${PHASE5_USE_EXISTING_UI}" \
  PHASE5_RECOVERY_EVENTS_FILE="${PHASE5_RECOVERY_EVENTS_FILE}" \
  ECM_UI_URL="${ECM_UI_URL}" \
  PW_PROJECT="${PW_PROJECT}" \
  PW_WORKERS="${PW_WORKERS}" \
  node - "${PHASE5_REGRESSION_SUMMARY_JSON}" "${PHASE5_ANALYSIS_JSON_FILE:-}" <<'NODE' >/dev/null 2>&1 || {
const fs = require('fs');

const outputFile = process.argv[2];
const analysisFile = process.argv[3] || '';
const parseIntSafe = (rawValue, fallback = 0) => {
  const value = Number.parseInt(rawValue ?? '', 10);
  return Number.isFinite(value) ? value : fallback;
};
const parseNumberSafe = (rawValue, fallback = 0) => {
  const value = Number.parseFloat(rawValue ?? '');
  return Number.isFinite(value) ? value : fallback;
};

let analysis = {};
if (analysisFile && fs.existsSync(analysisFile)) {
  try {
    analysis = JSON.parse(fs.readFileSync(analysisFile, 'utf8'));
  } catch (_error) {
    analysis = {};
  }
}

const durationHotspots = Array.isArray(analysis.duration_hotspots)
  ? analysis.duration_hotspots
      .map((item) => ({
        spec: String(item?.spec ?? ''),
        title: String(item?.title ?? ''),
        duration_sec: parseNumberSafe(item?.duration_sec, 0),
      }))
      .sort((left, right) => right.duration_sec - left.duration_sec || left.spec.localeCompare(right.spec))
  : [];
const flakyRiskCandidates = Array.isArray(analysis.flaky_risk_candidates)
  ? analysis.flaky_risk_candidates
      .map((item) => ({
        spec: String(item?.spec ?? ''),
        title: String(item?.title ?? ''),
        duration_sec: parseNumberSafe(item?.duration_sec, 0),
        score: parseIntSafe(item?.score, 0),
      }))
      .sort(
        (left, right) =>
          right.score - left.score || right.duration_sec - left.duration_sec || left.spec.localeCompare(right.spec)
      )
  : [];
const recoveryMissingEvents = Array.isArray(analysis.recovery_missing_events)
  ? analysis.recovery_missing_events.map((item) => String(item)).sort()
  : [];
const recoveryUnexpectedEvents = Array.isArray(analysis.recovery_unexpected_events)
  ? analysis.recovery_unexpected_events.map((item) => String(item)).sort()
  : [];
const recoveryEventCounts = Array.isArray(analysis.recovery_event_counts)
  ? analysis.recovery_event_counts
      .map((item) => ({
        name: String(item?.name ?? ''),
        count: parseIntSafe(item?.count, 0),
      }))
      .filter((item) => item.name.length > 0)
      .sort((left, right) => left.name.localeCompare(right.name))
  : [];
const strictFailureReasons = Array.isArray(analysis.strict_failure_reasons)
  ? analysis.strict_failure_reasons.map((item) => String(item)).sort()
  : [];

const summary = {
  schema_version: 1,
  run_metadata: {
    script: 'scripts/phase5-regression.sh',
    cwd: process.cwd(),
    start_epoch_seconds: parseIntSafe(process.env.PHASE5_RUN_START_EPOCH),
    start_utc: process.env.PHASE5_RUN_START_ISO || '',
    end_epoch_seconds: parseIntSafe(process.env.PHASE5_RUN_END_EPOCH),
    end_utc: process.env.PHASE5_RUN_END_ISO || '',
    duration_seconds: parseIntSafe(process.env.PHASE5_RUN_DURATION_SECONDS),
    ui_url_configured: process.env.ECM_UI_URL || '',
    ui_url_effective: process.env.PHASE5_EFFECTIVE_ECM_UI_URL || '',
    playwright_project: process.env.PW_PROJECT || '',
    playwright_workers: parseIntSafe(process.env.PW_WORKERS, 1),
    spec_count: parseIntSafe(process.env.PHASE5_SPEC_COUNT),
    validate_registry_only: process.env.PHASE5_VALIDATE_RECOVERY_REGISTRY_ONLY === '1',
    use_existing_ui: process.env.PHASE5_USE_EXISTING_UI === '1',
    recovery_events_file: process.env.PHASE5_RECOVERY_EVENTS_FILE || '',
    playwright_log: process.env.PHASE5_PLAYWRIGHT_LOG_PATH || '',
  },
  strict_mode: process.env.PHASE5_RECOVERY_GUARD_STRICT === '1',
  strict_threshold_controls: {
    hotspot_duration_sec_threshold: parseNumberSafe(process.env.PHASE5_STRICT_HOTSPOT_DURATION_SEC_THRESHOLD, 0),
    flaky_risk_score_threshold: parseIntSafe(process.env.PHASE5_STRICT_FLAKY_RISK_SCORE_THRESHOLD, 0),
    hotspot_match_count: parseIntSafe(analysis.strict_hotspot_match_count, 0),
    flaky_risk_match_count: parseIntSafe(analysis.strict_flaky_risk_match_count, 0),
    strict_guard_failed: Boolean(analysis.strict_guard_failed),
    strict_failure_reasons: strictFailureReasons,
  },
  duration_hotspots: durationHotspots,
  flaky_risk_candidates: flakyRiskCandidates,
  retry_signal_count: parseIntSafe(analysis.retry_signal_count, 0),
  startup_sla: {
    warning_count: parseIntSafe(analysis.startup_sla_warning_count, 0),
    drift_warning_count: parseIntSafe(analysis.startup_sla_drift_warning_count, 0),
  },
  recovery_guard: {
    warning_count: parseIntSafe(analysis.recovery_guard_warning_count, 0),
    missing_events: recoveryMissingEvents,
    unexpected_events: recoveryUnexpectedEvents,
    event_counts: recoveryEventCounts,
  },
  exit_status: parseIntSafe(process.env.PHASE5_SUMMARY_EXIT_CODE, 1),
};

fs.writeFileSync(outputFile, `${JSON.stringify(summary, null, 2)}\n`, 'utf8');
NODE
    echo "phase5_regression: WARN failed to write summary artifact: ${PHASE5_REGRESSION_SUMMARY_JSON}"
    return 0
  }
  echo "phase5_regression: wrote summary artifact => ${PHASE5_REGRESSION_SUMMARY_JSON}"
  return 0
}

phase5_on_exit() {
  local exit_code="$1"
  if [[ -n "${srv_pid:-}" ]]; then
    kill "${srv_pid}" >/dev/null 2>&1 || true
  fi
  write_phase5_summary_artifact "${exit_code}"
  if [[ -n "${PHASE5_ANALYSIS_JSON_FILE:-}" && -f "${PHASE5_ANALYSIS_JSON_FILE}" ]]; then
    rm -f "${PHASE5_ANALYSIS_JSON_FILE}" >/dev/null 2>&1 || true
  fi
}

trap 'phase5_on_exit "$?"' EXIT

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
  local analysis_json_file="${2:-}"
  node - \
    "${log_file}" \
    "${PHASE5_RECOVERY_GUARD_STRICT}" \
    "${PHASE5_RECOVERY_EVENTS_FILE}" \
    "${analysis_json_file}" \
    "${PHASE5_STRICT_HOTSPOT_DURATION_SEC_THRESHOLD}" \
    "${PHASE5_STRICT_FLAKY_RISK_SCORE_THRESHOLD}" <<'NODE'
const fs = require('fs');

const logFile = process.argv[2];
const strictMode = process.argv[3] || '0';
const expectedEventsFile = process.argv[4] || '';
const analysisJsonFile = process.argv[5] || '';
const strictHotspotThresholdRaw = process.argv[6] || '0';
const strictFlakyRiskScoreThresholdRaw = process.argv[7] || '0';

const parsePositiveNumber = (rawValue) => {
  const value = Number.parseFloat(rawValue ?? '');
  return Number.isFinite(value) && value > 0 ? value : 0;
};
const parsePositiveInt = (rawValue) => {
  const value = Number.parseInt(rawValue ?? '', 10);
  return Number.isFinite(value) && value > 0 ? value : 0;
};
const strictHotspotThresholdSeconds = parsePositiveNumber(strictHotspotThresholdRaw);
const strictFlakyRiskScoreThreshold = parsePositiveInt(strictFlakyRiskScoreThresholdRaw);

const analysis = {
  schema_version: 1,
  duration_hotspots: [],
  flaky_risk_candidates: [],
  retry_signal_count: 0,
  startup_sla_warning_count: 0,
  startup_sla_drift_warning_count: 0,
  recovery_guard_warning_count: 0,
  recovery_missing_events: [],
  recovery_unexpected_events: [],
  recovery_event_counts: [],
  strict_hotspot_duration_sec_threshold: strictHotspotThresholdSeconds,
  strict_hotspot_match_count: 0,
  strict_flaky_risk_score_threshold: strictFlakyRiskScoreThreshold,
  strict_flaky_risk_match_count: 0,
  strict_guard_failed: false,
  strict_failure_reasons: [],
};

const persistAnalysis = () => {
  if (!analysisJsonFile) {
    return;
  }
  fs.writeFileSync(analysisJsonFile, `${JSON.stringify(analysis, null, 2)}\n`, 'utf8');
};
const exitWithCode = (exitCode) => {
  persistAnalysis();
  process.exit(exitCode);
};

if (!logFile || !fs.existsSync(logFile)) {
  exitWithCode(0);
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

tests.sort(
  (left, right) =>
    right.durationSec - left.durationSec
    || left.spec.localeCompare(right.spec)
    || left.title.localeCompare(right.title)
);
analysis.duration_hotspots = tests.slice(0, 5).map((item) => ({
  spec: item.spec,
  title: item.title,
  duration_sec: Number(item.durationSec.toFixed(3)),
}));
console.log('phase5_regression: duration hotspots (top 5)');
if (analysis.duration_hotspots.length === 0) {
  console.log(' - (none)');
} else {
  for (const item of analysis.duration_hotspots) {
    console.log(` - ${item.spec} :: ${item.title} (${item.duration_sec.toFixed(1)}s)`);
  }
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
  .sort(
    (left, right) =>
      right.score - left.score
      || right.durationSec - left.durationSec
      || left.spec.localeCompare(right.spec)
      || left.title.localeCompare(right.title)
  );
analysis.flaky_risk_candidates = riskCandidates.slice(0, 3).map((item) => ({
  spec: item.spec,
  title: item.title,
  duration_sec: Number(item.durationSec.toFixed(3)),
  score: item.score,
}));

if (analysis.flaky_risk_candidates.length > 0) {
  console.log('phase5_regression: flaky-risk candidates (heuristic)');
  for (const item of analysis.flaky_risk_candidates) {
    console.log(` - score ${item.score}: ${item.spec} :: ${item.title} (${item.duration_sec.toFixed(1)}s)`);
  }
} else {
  console.log('phase5_regression: flaky-risk candidates (heuristic): none');
}

const retrySignals = lines.filter((line) => /retry #\d+/i.test(line)).length;
analysis.retry_signal_count = retrySignals;
if (retrySignals > 0) {
  console.log(`phase5_regression: retry signals observed in run: ${retrySignals}`);
}

const startupSlaLinePattern = /^\s*startup_sla:[a-z_]+_ms=[0-9]+:threshold_ms=[0-9]+\s*$/;
const startupSlaLines = lines.filter((line) => startupSlaLinePattern.test(line));
if (startupSlaLines.length > 0) {
  const parseBaselineOverride = (rawValue) => {
    const value = Number.parseInt(rawValue ?? '', 10);
    return Number.isFinite(value) && value > 0 ? value : null;
  };
  const baselineOverrides = {
    login_visible:
      parseBaselineOverride(process.env.ECM_STARTUP_SLA_BASELINE_LOGIN_VISIBLE_MS)
      ?? parseBaselineOverride(process.env.ECM_STARTUP_SLA_BASELINE_LOGIN_MS),
    browse_visible:
      parseBaselineOverride(process.env.ECM_STARTUP_SLA_BASELINE_BROWSE_VISIBLE_MS)
      ?? parseBaselineOverride(process.env.ECM_STARTUP_SLA_BASELINE_BROWSE_MS),
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
    analysis.startup_sla_warning_count = warnCount;
    analysis.startup_sla_drift_warning_count = driftWarnCount;
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
  analysis.recovery_event_counts = sorted.map(([name, count]) => ({ name, count }));
  for (const [eventName, count] of sorted) {
    console.log(` - ${eventName}: ${count}`);
  }
}

if (!expectedEventsFile || !fs.existsSync(expectedEventsFile)) {
  console.log(`phase5_regression: recovery expected events file not found: ${expectedEventsFile || '(empty)'}`);
  analysis.strict_guard_failed = true;
  analysis.strict_failure_reasons = ['recovery_events_file_missing'];
  exitWithCode(2);
}
const expectedEvents = Array.from(
  new Set(
    fs.readFileSync(expectedEventsFile, 'utf8')
      .replace(/\r/g, '')
      .split('\n')
      .map((line) => line.replace(/#.*$/, '').trim().toLowerCase())
      .filter((line) => /^[a-z0-9_]+$/.test(line))
  )
);
if (expectedEvents.length === 0) {
  console.log(`phase5_regression: recovery expected events file is empty or invalid: ${expectedEventsFile}`);
  analysis.strict_guard_failed = true;
  analysis.strict_failure_reasons = ['recovery_events_file_invalid'];
  exitWithCode(2);
}
console.log(`phase5_regression: recovery expected events source: ${expectedEventsFile} (${expectedEvents.length})`);

const expectedEventSet = new Set(expectedEvents);
const missingEvents = expectedEvents.filter((eventName) => !recoverySummary.has(eventName));
const unexpectedEvents = Array.from(recoverySummary.keys()).filter((eventName) => !expectedEventSet.has(eventName));
missingEvents.sort((left, right) => left.localeCompare(right));
unexpectedEvents.sort((left, right) => left.localeCompare(right));

console.log('phase5_regression: recovery guard status');
if (missingEvents.length === 0) {
  console.log(' - OK all expected recovery events observed');
} else {
  for (const missing of missingEvents) {
    console.log(` - WARN missing event: ${missing}`);
  }
}
if (unexpectedEvents.length > 0) {
  for (const unexpected of unexpectedEvents) {
    console.log(` - WARN unexpected event: ${unexpected}`);
  }
}
const guardWarningCount = missingEvents.length + unexpectedEvents.length;
analysis.recovery_guard_warning_count = guardWarningCount;
analysis.recovery_missing_events = missingEvents;
analysis.recovery_unexpected_events = unexpectedEvents;
console.log(`phase5_regression: recovery guard warning count: ${guardWarningCount}`);

if (strictHotspotThresholdSeconds > 0) {
  const hotspotMatchCount = tests.filter((item) => item.durationSec >= strictHotspotThresholdSeconds).length;
  analysis.strict_hotspot_match_count = hotspotMatchCount;
  console.log(
    `phase5_regression: strict hotspot threshold >=${strictHotspotThresholdSeconds}s match count: ${hotspotMatchCount}`
  );
}
if (strictFlakyRiskScoreThreshold > 0) {
  const flakyRiskMatchCount = riskCandidates.filter((item) => item.score >= strictFlakyRiskScoreThreshold).length;
  analysis.strict_flaky_risk_match_count = flakyRiskMatchCount;
  console.log(
    `phase5_regression: strict flaky-risk score threshold >=${strictFlakyRiskScoreThreshold} match count: ${flakyRiskMatchCount}`
  );
}

const strictFailureReasons = [];
if (strictMode === '1' && guardWarningCount > 0) {
  strictFailureReasons.push('recovery_guard');
}
if (strictMode === '1' && strictHotspotThresholdSeconds > 0 && analysis.strict_hotspot_match_count > 0) {
  strictFailureReasons.push('hotspot_threshold');
}
if (strictMode === '1' && strictFlakyRiskScoreThreshold > 0 && analysis.strict_flaky_risk_match_count > 0) {
  strictFailureReasons.push('flaky_risk_threshold');
}
analysis.strict_failure_reasons = strictFailureReasons;
analysis.strict_guard_failed = strictFailureReasons.length > 0;
if (analysis.strict_guard_failed) {
  if (strictFailureReasons.includes('recovery_guard')) {
    console.log('phase5_regression: strict recovery guard failed');
  }
  if (strictFailureReasons.includes('hotspot_threshold')) {
    console.log('phase5_regression: strict hotspot threshold failed');
  }
  if (strictFailureReasons.includes('flaky_risk_threshold')) {
    console.log('phase5_regression: strict flaky-risk threshold failed');
  }
  exitWithCode(2);
}
exitWithCode(0);
NODE
}

validate_recovery_event_registry() {
  local events_file="$1"
  local strict_mode="$2"

  echo "phase5_regression: validate recovery event registry"
  if [[ ! -f "${events_file}" ]]; then
    echo " - WARN events file not found: ${events_file}"
    if [[ "${strict_mode}" == "1" ]]; then
      echo " - strict mode enabled: failing due to missing events file"
      return 1
    fi
    return 0
  fi

  local expected_tmp
  local observed_tmp
  local observed_missing_tmp
  local expected_stale_tmp
  expected_tmp="$(mktemp "/tmp/phase5-recovery-expected.XXXXXX")"
  observed_tmp="$(mktemp "/tmp/phase5-recovery-observed.XXXXXX")"
  observed_missing_tmp="$(mktemp "/tmp/phase5-recovery-observed-missing.XXXXXX")"
  expected_stale_tmp="$(mktemp "/tmp/phase5-recovery-expected-stale.XXXXXX")"
  trap 'rm -f "${expected_tmp}" "${observed_tmp}" "${observed_missing_tmp}" "${expected_stale_tmp}" >/dev/null 2>&1 || true' RETURN

  sed -E 's/#.*$//' "${events_file}" \
    | awk 'NF { print tolower($0) }' \
    | sort -u >"${expected_tmp}"

  rg -No "recovery_event:[a-z0-9_]+" "${PHASE5_SPECS[@]}" \
    | sed -E 's/.*recovery_event:([a-z0-9_]+).*/\1/' \
    | awk 'NF { print tolower($0) }' \
    | sort -u >"${observed_tmp}" || true

  local expected_count
  local observed_count
  expected_count="$(wc -l < "${expected_tmp}" | tr -d '[:space:]')"
  observed_count="$(wc -l < "${observed_tmp}" | tr -d '[:space:]')"
  echo " - expected events: ${expected_count}"
  echo " - observed markers in specs: ${observed_count}"

  comm -23 "${observed_tmp}" "${expected_tmp}" >"${observed_missing_tmp}"
  comm -13 "${observed_tmp}" "${expected_tmp}" >"${expected_stale_tmp}"

  local diff_missing_count
  local diff_stale_count
  diff_missing_count="$(wc -l < "${observed_missing_tmp}" | tr -d '[:space:]')"
  diff_stale_count="$(wc -l < "${expected_stale_tmp}" | tr -d '[:space:]')"
  local diff_missing_csv="none"
  local diff_stale_csv="none"
  if [[ -s "${observed_missing_tmp}" ]]; then
    diff_missing_csv="$(paste -sd, "${observed_missing_tmp}")"
  fi
  if [[ -s "${expected_stale_tmp}" ]]; then
    diff_stale_csv="$(paste -sd, "${expected_stale_tmp}")"
  fi
  echo " - DIFF missing_from_events_file_count: ${diff_missing_count}"
  echo " - DIFF missing_from_events_file_csv: ${diff_missing_csv}"
  echo " - DIFF stale_events_file_entries_count: ${diff_stale_count}"
  echo " - DIFF stale_events_file_entries_csv: ${diff_stale_csv}"

  local warning_count=0
  if [[ -s "${observed_missing_tmp}" ]]; then
    while IFS= read -r event_name; do
      [[ -z "${event_name}" ]] && continue
      echo " - WARN marker missing from events file: ${event_name}"
      warning_count=$((warning_count + 1))
    done < "${observed_missing_tmp}"
  fi
  if [[ -s "${expected_stale_tmp}" ]]; then
    while IFS= read -r event_name; do
      [[ -z "${event_name}" ]] && continue
      echo " - WARN events file entry not found in specs: ${event_name}"
      warning_count=$((warning_count + 1))
    done < "${expected_stale_tmp}"
  fi

  if [[ "${warning_count}" -eq 0 ]]; then
    echo " - OK registry matches spec markers"
    return 0
  fi
  echo " - WARN registry mismatch count: ${warning_count}"
  if [[ "${strict_mode}" == "1" ]]; then
    echo " - strict mode enabled: failing due to registry mismatch"
    return 1
  fi
  return 0
}

collect_recovery_events_from_specs() {
  rg -No "recovery_event:[a-z0-9_]+" "${PHASE5_SPECS[@]}" \
    | sed -E 's/.*recovery_event:([a-z0-9_]+).*/\1/' \
    | awk 'NF { print tolower($0) }' \
    | sort -u
}

sync_recovery_event_registry() {
  local events_file="$1"

  echo "phase5_regression: sync recovery event registry"
  local generated_tmp
  generated_tmp="$(mktemp "/tmp/phase5-recovery-generated.XXXXXX")"
  local sync_rc=0

  if ! collect_recovery_events_from_specs >"${generated_tmp}"; then
    echo " - WARN failed to collect recovery event markers from specs"
    sync_rc=1
  elif [[ ! -s "${generated_tmp}" ]]; then
    echo " - WARN no recovery event markers found in PHASE5_SPECS"
    sync_rc=1
  else
    mkdir -p "$(dirname "${events_file}")"
    {
      echo "# phase5-regression expected recovery events"
      echo "# generated from PHASE5_SPECS recovery_event markers"
      echo ""
      cat "${generated_tmp}"
    } > "${events_file}"
    local generated_count
    generated_count="$(wc -l < "${generated_tmp}" | tr -d '[:space:]')"
    echo " - synced file: ${events_file} (${generated_count} events)"
  fi

  rm -f "${generated_tmp}" >/dev/null 2>&1 || true
  return "${sync_rc}"
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

if [[ "${PHASE5_RECOVERY_REGISTRY_SYNC}" == "1" ]]; then
  sync_recovery_event_registry "${PHASE5_RECOVERY_EVENTS_FILE}"
fi

validate_recovery_event_registry "${PHASE5_RECOVERY_EVENTS_FILE}" "${PHASE5_RECOVERY_REGISTRY_STRICT}"
if [[ "${PHASE5_VALIDATE_RECOVERY_REGISTRY_ONLY}" == "1" ]]; then
  echo "phase5_regression: registry-only mode complete"
  exit 0
fi

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
PHASE5_PLAYWRIGHT_LOG="$(mktemp "/tmp/phase5-regression.playwright.XXXXXX")"
PHASE5_ANALYSIS_JSON_FILE="$(mktemp "/tmp/phase5-regression.analysis.XXXXXX")"
playwright_rc=0
timing_summary_rc=0
run_with_tee "${PHASE5_PLAYWRIGHT_LOG}" \
  env ECM_UI_URL="${EFFECTIVE_ECM_UI_URL}" \
  npx playwright test \
  "${PHASE5_SPECS[@]}" \
  --project="${PW_PROJECT}" --workers="${PW_WORKERS}" || playwright_rc=$?
print_playwright_timing_summary "${PHASE5_PLAYWRIGHT_LOG}" "${PHASE5_ANALYSIS_JSON_FILE}" || timing_summary_rc=$?
if [[ "${timing_summary_rc}" -ne 0 ]]; then
  echo "phase5_regression: timing guard failed (${timing_summary_rc})"
  echo "phase5_regression: playwright log => ${PHASE5_PLAYWRIGHT_LOG}"
  exit "${timing_summary_rc}"
fi
if [[ "${playwright_rc}" -eq 0 ]]; then
  echo "phase5_regression: ok"
else
  print_playwright_failure_summary "${PHASE5_PLAYWRIGHT_LOG}"
  echo "phase5_regression: playwright log => ${PHASE5_PLAYWRIGHT_LOG}"
  exit "${playwright_rc}"
fi
