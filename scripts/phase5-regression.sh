#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

ECM_UI_URL="${ECM_UI_URL:-http://localhost:5500}"
PW_WORKERS="${PW_WORKERS:-1}"
PW_PROJECT="${PW_PROJECT:-chromium}"
PHASE5_USE_EXISTING_UI="${PHASE5_USE_EXISTING_UI:-0}"

echo "phase5_regression: start"
echo "ECM_UI_URL=${ECM_UI_URL}"
echo "PW_PROJECT=${PW_PROJECT} PW_WORKERS=${PW_WORKERS}"
echo "PHASE5_USE_EXISTING_UI=${PHASE5_USE_EXISTING_UI}"

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
  "e2e/search-suggestions-save-search.mock.spec.ts"
  "e2e/settings-session-actions.mock.spec.ts"
  "e2e/auth-session-recovery.mock.spec.ts"
  "e2e/auth-boot-watchdog-recovery.mock.spec.ts"
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
if [[ "${playwright_rc}" -eq 0 ]]; then
  echo "phase5_regression: ok"
else
  print_playwright_failure_summary "${playwright_log}"
  echo "phase5_regression: playwright log => ${playwright_log}"
  exit "${playwright_rc}"
fi
