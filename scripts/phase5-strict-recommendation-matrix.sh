#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"

ECM_UI_URL_MOCKED="${ECM_UI_URL_MOCKED:-http://localhost:5500}"
PW_PROJECT="${PW_PROJECT:-chromium}"
PW_WORKERS="${PW_WORKERS:-1}"
MATRIX_TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
PHASE5_STRICT_MATRIX_OUTPUT_DIR="${PHASE5_STRICT_MATRIX_OUTPUT_DIR:-/tmp/phase5-strict-recommendation-matrix-${MATRIX_TIMESTAMP}}"
PHASE5_STRICT_MATRIX_SUMMARY_DIR="${PHASE5_STRICT_MATRIX_SUMMARY_DIR:-${PHASE5_STRICT_MATRIX_OUTPUT_DIR}/phase5-summaries}"

mkdir -p "${PHASE5_STRICT_MATRIX_OUTPUT_DIR}" "${PHASE5_STRICT_MATRIX_SUMMARY_DIR}"

echo "phase5_strict_recommendation_matrix: start"
echo "ECM_UI_URL_MOCKED=${ECM_UI_URL_MOCKED}"
echo "PW_PROJECT=${PW_PROJECT}"
echo "PW_WORKERS=${PW_WORKERS}"
echo "PHASE5_STRICT_MATRIX_OUTPUT_DIR=${PHASE5_STRICT_MATRIX_OUTPUT_DIR}"
echo "PHASE5_STRICT_MATRIX_SUMMARY_DIR=${PHASE5_STRICT_MATRIX_SUMMARY_DIR}"

strip_ansi_file() {
  local log_file="$1"
  sed -E $'s/\x1B\\[[0-9;]*[[:alpha:]]//g' "${log_file}" | tr -d '\r'
}

shell_join() {
  local quoted=()
  local arg
  for arg in "$@"; do
    printf -v q '%q' "${arg}"
    quoted+=("${q}")
  done
  printf '%s' "${quoted[*]}"
}

SCENARIO_RESULTS=()
MATRIX_FAILED=0

run_scenario() {
  local scenario_key="$1"
  local description=""
  local expected_rc="0"
  local summary_dir="${PHASE5_STRICT_MATRIX_SUMMARY_DIR}/${scenario_key}"
  local missing_events_file="${PHASE5_STRICT_MATRIX_OUTPUT_DIR}/${scenario_key}.missing-events.expected.txt"

  local -a cmd=()
  local -a expected_patterns=()

  case "${scenario_key}" in
    baseline_sufficient_sample)
      description="strict baseline with sufficient sample thresholds (expect PASS)"
      expected_rc="0"
      cmd=(
        env
        ECM_UI_URL_MOCKED="${ECM_UI_URL_MOCKED}"
        PW_PROJECT="${PW_PROJECT}"
        PW_WORKERS="${PW_WORKERS}"
        PHASE5_RECOVERY_EVENTS_FILE="e2e/recovery-events.expected.txt"
        DELIVERY_GATE_PRINT_EXECUTION_PLAN=0
        DELIVERY_GATE_PRINT_STRICT_SUGGESTIONS_JSON=0
        DELIVERY_GATE_RECOVERY_REGISTRY_SYNC=0
        DELIVERY_GATE_RECOVERY_REGISTRY_STRICT=0
        DELIVERY_GATE_RECOVERY_REGISTRY_VERIFY_IDEMPOTENT=0
        bash
        scripts/phase5-phase6-delivery-gate.sh
        --mode=mocked
        --no-plan
        --no-print-strict-suggestions-json
        --phase5-summary-dir="${summary_dir}"
        --phase5-strict-recovery-guard=1
        --phase5-strict-hotspot-sec=999
        --phase5-strict-flaky-score=99
        --phase5-hotspot-recommend-percentile=0.95
        --phase5-hotspot-recommend-padding-sec=0.1
        --phase5-hotspot-recommend-min-sample=5
        --phase5-flaky-recommend-percentile=0.9
        --phase5-flaky-recommend-step=1
        --phase5-flaky-recommend-min-sample=3
      )
      expected_patterns=(
        "phase5_regression: strict hotspot recommendation p95="
        "phase5_regression: strict flaky-risk recommendation p90="
        "phase5_regression: recovery guard warning count: 0"
        "phase5_phase6_delivery_gate: ok"
      )
      ;;
    low_confidence_forced_strict_fail)
      description="force low-confidence recommendations and strict fail via tiny thresholds + high min-sample"
      expected_rc="1"
      cmd=(
        env
        ECM_UI_URL_MOCKED="${ECM_UI_URL_MOCKED}"
        PW_PROJECT="${PW_PROJECT}"
        PW_WORKERS="${PW_WORKERS}"
        PHASE5_RECOVERY_EVENTS_FILE="e2e/recovery-events.expected.txt"
        DELIVERY_GATE_PRINT_EXECUTION_PLAN=0
        DELIVERY_GATE_PRINT_STRICT_SUGGESTIONS_JSON=0
        DELIVERY_GATE_RECOVERY_REGISTRY_SYNC=0
        DELIVERY_GATE_RECOVERY_REGISTRY_STRICT=0
        DELIVERY_GATE_RECOVERY_REGISTRY_VERIFY_IDEMPOTENT=0
        bash
        scripts/phase5-phase6-delivery-gate.sh
        --mode=mocked
        --no-plan
        --no-print-strict-suggestions-json
        --phase5-summary-dir="${summary_dir}"
        --phase5-strict-recovery-guard=1
        --phase5-strict-hotspot-sec=0.1
        --phase5-strict-flaky-score=1
        --phase5-hotspot-recommend-percentile=0.95
        --phase5-hotspot-recommend-padding-sec=0.1
        --phase5-hotspot-recommend-min-sample=999
        --phase5-flaky-recommend-percentile=0.9
        --phase5-flaky-recommend-step=1
        --phase5-flaky-recommend-min-sample=999
      )
      expected_patterns=(
        "phase5_regression: strict hotspot recommendation low-confidence sample("
        "phase5_regression: strict flaky-risk recommendation low-confidence sample("
        "phase5_regression: strict hotspot threshold failed"
        "phase5_phase6_delivery_gate: failed in fast mocked layer"
      )
      ;;
    edge_guard_missing_events_preflight)
      description="edge guard path in preflight mode via missing expected-events file (expect fast FAIL)"
      expected_rc="1"
      rm -f "${missing_events_file}"
      cmd=(
        env
        ECM_UI_URL_MOCKED="${ECM_UI_URL_MOCKED}"
        PW_PROJECT="${PW_PROJECT}"
        PW_WORKERS="${PW_WORKERS}"
        PHASE5_RECOVERY_EVENTS_FILE="${missing_events_file}"
        DELIVERY_GATE_PRINT_EXECUTION_PLAN=0
        DELIVERY_GATE_PRINT_STRICT_SUGGESTIONS_JSON=0
        DELIVERY_GATE_RECOVERY_REGISTRY_SYNC=0
        DELIVERY_GATE_RECOVERY_REGISTRY_STRICT=1
        DELIVERY_GATE_RECOVERY_REGISTRY_VERIFY_IDEMPOTENT=0
        bash
        scripts/phase5-phase6-delivery-gate.sh
        --mode=preflight
        --no-plan
        --no-print-strict-suggestions-json
        --phase5-strict-recovery-guard=1
      )
      expected_patterns=(
        "phase5_regression: validate recovery event registry"
        "WARN events file not found"
        "strict mode enabled: failing due to missing events file"
        "phase5_phase6_delivery_gate: failed in fast mocked layer"
      )
      ;;
    *)
      echo "error: unsupported scenario key: ${scenario_key}"
      exit 1
      ;;
  esac

  mkdir -p "${summary_dir}"

  local log_file="${PHASE5_STRICT_MATRIX_OUTPUT_DIR}/${scenario_key}.log"
  local clean_log_file="${PHASE5_STRICT_MATRIX_OUTPUT_DIR}/${scenario_key}.clean.log"
  local key_lines_file="${PHASE5_STRICT_MATRIX_OUTPUT_DIR}/${scenario_key}.key-lines.txt"

  echo ""
  echo "[scenario] ${scenario_key}"
  echo "description=${description}"
  echo "expected_rc=${expected_rc}"
  echo "log_file=${log_file}"
  echo "command=$(shell_join "${cmd[@]}")"

  local cmd_rc=0
  local tee_rc=0
  set +e
  "${cmd[@]}" 2>&1 | tee "${log_file}"
  cmd_rc="${PIPESTATUS[0]:-1}"
  tee_rc="${PIPESTATUS[1]:-0}"
  set -e

  local actual_rc="${cmd_rc}"
  if [[ "${actual_rc}" -eq 0 && "${tee_rc}" -ne 0 ]]; then
    actual_rc="${tee_rc}"
  fi

  strip_ansi_file "${log_file}" > "${clean_log_file}"

  : > "${key_lines_file}"
  echo "scenario=${scenario_key}" >> "${key_lines_file}"
  echo "description=${description}" >> "${key_lines_file}"
  echo "expected_rc=${expected_rc}" >> "${key_lines_file}"
  echo "actual_rc=${actual_rc}" >> "${key_lines_file}"

  local summary_file=""
  summary_file="$(sed -nE 's#^.*mocked regression summary generated =>[[:space:]]*(.+)$#\1#p' "${clean_log_file}" | tail -n 1)"
  if [[ -n "${summary_file}" ]]; then
    echo "summary_file=${summary_file}" >> "${key_lines_file}"
  fi

  echo "" >> "${key_lines_file}"
  echo "key_line_checks:" >> "${key_lines_file}"

  local missing_patterns=0
  local pattern
  for pattern in "${expected_patterns[@]}"; do
    local matched_line=""
    matched_line="$(grep -F -m 1 "${pattern}" "${clean_log_file}" || true)"
    if [[ -n "${matched_line}" ]]; then
      echo "[MATCH] ${pattern}" >> "${key_lines_file}"
      echo "${matched_line}" >> "${key_lines_file}"
    else
      echo "[MISSING] ${pattern}" >> "${key_lines_file}"
      missing_patterns=$((missing_patterns + 1))
    fi
  done

  local scenario_status="PASS"
  if [[ "${actual_rc}" -ne "${expected_rc}" || "${missing_patterns}" -ne 0 ]]; then
    scenario_status="FAIL"
    MATRIX_FAILED=1
  fi

  echo "" >> "${key_lines_file}"
  echo "scenario_status=${scenario_status}" >> "${key_lines_file}"
  echo "missing_patterns=${missing_patterns}" >> "${key_lines_file}"

  SCENARIO_RESULTS+=("${scenario_key}|${scenario_status}|${expected_rc}|${actual_rc}|${missing_patterns}|${log_file}|${key_lines_file}|${summary_file}")

  echo "scenario_result=${scenario_status}"
  echo "key_lines_file=${key_lines_file}"
}

run_scenario "baseline_sufficient_sample"
run_scenario "low_confidence_forced_strict_fail"
run_scenario "edge_guard_missing_events_preflight"

echo ""
echo "phase5_strict_recommendation_matrix: scenario summary"
record_count="${#SCENARIO_RESULTS[@]}"
if [[ "${record_count}" -eq 0 ]]; then
  echo " - no scenario records"
  MATRIX_FAILED=1
fi

for record in "${SCENARIO_RESULTS[@]}"; do
  IFS='|' read -r scenario_key scenario_status expected_rc actual_rc missing_patterns log_file key_lines_file summary_file <<< "${record}"
  echo " - [${scenario_status}] ${scenario_key}: expected_rc=${expected_rc}, actual_rc=${actual_rc}, missing_patterns=${missing_patterns}"
  echo "   log=${log_file}"
  echo "   key_lines=${key_lines_file}"
  if [[ -n "${summary_file}" ]]; then
    echo "   summary=${summary_file}"
  fi
done

echo "artifacts_dir=${PHASE5_STRICT_MATRIX_OUTPUT_DIR}"
if [[ "${MATRIX_FAILED}" -ne 0 ]]; then
  echo "phase5_strict_recommendation_matrix: FAILED"
  exit 1
fi

echo "phase5_strict_recommendation_matrix: ok"
