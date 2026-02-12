#!/usr/bin/env bash
set -euo pipefail

# One-click verification script for ECM system.
# Runs all validation steps and consolidates logs in tmp/
#
# Usage:
#   ./scripts/verify.sh              # Full verification (restart services)
#   ./scripts/verify.sh --no-restart # Skip service restart (faster)
#   ./scripts/verify.sh --smoke-only # Only run smoke tests (no E2E)
#
# Prerequisites:
#   - Docker and docker-compose installed
#   - Node.js and npm installed
#   - jq installed (for JSON parsing)

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${REPO_ROOT}"
ORIGINAL_ARGS="$*"
PARSE_ERROR=0
PARSE_ERROR_MESSAGE=""
RUN_START_EPOCH="$(date +%s)"

# Parse arguments
SKIP_RESTART=0
SMOKE_ONLY=0
SKIP_BUILD=0
WOPI_ONLY=0
SKIP_WOPI=0
WOPI_CLEANUP=0
WOPI_QUERY_OVERRIDE=""
REPORT_LATEST=0
REPORT_LATEST_COUNT=5
REPORT_LATEST_STATUS=""
REPORT_LATEST_SINCE=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --no-restart) SKIP_RESTART=1 ;;
    --smoke-only) SMOKE_ONLY=1 ;;
    --skip-build) SKIP_BUILD=1 ;;
    --wopi-only) WOPI_ONLY=1 ;;
    --skip-wopi) SKIP_WOPI=1 ;;
    --wopi-cleanup) WOPI_CLEANUP=1 ;;
    --wopi-query)
      if [[ $# -lt 2 ]]; then
        PARSE_ERROR=1
        PARSE_ERROR_MESSAGE="Missing value for --wopi-query"
        break
      fi
      WOPI_QUERY_OVERRIDE="$2"
      shift 2
      continue
      ;;
    --wopi-query=*) WOPI_QUERY_OVERRIDE="${1#*=}" ;;
    --report-latest)
      REPORT_LATEST=1
      if [[ $# -gt 1 && "${2}" =~ ^[0-9]+$ ]]; then
        REPORT_LATEST_COUNT="${2}"
        shift 2
        continue
      fi
      ;;
    --report-latest=*) REPORT_LATEST=1; REPORT_LATEST_COUNT="${1#*=}" ;;
    --report-latest-status=*) REPORT_LATEST=1; REPORT_LATEST_STATUS="${1#*=}" ;;
    --report-latest-since=*) REPORT_LATEST=1; REPORT_LATEST_SINCE="${1#*=}" ;;
    --help|-h)
      echo "Usage: $0 [--no-restart] [--smoke-only] [--skip-build] [--wopi-only] [--skip-wopi] [--wopi-cleanup] [--wopi-query=<query>] [--report-latest[=N]]"
      echo "  --no-restart  Skip docker-compose restart (services must be running)"
      echo "  --smoke-only  Only run API smoke tests, skip E2E tests"
      echo "  --skip-build  Skip frontend build step"
      echo "  --wopi-only   Only run WOPI verification (skip other steps)"
      echo "  --skip-wopi   Skip WOPI verification step"
      echo "  --wopi-cleanup  Remove auto-uploaded WOPI sample after verification"
      echo "  --wopi-query=<query>  Search query to find WOPI document"
      echo "  --wopi-query <query>  Search query to find WOPI document"
      echo "  --report-latest[=N]  Summarize the latest N verify-summary.json files"
      echo "  --report-latest <N>  Summarize the latest N verify-summary.json files"
      echo "  --report-latest-status=<STATUS>  Filter latest summaries by status (PASSED/FAILED)"
      echo "  --report-latest-since=<timestamp>  Filter summaries at/after timestamp (YYYYMMDD_HHMMSS)"
      exit 0
      ;;
  esac
  shift
done

if [[ ${REPORT_LATEST} -eq 1 ]]; then
  if ! command -v jq >/dev/null 2>&1; then
    echo "ERROR: jq is required for --report-latest" >&2
    exit 1
  fi
  log_dir="${REPO_ROOT}/tmp"
  mkdir -p "${log_dir}"
  latest_json="${log_dir}/verify-latest.json"
  latest_md="${log_dir}/verify-latest.md"
  latest_steps_csv="${log_dir}/verify-latest-steps.csv"
  latest_runs_csv="${log_dir}/verify-latest-runs.csv"
  status_filter=""
  mapfile -t summary_files < <(ls -1t "${log_dir}"/*_verify-summary.json 2>/dev/null | head -n "${REPORT_LATEST_COUNT}")
  if [[ ${#summary_files[@]} -eq 0 ]]; then
    echo "ERROR: No verify-summary.json files found in ${log_dir}" >&2
    exit 1
  fi
  if [[ -n "${REPORT_LATEST_SINCE}" ]]; then
    if [[ ! "${REPORT_LATEST_SINCE}" =~ ^[0-9]{8}_[0-9]{6}$ ]]; then
      echo "ERROR: --report-latest-since must match YYYYMMDD_HHMMSS" >&2
      exit 1
    fi
    mapfile -t summary_files < <(
      jq -r 'select(.timestamp >= $since) | .artifacts.report' \
        --arg since "${REPORT_LATEST_SINCE}" \
        "${summary_files[@]}" 2>/dev/null \
        | sed 's/_verify-report.md/_verify-summary.json/' \
        | head -n "${REPORT_LATEST_COUNT}"
    )
  fi
  if [[ -n "${REPORT_LATEST_STATUS}" ]]; then
    status_filter="$(printf '%s' "${REPORT_LATEST_STATUS}" | tr '[:lower:]' '[:upper:]')"
    if [[ "${status_filter}" != "PASSED" && "${status_filter}" != "FAILED" ]]; then
      echo "ERROR: --report-latest-status must be PASSED or FAILED" >&2
      exit 1
    fi
    mapfile -t summary_files < <(
      jq -r 'select(.status == $status) | .artifacts.report' \
        --arg status "${status_filter}" \
        "${summary_files[@]}" 2>/dev/null \
        | sed 's/_verify-report.md/_verify-summary.json/' \
        | head -n "${REPORT_LATEST_COUNT}"
    )
  fi
  if [[ ${#summary_files[@]} -eq 0 ]]; then
    echo "ERROR: No verify-summary.json files match the filters in ${log_dir}" >&2
    exit 1
  fi

  jq -s '{
    summary: {
      generatedAt: (now | todateiso8601),
      statusFilter: (if $statusFilter == "" then null else $statusFilter end),
      sinceFilter: (if $sinceFilter == "" then null else $sinceFilter end),
      count: length,
      passedRuns: (map(select(.status == "PASSED")) | length),
      failedRuns: (map(select(.status == "FAILED")) | length),
      wopiPassed: (map(select(.artifacts.wopiStatus == "passed")) | length),
      wopiFailed: (map(select(.artifacts.wopiStatus == "failed")) | length),
      wopiSkipped: (map(select(.artifacts.wopiStatus == "skipped")) | length),
      totalDurationSeconds: (map(.durationSeconds) | add),
      avgDurationSeconds: (if length == 0 then 0 else ((map(.durationSeconds) | add) / length | round) end),
      stepStatsCsv: $stepStatsCsv,
      runsCsv: $runsCsv,
      stepStats: (
        [.[] | (.steps // []) | .[]]
        | sort_by(.step)
        | group_by(.step)
        | map({
            step: .[0].step,
            runs: length,
            passed: (map(select(.status == "passed")) | length),
            failed: (map(select(.status == "failed")) | length),
            skipped: (map(select(.status == "skipped")) | length),
            avgDurationSeconds: ((map(.durationSeconds) | add) / length | round),
            maxDurationSeconds: (map(.durationSeconds) | max)
          })
        | sort_by(.avgDurationSeconds)
        | reverse
      )
    },
    runs: .
  }' --arg statusFilter "${status_filter}" --arg sinceFilter "${REPORT_LATEST_SINCE}" --arg stepStatsCsv "${latest_steps_csv}" --arg runsCsv "${latest_runs_csv}" "${summary_files[@]}" > "${latest_json}"
  summary_tsv="$(
    jq -s -r '[
      length,
      (map(select(.status == "PASSED")) | length),
      (map(select(.status == "FAILED")) | length),
      (map(select(.artifacts.wopiStatus == "passed")) | length),
      (map(select(.artifacts.wopiStatus == "failed")) | length),
      (map(select(.artifacts.wopiStatus == "skipped")) | length),
      ((map(.durationSeconds) | add) / length | round),
      (map(.durationSeconds) | add)
    ] | @tsv' "${summary_files[@]}"
  )"
  IFS=$'\t' read -r summary_runs summary_passed summary_failed summary_wopi_passed summary_wopi_failed summary_wopi_skipped summary_avg summary_total <<< "${summary_tsv}"
  step_stats_csv="$(
    jq -s -r '[.[] | (.steps // []) | .[]]
      | sort_by(.step)
      | group_by(.step)
      | map({
          step: .[0].step,
          runs: length,
          passed: (map(select(.status == "passed")) | length),
          failed: (map(select(.status == "failed")) | length),
          skipped: (map(select(.status == "skipped")) | length),
          avg: ((map(.durationSeconds) | add) / length | round),
          max: (map(.durationSeconds) | max)
        })
      | sort_by(.avg)
      | reverse
      | (["step","runs","passed","failed","skipped","avg_duration_s","max_duration_s"] | @csv),
        (.[] | [ .step, .runs, .passed, .failed, .skipped, .avg, .max ] | @csv)
      ' "${summary_files[@]}"
  )"
  printf '%s\n' "${step_stats_csv}" > "${latest_steps_csv}"
  runs_csv="$(
    jq -s -r '(["timestamp","status","passed","failed","skipped","duration_s","wopi_status","command"] | @csv),
      (.[] | [
        .timestamp,
        .status,
        .results.passed,
        .results.failed,
        .results.skipped,
        .durationSeconds,
        (.artifacts.wopiStatus // ""),
        .command
      ] | @csv)' "${summary_files[@]}"
  )"
  printf '%s\n' "${runs_csv}" > "${latest_runs_csv}"
  failed_table_rows="$(
    jq -s -r 'map(select(.status == "FAILED"))
      | sort_by(.timestamp)
      | reverse
      | map("| \(.timestamp) | \(.results.failed) | \(.durationSeconds) | \(.command) |")
      | .[]' "${summary_files[@]}"
  )"
  step_table_rows="$(
    jq -s -r '[.[] | (.steps // []) | .[]]
      | sort_by(.step)
      | group_by(.step)
      | map({
          step: .[0].step,
          runs: length,
          passed: (map(select(.status == "passed")) | length),
          failed: (map(select(.status == "failed")) | length),
          skipped: (map(select(.status == "skipped")) | length),
          avg: ((map(.durationSeconds) | add) / length | round),
          max: (map(.durationSeconds) | max)
        })
      | sort_by(.avg)
      | reverse
      | .[:5]
      | map("| \(.step) | \(.avg) | \(.max) | \(.runs) | \(.passed) | \(.failed) | \(.skipped) |")
      | .[]' "${summary_files[@]}"
  )"
  {
    echo "# Verification Summary (latest ${#summary_files[@]})"
    echo ""
    echo "- Summary: runs=${summary_runs} passed=${summary_passed} failed=${summary_failed} avgDuration=${summary_avg}s totalDuration=${summary_total}s"
    if [[ -n "${status_filter}" ]]; then
      echo "- Filter: status=${status_filter}"
    fi
    if [[ -n "${REPORT_LATEST_SINCE}" ]]; then
      echo "- Filter: since=${REPORT_LATEST_SINCE}"
    fi
    echo "- WOPI: passed=${summary_wopi_passed} failed=${summary_wopi_failed} skipped=${summary_wopi_skipped}"
    if [[ -n "${failed_table_rows}" ]]; then
      echo ""
      echo "## Failed runs"
      echo ""
      echo "| Timestamp | Failed | Duration(s) | Command |"
      echo "| --- | --- | --- | --- |"
      printf '%s\n' "${failed_table_rows}"
    else
      echo "- Failed runs: none"
    fi
    if [[ -n "${step_table_rows}" ]]; then
      echo ""
      echo "## Top steps by avg duration"
      echo ""
      echo "| Step | Avg(s) | Max(s) | Runs | Passed | Failed | Skipped |"
      echo "| --- | --- | --- | --- | --- | --- | --- |"
      printf '%s\n' "${step_table_rows}"
      echo "- Step stats CSV: ${latest_steps_csv}"
    fi
    echo "- Runs CSV: ${latest_runs_csv}"
    for file in "${summary_files[@]}"; do
      jq -r '"- \(.timestamp) \(.status) passed=\(.results.passed) failed=\(.results.failed) skipped=\(.results.skipped) duration=\(.durationSeconds)s (\(.command))"' "${file}"
    done
  } > "${latest_md}"
  echo "Wrote ${latest_json}"
  echo "Wrote ${latest_md}"
  exit 0
fi

# Guardrail: docker-compose.yml loads `.env.mail` via `env_file`; create a safe placeholder when missing.
ENV_MAIL_WAS_MISSING=0
ENV_MAIL_FILE="${REPO_ROOT}/.env.mail"
ENV_MAIL_EXAMPLE_FILE="${REPO_ROOT}/.env.mail.example"
if [[ ! -f "${ENV_MAIL_FILE}" ]]; then
  ENV_MAIL_WAS_MISSING=1
  if [[ -f "${ENV_MAIL_EXAMPLE_FILE}" ]]; then
    cp "${ENV_MAIL_EXAMPLE_FILE}" "${ENV_MAIL_FILE}"
  else
    cat > "${ENV_MAIL_FILE}" <<'EOF'
# Placeholder for mail automation OAuth/IMAP settings (gitignored).
# If you need real mail automation, copy from .env.mail.example and fill values.
EOF
  fi
  chmod 600 "${ENV_MAIL_FILE}" 2>/dev/null || true
fi

# If wopi-only is enabled, skip all other steps.
if [[ ${WOPI_ONLY} -eq 1 ]]; then
  SKIP_RESTART=1
  SMOKE_ONLY=1
  SKIP_BUILD=1
fi

# Load environment
if [[ -f .env ]]; then
  set -a
  # shellcheck disable=SC1091
  . ./.env
  set +a
fi

# Configuration
KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:${KEYCLOAK_PORT:-8180}}"
ECM_API_URL="${ECM_API_URL:-http://localhost:${ECM_API_PORT:-7700}}"
KEYCLOAK_REALM="${KEYCLOAK_REALM:-ecm}"
ECM_FRONTEND_URL="${ECM_FRONTEND_URL:-http://localhost:${ECM_FRONTEND_PORT:-5500}}"
ECM_VERIFY_USER="${ECM_VERIFY_USER:-${KEYCLOAK_USER:-admin}}"
ECM_VERIFY_PASS="${ECM_VERIFY_PASS:-${KEYCLOAK_PASSWORD:-admin}}"
FRONTEND_DIR="${REPO_ROOT}/ecm-frontend"
LOG_DIR="${REPO_ROOT}/tmp"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
REPORT_FILE="${LOG_DIR}/${TIMESTAMP}_verify-report.md"
SUMMARY_JSON_FILE="${LOG_DIR}/${TIMESTAMP}_verify-summary.json"
WOPI_SUMMARY_FILE="${LOG_DIR}/${TIMESTAMP}_verify-wopi.summary.log"
STEP_SUMMARY_FILE="${LOG_DIR}/${TIMESTAMP}_verify-steps.log"
FRONTEND_DEPS_INSTALLED=0
NPM_CACHE_DIR="${REPO_ROOT}/tmp/npm-cache"

# Create log directory
mkdir -p "${LOG_DIR}"

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

log_info() { echo -e "${BLUE}[INFO]${NC} $*"; }
log_success() { echo -e "${GREEN}[OK]${NC} $*"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*"; }

if [[ "${ENV_MAIL_WAS_MISSING}" -eq 1 ]]; then
  log_warn ".env.mail was missing; created a placeholder so docker-compose can run. Mail OAuth features may be disabled."
fi

# Track step results
STEPS_PASSED=0
STEPS_FAILED=0
STEPS_SKIPPED=0

record_step() {
  local name="$1"
  local status="$2"
  local duration="$3"
  local log_file="${4:-}"
  local reason="${5:-}"
  if [[ ! -f "${STEP_SUMMARY_FILE}" ]]; then
    echo "step,status,duration_s,log,reason" > "${STEP_SUMMARY_FILE}"
  fi
  echo "${name},${status},${duration},${log_file},${reason}" >> "${STEP_SUMMARY_FILE}"
}

write_wopi_summary() {
  local status="$1"
  local log_file="${2:-}"
  local reason="${3:-}"
  {
    echo "verify-wopi status: ${status}"
    echo "log: ${log_file}"
    if [[ -n "${reason}" ]]; then
      echo "reason: ${reason}"
    fi
    if [[ -n "${log_file}" && -f "${log_file}" ]]; then
      echo ""
      echo "verify-wopi output:"
      grep -E '^\[verify\]' "${log_file}" || true
    fi
  } > "${WOPI_SUMMARY_FILE}"
}

write_verification_report() {
  local exit_code="${1:-0}"
  local duration="${2:-0}"
  local status="PASSED"
  if [[ ${STEPS_FAILED} -gt 0 || ${exit_code} -ne 0 ]]; then
    status="FAILED"
  fi

  {
    echo "# Verification Report (${TIMESTAMP})"
    echo ""
    echo "## Command"
    if [[ -n "${ORIGINAL_ARGS}" ]]; then
      echo "- $0 ${ORIGINAL_ARGS}"
    else
      echo "- $0"
    fi
    echo ""
    echo "## Results"
    echo "- Duration (s): ${duration}"
    echo "- Passed: ${STEPS_PASSED}"
    echo "- Failed: ${STEPS_FAILED}"
    echo "- Skipped: ${STEPS_SKIPPED}"
    echo "- Exit code: ${exit_code}"
    echo "- Status: ${status}"
    echo ""
    echo "## Artifacts"
    echo "- Logs prefix: ${LOG_DIR}/${TIMESTAMP}_*"
    if [[ -f "${WOPI_SUMMARY_FILE}" ]]; then
      local wopi_status
      wopi_status="$(grep -m1 '^verify-wopi status:' "${WOPI_SUMMARY_FILE}" | sed 's/^verify-wopi status: //' || true)"
      if [[ -n "${wopi_status}" ]]; then
        echo "- WOPI status: ${wopi_status}"
      fi
      local wopi_reason
      wopi_reason="$(grep -m1 '^reason:' "${WOPI_SUMMARY_FILE}" | sed 's/^reason: //' || true)"
      if [[ -n "${wopi_reason}" ]]; then
        echo "- WOPI reason: ${wopi_reason}"
      fi
      echo "- WOPI summary: ${WOPI_SUMMARY_FILE}"
    fi
    if [[ -f "${STEP_SUMMARY_FILE}" ]]; then
      echo "- Step summary: ${STEP_SUMMARY_FILE}"
    fi
    if [[ -f "${SUMMARY_JSON_FILE}" ]]; then
      echo "- JSON summary: ${SUMMARY_JSON_FILE}"
    fi
  } > "${REPORT_FILE}"
}

write_json_report() {
  local exit_code="${1:-0}"
  local duration="${2:-0}"
  if ! command -v jq >/dev/null 2>&1; then
    return
  fi

  local status="PASSED"
  if [[ ${STEPS_FAILED} -gt 0 || ${exit_code} -ne 0 ]]; then
    status="FAILED"
  fi

  local wopi_status=""
  local wopi_reason=""
  if [[ -f "${WOPI_SUMMARY_FILE}" ]]; then
    wopi_status="$(grep -m1 '^verify-wopi status:' "${WOPI_SUMMARY_FILE}" | sed 's/^verify-wopi status: //' || true)"
    wopi_reason="$(grep -m1 '^reason:' "${WOPI_SUMMARY_FILE}" | sed 's/^reason: //' || true)"
  fi
  local steps_json="[]"
  if [[ -f "${STEP_SUMMARY_FILE}" ]]; then
    steps_json="$(
      jq -Rn --rawfile steps "${STEP_SUMMARY_FILE}" '
        ($steps | split("\n") | map(select(length > 0))) as $lines
        | if ($lines | length) <= 1 then []
          else $lines[1:] | map(split(",") | {
            step: .[0],
            status: .[1],
            durationSeconds: (.[2] | tonumber),
            log: (if (.[3] // "") == "" then null else .[3] end),
            reason: (if (.[4] // "") == "" then null else .[4] end)
          })
        end
      '
    )"
  fi

  jq -n \
    --arg timestamp "${TIMESTAMP}" \
    --arg command "$0 ${ORIGINAL_ARGS}" \
    --arg status "${status}" \
    --arg logsPrefix "${LOG_DIR}/${TIMESTAMP}_*" \
    --arg wopiStatus "${wopi_status}" \
    --arg wopiReason "${wopi_reason}" \
    --arg wopiSummary "${WOPI_SUMMARY_FILE}" \
    --arg stepSummary "${STEP_SUMMARY_FILE}" \
    --arg report "${REPORT_FILE}" \
    --argjson steps "${steps_json}" \
    --argjson exitCode "${exit_code}" \
    --argjson duration "${duration}" \
    --argjson passed "${STEPS_PASSED}" \
    --argjson failed "${STEPS_FAILED}" \
    --argjson skipped "${STEPS_SKIPPED}" \
    '{
      timestamp: $timestamp,
      command: $command,
      status: $status,
      exitCode: $exitCode,
      durationSeconds: $duration,
      results: { passed: $passed, failed: $failed, skipped: $skipped },
      steps: $steps,
      artifacts: {
        logsPrefix: $logsPrefix,
        report: $report,
        wopiSummary: $wopiSummary,
        wopiStatus: $wopiStatus,
        wopiReason: $wopiReason,
        stepSummary: $stepSummary
      }
    }' > "${SUMMARY_JSON_FILE}"
}

handle_exit() {
  local exit_code=$?
  local duration
  duration="$(( $(date +%s) - RUN_START_EPOCH ))"
  set +e
  write_verification_report "${exit_code}" "${duration}"
  write_json_report "${exit_code}" "${duration}"
  log_info "Report: ${REPORT_FILE}"
  if [[ -f "${WOPI_SUMMARY_FILE}" ]]; then
    log_info "WOPI summary: ${WOPI_SUMMARY_FILE}"
  fi
  return "${exit_code}"
}

trap handle_exit EXIT

if [[ ${PARSE_ERROR} -eq 1 ]]; then
  log_error "${PARSE_ERROR_MESSAGE}"
  exit 1
fi

run_step() {
  local step_name="$1"
  local log_file="${LOG_DIR}/${TIMESTAMP}_${step_name}.log"
  shift

  log_info "Running: ${step_name}..."
  local start_ts
  start_ts="$(date +%s)"

  if "$@" > "${log_file}" 2>&1; then
    log_success "${step_name} passed (log: ${log_file})"
    ((STEPS_PASSED+=1))
    record_step "${step_name}" "passed" "$(( $(date +%s) - start_ts ))" "${log_file}"
    if [[ "${step_name}" == "verify-wopi" ]]; then
      write_wopi_summary "passed" "${log_file}"
    fi
    return 0
  else
    local exit_code=$?
    log_error "${step_name} failed (exit code: ${exit_code}, log: ${log_file})"
    ((STEPS_FAILED+=1))
    record_step "${step_name}" "failed" "$(( $(date +%s) - start_ts ))" "${log_file}"
    if [[ "${step_name}" == "verify-wopi" ]]; then
      write_wopi_summary "failed" "${log_file}"
    fi
    return ${exit_code}
  fi
}

run_step_optional() {
  local step_name="$1"
  local log_file="${LOG_DIR}/${TIMESTAMP}_${step_name}.log"
  shift

  log_info "Running: ${step_name}..."
  local start_ts
  start_ts="$(date +%s)"

  if "$@" > "${log_file}" 2>&1; then
    log_success "${step_name} passed (log: ${log_file})"
    ((STEPS_PASSED+=1))
    record_step "${step_name}" "passed" "$(( $(date +%s) - start_ts ))" "${log_file}"
    return 0
  else
    local exit_code=$?
    log_warn "${step_name} failed (non-critical, exit code: ${exit_code}, log: ${log_file})"
    ((STEPS_SKIPPED+=1))
    record_step "${step_name}" "skipped" "$(( $(date +%s) - start_ts ))" "${log_file}" "non-critical failure"
    return 0
  fi
}

record_wait_step() {
  local step_name="$1"
  local service_label="$2"
  local url="$3"
  local attempts="${4:-30}"
  local start_ts
  start_ts="$(date +%s)"
  if wait_for_service "${service_label}" "${url}" "${attempts}"; then
    record_step "${step_name}" "passed" "$(( $(date +%s) - start_ts ))" ""
    return 0
  fi
  record_step "${step_name}" "failed" "$(( $(date +%s) - start_ts ))" "" "service not ready"
  return 1
}

docker_compose() {
  if command -v docker-compose >/dev/null 2>&1; then
    (cd "${REPO_ROOT}" && docker-compose -f docker-compose.yml "$@")
  else
    docker compose --project-directory "${REPO_ROOT}" -f "${REPO_ROOT}/docker-compose.yml" "$@"
  fi
}

wait_for_service() {
  local name="$1"
  local url="$2"
  local max_attempts="${3:-30}"
  local attempt=1

  log_info "Waiting for ${name} at ${url}..."

  while [[ ${attempt} -le ${max_attempts} ]]; do
    if curl -fsS -o /dev/null "${url}" 2>/dev/null; then
      log_success "${name} is ready"
      return 0
    fi
    echo -n "."
    sleep 2
    ((attempt++))
  done

  echo ""
  log_error "${name} did not become ready after $((max_attempts * 2)) seconds"
  return 1
}

wait_for_clamav_health() {
  local container_id status
  for _ in {1..30}; do
    container_id="$(docker_compose ps -q clamav 2>/dev/null || true)"
    if [[ -z "${container_id}" ]]; then
      echo "ClamAV service not running; skipping."
      return 0
    fi

    status="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}no-healthcheck{{end}}' "${container_id}" 2>/dev/null || true)"
    if [[ "${status}" == "healthy" ]]; then
      echo "ClamAV healthy."
      return 0
    fi
    if [[ "${status}" == "no-healthcheck" || -z "${status}" ]]; then
      echo "ClamAV healthcheck not available; skipping."
      return 0
    fi
    sleep 2
  done
  return 1
}

ensure_clamav_health() {
  local container_id status
  container_id="$(docker_compose ps -q clamav 2>/dev/null || true)"
  if [[ -z "${container_id}" ]]; then
    echo "ClamAV service not running; skipping."
    return 0
  fi

  status="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}no-healthcheck{{end}}' "${container_id}" 2>/dev/null || true)"
  if [[ "${status}" == "healthy" ]]; then
    echo "ClamAV healthy."
    return 0
  fi
  if [[ "${status}" == "no-healthcheck" || -z "${status}" ]]; then
    echo "ClamAV healthcheck not available; skipping."
    return 0
  fi

  echo "ClamAV status=${status}; waiting for healthy..."
  if wait_for_clamav_health; then
    return 0
  fi

  echo "ClamAV still not healthy; restarting..."
  docker_compose restart clamav
  if wait_for_clamav_health; then
    return 0
  fi

  echo "ClamAV still unhealthy after restart."
  return 1
}

ensure_frontend_deps() {
  if [[ "${FRONTEND_DEPS_INSTALLED}" -eq 1 ]]; then
    return 0
  fi

  if [[ -f package-lock.json ]]; then
    mkdir -p "${NPM_CACHE_DIR}"
    run_step "frontend-install" npm ci --legacy-peer-deps --no-audit --no-fund --cache "${NPM_CACHE_DIR}"
  else
    log_warn "package-lock.json missing; falling back to npm install."
    mkdir -p "${NPM_CACHE_DIR}"
    run_step "frontend-install" npm install --legacy-peer-deps --no-audit --no-fund --cache "${NPM_CACHE_DIR}"
  fi
  FRONTEND_DEPS_INSTALLED=1
}

# ============================================================
# STEP 1: Restart services (optional)
# ============================================================
if [[ ${SKIP_RESTART} -eq 0 ]]; then
  log_info "=== Step 1: Restarting ECM services ==="
  if [[ -f "${SCRIPT_DIR}/restart-ecm.sh" ]]; then
    run_step "restart-ecm" bash "${SCRIPT_DIR}/restart-ecm.sh" || true
  else
    log_warn "restart-ecm.sh not found, skipping restart"
    ((STEPS_SKIPPED+=1))
    record_step "restart-ecm" "skipped" "0" "" "restart-ecm.sh not found"
  fi
else
  log_info "=== Step 1: Skipping service restart (--no-restart) ==="
  ((STEPS_SKIPPED+=1))
  record_step "restart-ecm" "skipped" "0" "" "Skipped via --no-restart flag"
fi

# ============================================================
# STEP 2: Wait for services to be healthy
# ============================================================
log_info "=== Step 2: Waiting for services ==="

# Wait for Keycloak
record_wait_step "wait-keycloak" "Keycloak" "${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}/.well-known/openid-configuration" 60 || {
  log_error "Keycloak not ready, aborting"
  exit 1
}

# Wait for ECM Core API
record_wait_step "wait-ecm-api" "ECM Core API" "${ECM_API_URL}/actuator/health" 60 || {
  log_error "ECM Core API not ready, aborting"
  exit 1
}

log_success "All services are ready"

# ============================================================
# STEP 2.5: Ensure ClamAV is healthy (optional)
# ============================================================
log_info "=== Step 2.5: Checking ClamAV health ==="
run_step_optional "clamav-health" ensure_clamav_health

# ============================================================
# STEP 3: Create test users in Keycloak
# ============================================================
if [[ ${WOPI_ONLY} -eq 0 ]]; then
  log_info "=== Step 3: Creating test users in Keycloak ==="
  if [[ -f "${SCRIPT_DIR}/keycloak/create-test-users.sh" ]]; then
    run_step_optional "create-test-users" bash "${SCRIPT_DIR}/keycloak/create-test-users.sh"
  else
    log_warn "create-test-users.sh not found, skipping"
    ((STEPS_SKIPPED+=1))
    record_step "create-test-users" "skipped" "0" "" "create-test-users.sh not found"
  fi
else
  log_info "=== Step 3: Skipping test user creation (--wopi-only) ==="
  ((STEPS_SKIPPED+=1))
  record_step "create-test-users" "skipped" "0" "" "Skipped via --wopi-only flag"
fi

# ============================================================
# STEP 4: Get access token
# ============================================================
if [[ ${WOPI_ONLY} -eq 0 ]]; then
  log_info "=== Step 4: Getting access token ==="
  if [[ -f "${SCRIPT_DIR}/get-token.sh" ]]; then
    run_step "get-token-admin" bash "${SCRIPT_DIR}/get-token.sh" admin admin
    run_step "get-token-viewer" bash "${SCRIPT_DIR}/get-token.sh" viewer viewer
  else
    log_warn "get-token.sh not found, skipping"
    ((STEPS_SKIPPED+=1))
    record_step "get-token-admin" "skipped" "0" "" "get-token.sh not found"
    record_step "get-token-viewer" "skipped" "0" "" "get-token.sh not found"
  fi
else
  log_info "=== Step 4: Skipping token fetch (--wopi-only) ==="
  ((STEPS_SKIPPED+=1))
  record_step "get-token-admin" "skipped" "0" "" "Skipped via --wopi-only flag"
  record_step "get-token-viewer" "skipped" "0" "" "Skipped via --wopi-only flag"
fi

# ============================================================
# STEP 5: Run API smoke tests
# ============================================================
if [[ ${WOPI_ONLY} -eq 0 ]]; then
  log_info "=== Step 5: Running API smoke tests ==="
  if [[ -f "${SCRIPT_DIR}/smoke.sh" ]]; then
    run_step "smoke-test" env \
      ECM_API="${ECM_API_URL}" \
      ECM_TOKEN_FILE="${REPO_ROOT}/tmp/admin.access_token" \
      bash "${SCRIPT_DIR}/smoke.sh"
  else
    log_error "smoke.sh not found!"
    ((STEPS_FAILED+=1))
  fi
else
  log_info "=== Step 5: Skipping API smoke tests (--wopi-only) ==="
  ((STEPS_SKIPPED+=1))
  record_step "smoke-test" "skipped" "0" "" "Skipped via --wopi-only flag"
fi

# ============================================================
# STEP 5.5: Phase C security verification (ACL + share links)
# ============================================================
if [[ ${WOPI_ONLY} -eq 0 ]]; then
  log_info "=== Step 5.5: Running security verification (Phase C) ==="
  if [[ -f "${SCRIPT_DIR}/verify-phase-c.py" ]]; then
    run_step "verify-phase-c" env \
      API_BASE_URL="${ECM_API_URL}" \
      python3 "${SCRIPT_DIR}/verify-phase-c.py"
  else
    log_warn "verify-phase-c.py not found, skipping"
    ((STEPS_SKIPPED+=1))
  fi
else
  log_info "=== Step 5.5: Skipping security verification (--wopi-only) ==="
  ((STEPS_SKIPPED+=1))
  record_step "verify-phase-c" "skipped" "0" "" "Skipped via --wopi-only flag"
fi

# ============================================================
# STEP 6: Build frontend (optional)
# ============================================================
if [[ ${SKIP_BUILD} -eq 0 ]]; then
  log_info "=== Step 6: Building frontend ==="
  if [[ -d "${FRONTEND_DIR}" ]]; then
    cd "${FRONTEND_DIR}"
    ensure_frontend_deps
    run_step "frontend-build" npm run build
    cd "${REPO_ROOT}"
  else
    log_error "Frontend directory not found: ${FRONTEND_DIR}"
    ((STEPS_FAILED+=1))
  fi
else
  log_info "=== Step 6: Skipping frontend build (--skip-build) ==="
  ((STEPS_SKIPPED+=1))
  record_step "frontend-build" "skipped" "0" "" "Skipped via --skip-build flag"
fi

# ============================================================
# STEP 6.5: Verify WOPI preview/edit + audit (optional)
# ============================================================
if [[ ${SKIP_WOPI} -eq 0 ]]; then
  log_info "=== Step 6.5: Verifying WOPI preview + audit ==="
  if [[ -f "${SCRIPT_DIR}/verify-wopi.js" ]]; then
    if [[ -d "${FRONTEND_DIR}" ]]; then
      cd "${FRONTEND_DIR}"
      ensure_frontend_deps
      if ! npx playwright --version > /dev/null 2>&1; then
        log_info "Installing Playwright browsers..."
        npx playwright install chromium > /dev/null 2>&1 || true
      fi
      cd "${REPO_ROOT}"
    fi
    wopi_env=(
      "ECM_FRONTEND_URL=${ECM_FRONTEND_URL}"
      "ECM_API_URL=${ECM_API_URL}"
      "KEYCLOAK_URL=${KEYCLOAK_URL}"
      "KEYCLOAK_REALM=${KEYCLOAK_REALM}"
      "ECM_VERIFY_USER=${ECM_VERIFY_USER}"
      "ECM_VERIFY_PASS=${ECM_VERIFY_PASS}"
    )
    if [[ ${WOPI_CLEANUP} -eq 1 ]]; then
      wopi_env+=("ECM_VERIFY_CLEANUP=1")
    fi
    if [[ -n "${WOPI_QUERY_OVERRIDE}" ]]; then
      wopi_env+=("ECM_VERIFY_QUERY=${WOPI_QUERY_OVERRIDE}")
    fi
    run_step "verify-wopi" env "${wopi_env[@]}" node "${SCRIPT_DIR}/verify-wopi.js"
  else
    log_warn "verify-wopi.js not found, skipping"
    ((STEPS_SKIPPED+=1))
    write_wopi_summary "skipped" "" "verify-wopi.js not found"
    record_step "verify-wopi" "skipped" "0" "" "verify-wopi.js not found"
  fi
else
  log_info "=== Step 6.5: Skipping WOPI verification (--skip-wopi) ==="
  ((STEPS_SKIPPED+=1))
  write_wopi_summary "skipped" "" "Skipped via --skip-wopi flag"
  record_step "verify-wopi" "skipped" "0" "" "Skipped via --skip-wopi flag"
fi

# ============================================================
# STEP 7: Run E2E tests (optional)
# ============================================================
if [[ ${SMOKE_ONLY} -eq 0 ]]; then
  log_info "=== Step 7: Running E2E tests ==="
  if [[ -d "${FRONTEND_DIR}/e2e" ]]; then
    cd "${FRONTEND_DIR}"
    ensure_frontend_deps
    # Install Playwright browsers if needed
    if ! npx playwright --version > /dev/null 2>&1; then
      log_info "Installing Playwright browsers..."
      npx playwright install chromium > /dev/null 2>&1 || true
    fi

    run_step "e2e-test" npx playwright test --project=chromium
    cd "${REPO_ROOT}"
  else
    log_warn "E2E tests directory not found, skipping"
    ((STEPS_SKIPPED+=1))
  fi
else
  log_info "=== Step 7: Skipping E2E tests (--smoke-only) ==="
  ((STEPS_SKIPPED+=1))
  record_step "e2e-test" "skipped" "0" "" "Skipped via --smoke-only flag"
fi

# ============================================================
# Summary
# ============================================================
echo ""
echo "============================================================"
echo "VERIFICATION SUMMARY"
echo "============================================================"
echo -e "  ${GREEN}Passed:${NC}  ${STEPS_PASSED}"
echo -e "  ${RED}Failed:${NC}  ${STEPS_FAILED}"
echo -e "  ${YELLOW}Skipped:${NC} ${STEPS_SKIPPED}"
echo ""
echo "Logs directory: ${LOG_DIR}"
echo "Log files prefix: ${TIMESTAMP}_*"
echo ""

if [[ ${STEPS_FAILED} -gt 0 ]]; then
  log_error "Verification FAILED (${STEPS_FAILED} step(s) failed)"
  exit 1
else
  log_success "Verification PASSED"
  exit 0
fi
