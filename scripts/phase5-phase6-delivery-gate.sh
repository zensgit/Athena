#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

ECM_UI_URL_MOCKED="${ECM_UI_URL_MOCKED:-http://localhost:5500}"
ECM_UI_URL_FULLSTACK_INPUT="${ECM_UI_URL_FULLSTACK:-}"
ECM_API_URL="${ECM_API_URL:-http://localhost:7700}"
KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8180}"
KEYCLOAK_REALM="${KEYCLOAK_REALM:-ecm}"

ECM_E2E_USERNAME="${ECM_E2E_USERNAME:-admin}"
ECM_E2E_PASSWORD="${ECM_E2E_PASSWORD:-admin}"

PW_PROJECT="${PW_PROJECT:-chromium}"
PW_WORKERS="${PW_WORKERS:-1}"
ECM_SYNC_PREBUILT_UI="${ECM_SYNC_PREBUILT_UI:-auto}"
DELIVERY_GATE_MODE="${DELIVERY_GATE_MODE:-all}"
DELIVERY_GATE_PRINT_EXECUTION_PLAN="${DELIVERY_GATE_PRINT_EXECUTION_PLAN:-1}"
DELIVERY_GATE_EXECUTION_PLAN_FORMAT="${DELIVERY_GATE_EXECUTION_PLAN_FORMAT:-text}"
DELIVERY_GATE_EXECUTION_PLAN_FILE="${DELIVERY_GATE_EXECUTION_PLAN_FILE:-}"
DELIVERY_GATE_PRINT_STRICT_SUGGESTIONS_JSON="${DELIVERY_GATE_PRINT_STRICT_SUGGESTIONS_JSON:-0}"
DELIVERY_GATE_STRICT_SUGGESTIONS_FILE="${DELIVERY_GATE_STRICT_SUGGESTIONS_FILE:-}"
DELIVERY_GATE_PHASE5_SUMMARY_DIR="${DELIVERY_GATE_PHASE5_SUMMARY_DIR:-}"
DELIVERY_GATE_PLAN_ONLY="0"
if [[ -n "${DELIVERY_GATE_PHASE5_RECOVERY_GUARD_STRICT:-}" ]]; then
  DELIVERY_GATE_PHASE5_RECOVERY_GUARD_STRICT="${DELIVERY_GATE_PHASE5_RECOVERY_GUARD_STRICT}"
  DELIVERY_GATE_PHASE5_RECOVERY_GUARD_STRICT_SOURCE="env"
elif [[ -n "${PHASE5_RECOVERY_GUARD_STRICT:-}" ]]; then
  DELIVERY_GATE_PHASE5_RECOVERY_GUARD_STRICT="${PHASE5_RECOVERY_GUARD_STRICT}"
  DELIVERY_GATE_PHASE5_RECOVERY_GUARD_STRICT_SOURCE="inherited_env"
else
  DELIVERY_GATE_PHASE5_RECOVERY_GUARD_STRICT="0"
  DELIVERY_GATE_PHASE5_RECOVERY_GUARD_STRICT_SOURCE="local_default"
fi
if [[ -n "${DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_DURATION_SEC_THRESHOLD:-}" ]]; then
  DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_DURATION_SEC_THRESHOLD="${DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_DURATION_SEC_THRESHOLD}"
  DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_DURATION_SEC_THRESHOLD_SOURCE="env"
elif [[ -n "${PHASE5_STRICT_HOTSPOT_DURATION_SEC_THRESHOLD:-}" ]]; then
  DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_DURATION_SEC_THRESHOLD="${PHASE5_STRICT_HOTSPOT_DURATION_SEC_THRESHOLD}"
  DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_DURATION_SEC_THRESHOLD_SOURCE="inherited_env"
else
  DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_DURATION_SEC_THRESHOLD="0"
  DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_DURATION_SEC_THRESHOLD_SOURCE="local_default"
fi
if [[ -n "${DELIVERY_GATE_PHASE5_STRICT_FLAKY_RISK_SCORE_THRESHOLD:-}" ]]; then
  DELIVERY_GATE_PHASE5_STRICT_FLAKY_RISK_SCORE_THRESHOLD="${DELIVERY_GATE_PHASE5_STRICT_FLAKY_RISK_SCORE_THRESHOLD}"
  DELIVERY_GATE_PHASE5_STRICT_FLAKY_RISK_SCORE_THRESHOLD_SOURCE="env"
elif [[ -n "${PHASE5_STRICT_FLAKY_RISK_SCORE_THRESHOLD:-}" ]]; then
  DELIVERY_GATE_PHASE5_STRICT_FLAKY_RISK_SCORE_THRESHOLD="${PHASE5_STRICT_FLAKY_RISK_SCORE_THRESHOLD}"
  DELIVERY_GATE_PHASE5_STRICT_FLAKY_RISK_SCORE_THRESHOLD_SOURCE="inherited_env"
else
  DELIVERY_GATE_PHASE5_STRICT_FLAKY_RISK_SCORE_THRESHOLD="0"
  DELIVERY_GATE_PHASE5_STRICT_FLAKY_RISK_SCORE_THRESHOLD_SOURCE="local_default"
fi
if [[ -n "${DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_RECOMMEND_PERCENTILE:-}" ]]; then
  DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_RECOMMEND_PERCENTILE="${DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_RECOMMEND_PERCENTILE}"
  DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_RECOMMEND_PERCENTILE_SOURCE="env"
elif [[ -n "${PHASE5_STRICT_HOTSPOT_RECOMMEND_PERCENTILE:-}" ]]; then
  DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_RECOMMEND_PERCENTILE="${PHASE5_STRICT_HOTSPOT_RECOMMEND_PERCENTILE}"
  DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_RECOMMEND_PERCENTILE_SOURCE="inherited_env"
else
  DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_RECOMMEND_PERCENTILE="0.95"
  DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_RECOMMEND_PERCENTILE_SOURCE="local_default"
fi
if [[ -n "${DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_RECOMMEND_PADDING_SEC:-}" ]]; then
  DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_RECOMMEND_PADDING_SEC="${DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_RECOMMEND_PADDING_SEC}"
  DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_RECOMMEND_PADDING_SEC_SOURCE="env"
elif [[ -n "${PHASE5_STRICT_HOTSPOT_RECOMMEND_PADDING_SEC:-}" ]]; then
  DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_RECOMMEND_PADDING_SEC="${PHASE5_STRICT_HOTSPOT_RECOMMEND_PADDING_SEC}"
  DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_RECOMMEND_PADDING_SEC_SOURCE="inherited_env"
else
  DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_RECOMMEND_PADDING_SEC="0.1"
  DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_RECOMMEND_PADDING_SEC_SOURCE="local_default"
fi
if [[ -n "${DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_RECOMMEND_MIN_SAMPLE:-}" ]]; then
  DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_RECOMMEND_MIN_SAMPLE="${DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_RECOMMEND_MIN_SAMPLE}"
  DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_RECOMMEND_MIN_SAMPLE_SOURCE="env"
elif [[ -n "${PHASE5_STRICT_HOTSPOT_RECOMMEND_MIN_SAMPLE:-}" ]]; then
  DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_RECOMMEND_MIN_SAMPLE="${PHASE5_STRICT_HOTSPOT_RECOMMEND_MIN_SAMPLE}"
  DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_RECOMMEND_MIN_SAMPLE_SOURCE="inherited_env"
else
  DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_RECOMMEND_MIN_SAMPLE="5"
  DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_RECOMMEND_MIN_SAMPLE_SOURCE="local_default"
fi
if [[ -n "${DELIVERY_GATE_PHASE5_STRICT_FLAKY_RECOMMEND_PERCENTILE:-}" ]]; then
  DELIVERY_GATE_PHASE5_STRICT_FLAKY_RECOMMEND_PERCENTILE="${DELIVERY_GATE_PHASE5_STRICT_FLAKY_RECOMMEND_PERCENTILE}"
  DELIVERY_GATE_PHASE5_STRICT_FLAKY_RECOMMEND_PERCENTILE_SOURCE="env"
elif [[ -n "${PHASE5_STRICT_FLAKY_RISK_RECOMMEND_PERCENTILE:-}" ]]; then
  DELIVERY_GATE_PHASE5_STRICT_FLAKY_RECOMMEND_PERCENTILE="${PHASE5_STRICT_FLAKY_RISK_RECOMMEND_PERCENTILE}"
  DELIVERY_GATE_PHASE5_STRICT_FLAKY_RECOMMEND_PERCENTILE_SOURCE="inherited_env"
else
  DELIVERY_GATE_PHASE5_STRICT_FLAKY_RECOMMEND_PERCENTILE="0.9"
  DELIVERY_GATE_PHASE5_STRICT_FLAKY_RECOMMEND_PERCENTILE_SOURCE="local_default"
fi
if [[ -n "${DELIVERY_GATE_PHASE5_STRICT_FLAKY_RECOMMEND_STEP:-}" ]]; then
  DELIVERY_GATE_PHASE5_STRICT_FLAKY_RECOMMEND_STEP="${DELIVERY_GATE_PHASE5_STRICT_FLAKY_RECOMMEND_STEP}"
  DELIVERY_GATE_PHASE5_STRICT_FLAKY_RECOMMEND_STEP_SOURCE="env"
elif [[ -n "${PHASE5_STRICT_FLAKY_RISK_RECOMMEND_STEP:-}" ]]; then
  DELIVERY_GATE_PHASE5_STRICT_FLAKY_RECOMMEND_STEP="${PHASE5_STRICT_FLAKY_RISK_RECOMMEND_STEP}"
  DELIVERY_GATE_PHASE5_STRICT_FLAKY_RECOMMEND_STEP_SOURCE="inherited_env"
else
  DELIVERY_GATE_PHASE5_STRICT_FLAKY_RECOMMEND_STEP="1"
  DELIVERY_GATE_PHASE5_STRICT_FLAKY_RECOMMEND_STEP_SOURCE="local_default"
fi
if [[ -n "${DELIVERY_GATE_PHASE5_STRICT_FLAKY_RECOMMEND_MIN_SAMPLE:-}" ]]; then
  DELIVERY_GATE_PHASE5_STRICT_FLAKY_RECOMMEND_MIN_SAMPLE="${DELIVERY_GATE_PHASE5_STRICT_FLAKY_RECOMMEND_MIN_SAMPLE}"
  DELIVERY_GATE_PHASE5_STRICT_FLAKY_RECOMMEND_MIN_SAMPLE_SOURCE="env"
elif [[ -n "${PHASE5_STRICT_FLAKY_RISK_RECOMMEND_MIN_SAMPLE:-}" ]]; then
  DELIVERY_GATE_PHASE5_STRICT_FLAKY_RECOMMEND_MIN_SAMPLE="${PHASE5_STRICT_FLAKY_RISK_RECOMMEND_MIN_SAMPLE}"
  DELIVERY_GATE_PHASE5_STRICT_FLAKY_RECOMMEND_MIN_SAMPLE_SOURCE="inherited_env"
else
  DELIVERY_GATE_PHASE5_STRICT_FLAKY_RECOMMEND_MIN_SAMPLE="3"
  DELIVERY_GATE_PHASE5_STRICT_FLAKY_RECOMMEND_MIN_SAMPLE_SOURCE="local_default"
fi
if [[ -n "${DELIVERY_GATE_RECOVERY_REGISTRY_SYNC:-}" ]]; then
  DELIVERY_GATE_RECOVERY_REGISTRY_SYNC="${DELIVERY_GATE_RECOVERY_REGISTRY_SYNC}"
  DELIVERY_GATE_RECOVERY_REGISTRY_SYNC_SOURCE="env"
elif [[ -n "${CI:-}" ]]; then
  DELIVERY_GATE_RECOVERY_REGISTRY_SYNC="1"
  DELIVERY_GATE_RECOVERY_REGISTRY_SYNC_SOURCE="ci_default"
else
  DELIVERY_GATE_RECOVERY_REGISTRY_SYNC="0"
  DELIVERY_GATE_RECOVERY_REGISTRY_SYNC_SOURCE="local_default"
fi
if [[ -n "${DELIVERY_GATE_RECOVERY_REGISTRY_STRICT:-}" ]]; then
  DELIVERY_GATE_RECOVERY_REGISTRY_STRICT="${DELIVERY_GATE_RECOVERY_REGISTRY_STRICT}"
  DELIVERY_GATE_RECOVERY_REGISTRY_STRICT_SOURCE="env"
elif [[ -n "${PHASE5_RECOVERY_REGISTRY_STRICT:-}" ]]; then
  DELIVERY_GATE_RECOVERY_REGISTRY_STRICT="${PHASE5_RECOVERY_REGISTRY_STRICT}"
  DELIVERY_GATE_RECOVERY_REGISTRY_STRICT_SOURCE="inherited_env"
elif [[ -n "${CI:-}" ]]; then
  DELIVERY_GATE_RECOVERY_REGISTRY_STRICT="1"
  DELIVERY_GATE_RECOVERY_REGISTRY_STRICT_SOURCE="ci_default"
else
  DELIVERY_GATE_RECOVERY_REGISTRY_STRICT="0"
  DELIVERY_GATE_RECOVERY_REGISTRY_STRICT_SOURCE="local_default"
fi
if [[ -n "${DELIVERY_GATE_RECOVERY_REGISTRY_VERIFY_IDEMPOTENT:-}" ]]; then
  DELIVERY_GATE_RECOVERY_REGISTRY_VERIFY_IDEMPOTENT="${DELIVERY_GATE_RECOVERY_REGISTRY_VERIFY_IDEMPOTENT}"
  DELIVERY_GATE_RECOVERY_REGISTRY_VERIFY_IDEMPOTENT_SOURCE="env"
elif [[ -n "${CI:-}" ]]; then
  DELIVERY_GATE_RECOVERY_REGISTRY_VERIFY_IDEMPOTENT="1"
  DELIVERY_GATE_RECOVERY_REGISTRY_VERIFY_IDEMPOTENT_SOURCE="ci_default"
else
  DELIVERY_GATE_RECOVERY_REGISTRY_VERIFY_IDEMPOTENT="0"
  DELIVERY_GATE_RECOVERY_REGISTRY_VERIFY_IDEMPOTENT_SOURCE="local_default"
fi
if [[ "${DELIVERY_GATE_RECOVERY_REGISTRY_VERIFY_IDEMPOTENT}" == "1" && "${DELIVERY_GATE_RECOVERY_REGISTRY_SYNC}" != "1" ]]; then
  DELIVERY_GATE_RECOVERY_REGISTRY_SYNC="1"
  DELIVERY_GATE_RECOVERY_REGISTRY_SYNC_SOURCE="${DELIVERY_GATE_RECOVERY_REGISTRY_SYNC_SOURCE}_auto_verify_dependency"
fi
if [[ -n "${ECM_FULLSTACK_ALLOW_STATIC:-}" ]]; then
  ECM_FULLSTACK_ALLOW_STATIC="${ECM_FULLSTACK_ALLOW_STATIC}"
elif [[ -n "${CI:-}" ]]; then
  ECM_FULLSTACK_ALLOW_STATIC="0"
else
  ECM_FULLSTACK_ALLOW_STATIC="1"
fi

is_http_reachable() {
  local target="$1"
  curl -fsS --max-time 2 "${target}" >/dev/null 2>&1
}

resolve_fullstack_ui_url() {
  if [[ -n "${ECM_UI_URL_FULLSTACK_INPUT}" ]]; then
    printf '%s' "${ECM_UI_URL_FULLSTACK_INPUT}"
    return
  fi

  local candidates=(
    "http://localhost:3000"
    "http://localhost"
  )
  local candidate
  for candidate in "${candidates[@]}"; do
    if is_http_reachable "${candidate}"; then
      printf '%s' "${candidate}"
      return
    fi
  done
  printf '%s' "http://localhost"
}

print_usage() {
  cat <<'USAGE'
Usage:
  scripts/phase5-phase6-delivery-gate.sh [mode]
  scripts/phase5-phase6-delivery-gate.sh --mode=<mode> [--plan] [--plan-format=<text|json>] [--plan-file=<path>] [--print-strict-suggestions-json] [--strict-suggestions-file=<path>] [--phase5-summary-dir=<path>] [--phase5-strict-recovery-guard=<0|1>] [--phase5-strict-hotspot-sec=<num>] [--phase5-strict-flaky-score=<int>] [--phase5-hotspot-recommend-percentile=<num>] [--phase5-hotspot-recommend-padding-sec=<num>] [--phase5-hotspot-recommend-min-sample=<int>] [--phase5-flaky-recommend-percentile=<num>] [--phase5-flaky-recommend-step=<int>] [--phase5-flaky-recommend-min-sample=<int>]
  scripts/phase5-phase6-delivery-gate.sh --help

Modes:
  all                   Run fast mocked layer then integration layer.
  mocked                Run fast mocked layer only (preflight + mocked regression).
  preflight             Run mocked registry preflight only.
  integration           Run integration layer only.
  integration-preflight Run mocked registry preflight, then integration layer.

Flags:
  --help, -h            Show this message and exit.
  --plan                Print execution plan and exit.
  --no-plan             Do not print execution plan before execution.
  --print-plan          Force printing execution plan before execution.
  --plan-format=<...>   Execution plan format: text|json.
  --plan-json           Shortcut for --plan-format=json.
  --plan-text           Shortcut for --plan-format=text.
  --plan-file=<path>    Write execution plan payload to file.
  --print-strict-suggestions-json
                        Print structured strict remediation suggestions JSON in failure hints.
  --no-print-strict-suggestions-json
                        Disable structured strict remediation suggestions JSON printing.
  --strict-suggestions-file=<path>
                        Write structured strict remediation suggestions JSON artifact to file.
  --phase5-summary-dir=<path>
                        Write mocked phase5 regression summary JSON artifacts to directory.
  --phase5-strict-recovery-guard=<0|1>
                        Pass strict recovery guard mode to mocked phase5 regression.
  --phase5-strict-hotspot-sec=<num>
                        Pass strict hotspot duration threshold (seconds) to mocked phase5 regression.
  --phase5-strict-flaky-score=<int>
                        Pass strict flaky-risk score threshold to mocked phase5 regression.
  --phase5-hotspot-recommend-percentile=<num>
                        Percentile (0,1] used for hotspot recommendation (default: 0.95).
  --phase5-hotspot-recommend-padding-sec=<num>
                        Non-negative seconds added to hotspot percentile recommendation (default: 0.1).
  --phase5-hotspot-recommend-min-sample=<int>
                        Minimum sample count for hotspot percentile confidence (default: 5).
  --phase5-flaky-recommend-percentile=<num>
                        Percentile (0,1] used for flaky-risk recommendation (default: 0.9).
  --phase5-flaky-recommend-step=<int>
                        Non-negative step added for flaky-risk recommendation floor (default: 1).
  --phase5-flaky-recommend-min-sample=<int>
                        Minimum sample count for flaky-risk percentile confidence (default: 3).

Environment controls:
  DELIVERY_GATE_MODE
  DELIVERY_GATE_PRINT_EXECUTION_PLAN=1|0
  DELIVERY_GATE_EXECUTION_PLAN_FORMAT=text|json
  DELIVERY_GATE_EXECUTION_PLAN_FILE=<path>
  DELIVERY_GATE_PRINT_STRICT_SUGGESTIONS_JSON=1|0
  DELIVERY_GATE_STRICT_SUGGESTIONS_FILE=<path>
  DELIVERY_GATE_PHASE5_SUMMARY_DIR=<path>
  DELIVERY_GATE_PHASE5_RECOVERY_GUARD_STRICT=1|0
  DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_DURATION_SEC_THRESHOLD=<num>
  DELIVERY_GATE_PHASE5_STRICT_FLAKY_RISK_SCORE_THRESHOLD=<int>
  DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_RECOMMEND_PERCENTILE=<num>
  DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_RECOMMEND_PADDING_SEC=<num>
  DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_RECOMMEND_MIN_SAMPLE=<int>
  DELIVERY_GATE_PHASE5_STRICT_FLAKY_RECOMMEND_PERCENTILE=<num>
  DELIVERY_GATE_PHASE5_STRICT_FLAKY_RECOMMEND_STEP=<int>
  DELIVERY_GATE_PHASE5_STRICT_FLAKY_RECOMMEND_MIN_SAMPLE=<int>
  DELIVERY_GATE_RECOVERY_REGISTRY_SYNC=1|0
  DELIVERY_GATE_RECOVERY_REGISTRY_STRICT=1|0
  DELIVERY_GATE_RECOVERY_REGISTRY_VERIFY_IDEMPOTENT=1|0
USAGE
}

strip_ansi_file() {
  local log_file="$1"
  sed -E $'s/\x1B\\[[0-9;]*[[:alpha:]]//g' "${log_file}" | tr -d '\r'
}

print_playwright_failure_summary() {
  local stage_label="$1"
  local log_file="$2"
  local failed_specs=()

  mapfile -t failed_specs < <(
    strip_ansi_file "${log_file}" \
      | rg "^[[:space:]]*\\[[^]]+\\][[:space:]]+›[[:space:]]+e2e/" \
      | sed -E 's/^[[:space:]]*\[[^]]+\][[:space:]]+›[[:space:]]+//'
  )

  if [[ "${#failed_specs[@]}" -gt 0 ]]; then
    echo "phase5_phase6_delivery_gate: ${stage_label} failed specs"
    local spec_line
    for spec_line in "${failed_specs[@]}"; do
      echo " - ${spec_line}"
    done
    return
  fi

  local first_error
  first_error="$(strip_ansi_file "${log_file}" | rg -m1 "(^Error:|^error:|error: )" || true)"
  if [[ -n "${first_error}" ]]; then
    echo "phase5_phase6_delivery_gate: ${stage_label} first error => ${first_error}"
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

build_execution_plan_payload() {
  local preflight_executor="phase5-regression (registry-only)"
  if [[ "${DELIVERY_GATE_RECOVERY_REGISTRY_SYNC}" == "1" && "${DELIVERY_GATE_RECOVERY_REGISTRY_VERIFY_IDEMPOTENT}" == "1" ]]; then
    preflight_executor="phase5-sync-recovery-registry.sh (sync + idempotence check)"
  fi

  local fast_layer_plan=""
  local mocked_regression_plan=""
  local integration_layer_plan=""
  case "${DELIVERY_GATE_MODE}" in
    all)
      fast_layer_plan="mocked recovery registry preflight + mocked regression gate"
      mocked_regression_plan="enabled"
      integration_layer_plan="dependency preflight + full-stack smokes + p1 smoke"
      ;;
    mocked)
      fast_layer_plan="mocked recovery registry preflight + mocked regression gate"
      mocked_regression_plan="enabled"
      integration_layer_plan="skipped"
      ;;
    preflight)
      fast_layer_plan="mocked recovery registry preflight only"
      mocked_regression_plan="skipped"
      integration_layer_plan="skipped"
      ;;
    integration)
      fast_layer_plan="skipped"
      mocked_regression_plan="skipped"
      integration_layer_plan="dependency preflight + full-stack smokes + p1 smoke"
      ;;
    integration-preflight)
      fast_layer_plan="mocked recovery registry preflight only"
      mocked_regression_plan="skipped"
      integration_layer_plan="dependency preflight + full-stack smokes + p1 smoke"
      ;;
  esac

  if [[ "${DELIVERY_GATE_EXECUTION_PLAN_FORMAT}" == "json" ]]; then
    cat <<JSON
{
  "schema_version": 1,
  "mode": "${DELIVERY_GATE_MODE}",
  "fast_registry_preflight_executor": "${preflight_executor}",
  "integration_strict_fullstack_preflight_condition": "ECM_FULLSTACK_ALLOW_STATIC=0",
  "print_strict_suggestions_json": "${DELIVERY_GATE_PRINT_STRICT_SUGGESTIONS_JSON}",
  "strict_suggestions_file": "${DELIVERY_GATE_STRICT_SUGGESTIONS_FILE:-"(unset)"}",
  "mocked_regression_summary_dir": "${DELIVERY_GATE_PHASE5_SUMMARY_DIR:-"(unset)"}",
  "phase5_recovery_guard_strict": "${DELIVERY_GATE_PHASE5_RECOVERY_GUARD_STRICT}",
  "phase5_strict_hotspot_duration_sec_threshold": "${DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_DURATION_SEC_THRESHOLD}",
  "phase5_strict_flaky_risk_score_threshold": "${DELIVERY_GATE_PHASE5_STRICT_FLAKY_RISK_SCORE_THRESHOLD}",
  "phase5_strict_hotspot_recommend_percentile": "${DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_RECOMMEND_PERCENTILE}",
  "phase5_strict_hotspot_recommend_padding_sec": "${DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_RECOMMEND_PADDING_SEC}",
  "phase5_strict_hotspot_recommend_min_sample": "${DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_RECOMMEND_MIN_SAMPLE}",
  "phase5_strict_flaky_recommend_percentile": "${DELIVERY_GATE_PHASE5_STRICT_FLAKY_RECOMMEND_PERCENTILE}",
  "phase5_strict_flaky_recommend_step": "${DELIVERY_GATE_PHASE5_STRICT_FLAKY_RECOMMEND_STEP}",
  "phase5_strict_flaky_recommend_min_sample": "${DELIVERY_GATE_PHASE5_STRICT_FLAKY_RECOMMEND_MIN_SAMPLE}",
  "fast_layer": "${fast_layer_plan}",
  "mocked_regression": "${mocked_regression_plan}",
  "integration_layer": "${integration_layer_plan}"
}
JSON
    return
  fi

  cat <<TEXT

phase5_phase6_delivery_gate: execution plan
 - schema version: 1
 - mode: ${DELIVERY_GATE_MODE}
 - fast registry preflight executor: ${preflight_executor}
 - integration strict-fullstack preflight enabled when ECM_FULLSTACK_ALLOW_STATIC=0
 - print strict suggestions json: ${DELIVERY_GATE_PRINT_STRICT_SUGGESTIONS_JSON}
 - strict suggestions file: ${DELIVERY_GATE_STRICT_SUGGESTIONS_FILE:-"(unset)"}
 - mocked regression summary dir: ${DELIVERY_GATE_PHASE5_SUMMARY_DIR:-"(unset)"}
 - phase5 recovery guard strict: ${DELIVERY_GATE_PHASE5_RECOVERY_GUARD_STRICT}
 - phase5 strict hotspot threshold (sec): ${DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_DURATION_SEC_THRESHOLD}
 - phase5 strict flaky-risk score threshold: ${DELIVERY_GATE_PHASE5_STRICT_FLAKY_RISK_SCORE_THRESHOLD}
 - phase5 hotspot recommend percentile: ${DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_RECOMMEND_PERCENTILE}
 - phase5 hotspot recommend padding (sec): ${DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_RECOMMEND_PADDING_SEC}
 - phase5 hotspot recommend min sample: ${DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_RECOMMEND_MIN_SAMPLE}
 - phase5 flaky-risk recommend percentile: ${DELIVERY_GATE_PHASE5_STRICT_FLAKY_RECOMMEND_PERCENTILE}
 - phase5 flaky-risk recommend step: ${DELIVERY_GATE_PHASE5_STRICT_FLAKY_RECOMMEND_STEP}
 - phase5 flaky-risk recommend min sample: ${DELIVERY_GATE_PHASE5_STRICT_FLAKY_RECOMMEND_MIN_SAMPLE}
 - fast layer: ${fast_layer_plan}
 - mocked regression: ${mocked_regression_plan}
 - integration layer: ${integration_layer_plan}
TEXT
}

write_execution_plan_file() {
  local plan_payload="$1"
  if [[ -z "${DELIVERY_GATE_EXECUTION_PLAN_FILE}" ]]; then
    return
  fi

  local plan_dir
  plan_dir="$(dirname "${DELIVERY_GATE_EXECUTION_PLAN_FILE}")"
  if [[ "${plan_dir}" != "." ]]; then
    mkdir -p "${plan_dir}"
  fi
  printf '%s\n' "${plan_payload}" >"${DELIVERY_GATE_EXECUTION_PLAN_FILE}"
  echo "phase5_phase6_delivery_gate: wrote execution plan => ${DELIVERY_GATE_EXECUTION_PLAN_FILE}"
}

build_strict_suggestions_payload() {
  local records_file="$1"
  local strict_reasons_csv="$2"
  local summary_file="$3"

  node - "${records_file}" "${strict_reasons_csv}" "${summary_file}" "${DELIVERY_GATE_MODE}" <<'NODE'
const fs = require('fs');

const recordsFile = process.argv[2] || '';
const strictReasonsCsv = process.argv[3] || '';
const summaryFile = process.argv[4] || '';
const mode = process.argv[5] || '';

const strictFailureReasons = strictReasonsCsv
  .split(',')
  .map((item) => item.trim())
  .filter((item) => item.length > 0);

let suggestions = [];
if (recordsFile && fs.existsSync(recordsFile)) {
  const lines = fs.readFileSync(recordsFile, 'utf8').replace(/\r/g, '').split('\n');
  suggestions = lines
    .map((line) => line.trim())
    .filter((line) => line.length > 0)
    .map((line) => {
      const [priorityRaw, reason, action, scope, ...commandParts] = line.split('\t');
      const priority = Number.parseInt(priorityRaw, 10);
      return {
        priority: Number.isFinite(priority) && priority > 0 ? priority : 0,
        reason: String(reason || '').trim() || 'unknown',
        action: String(action || '').trim() || 'unknown',
        scope: String(scope || '').trim() || 'global',
        command: commandParts.join('\t').trim(),
      };
    })
    .filter((item) => item.command.length > 0)
    .sort((left, right) => left.priority - right.priority);
}

const payload = {
  schema_version: 1,
  source: 'phase5-phase6-delivery-gate.strict-hints',
  generated_at_utc: new Date().toISOString(),
  mode,
  strict_failure_reasons: strictFailureReasons,
  summary_file: summaryFile,
  suggestion_count: suggestions.length,
  suggestions,
};

process.stdout.write(`${JSON.stringify(payload, null, 2)}\n`);
NODE
}

emit_strict_suggestions_artifact() {
  local records_file="$1"
  local strict_reasons_csv="$2"
  local summary_file="$3"

  if [[ "${DELIVERY_GATE_PRINT_STRICT_SUGGESTIONS_JSON}" != "1" && -z "${DELIVERY_GATE_STRICT_SUGGESTIONS_FILE}" ]]; then
    return
  fi

  local strict_payload=""
  if ! strict_payload="$(build_strict_suggestions_payload "${records_file}" "${strict_reasons_csv}" "${summary_file}")"; then
    echo " - WARN failed to build strict suggestions JSON payload."
    return
  fi

  if [[ "${DELIVERY_GATE_PRINT_STRICT_SUGGESTIONS_JSON}" == "1" ]]; then
    echo " - Strict suggestions JSON:"
    printf '%s\n' "${strict_payload}"
  fi

  if [[ -n "${DELIVERY_GATE_STRICT_SUGGESTIONS_FILE}" ]]; then
    local strict_file_dir
    strict_file_dir="$(dirname "${DELIVERY_GATE_STRICT_SUGGESTIONS_FILE}")"
    if [[ "${strict_file_dir}" != "." ]]; then
      mkdir -p "${strict_file_dir}"
    fi
    printf '%s\n' "${strict_payload}" >"${DELIVERY_GATE_STRICT_SUGGESTIONS_FILE}"
    echo " - Wrote strict suggestions artifact: ${DELIVERY_GATE_STRICT_SUGGESTIONS_FILE}"
  fi
}

STAGE_RESULTS=()
FAILED_STAGE_RECORDS=()

run_stage() {
  local layer="$1"
  local stage_key="$2"
  local stage_label="$3"
  shift 3

  local stage_log
  stage_log="$(mktemp "/tmp/${stage_key}.XXXXXX")"

  echo ""
  echo "[${layer}] ${stage_label}"
  local stage_rc=0
  run_with_tee "${stage_log}" "$@" || stage_rc=$?
  if [[ "${stage_rc}" -eq 0 ]]; then
    STAGE_RESULTS+=("${layer}|${stage_label}|PASS|${stage_log}")
    return 0
  fi

  STAGE_RESULTS+=("${layer}|${stage_label}|FAIL(${stage_rc})|${stage_log}")
  FAILED_STAGE_RECORDS+=("${layer}|${stage_label}|${stage_log}|${stage_rc}")
  print_playwright_failure_summary "${stage_label}" "${stage_log}"
  echo "phase5_phase6_delivery_gate: stage log => ${stage_log}"
  return "${stage_rc}"
}

summarize_unique_events() {
  printf '%s\n' "$@" \
    | awk 'NF && !seen[$0]++ {
        events[++count] = $0
      }
      END {
        limit = count < 8 ? count : 8
        for (i = 1; i <= limit; i++) {
          printf "%s%s", (i > 1 ? ", " : ""), events[i]
        }
      }'
}

print_layer_summary() {
  local layer="$1"
  local layer_has_stage=0
  local record
  for record in "${STAGE_RESULTS[@]}"; do
    local record_layer
    local record_label
    local record_status
    local record_log
    IFS='|' read -r record_layer record_label record_status record_log <<< "${record}"
    if [[ "${record_layer}" != "${layer}" ]]; then
      continue
    fi
    layer_has_stage=1
    echo " - [${record_status}] ${record_label}"
  done
  if [[ "${layer_has_stage}" -eq 0 ]]; then
    echo " - (not executed)"
  fi
}

print_gate_summary() {
  echo ""
  echo "phase5_phase6_delivery_gate: layer summary"
  echo "mode=${DELIVERY_GATE_MODE}"
  echo "fast mocked layer:"
  print_layer_summary "fast"
  echo "integration/full-stack layer:"
  print_layer_summary "integration"
}

extract_phase5_summary_path_from_log() {
  local log_file="$1"
  local summary_path=""
  summary_path="$(strip_ansi_file "${log_file}" | sed -nE 's#^.*phase5_regression: wrote summary artifact =>[[:space:]]*(.+)$#\1#p' | tail -n 1)"
  if [[ -n "${summary_path}" ]]; then
    printf '%s' "${summary_path}"
    return
  fi
  summary_path="$(strip_ansi_file "${log_file}" | sed -nE 's#^.*mocked regression summary generated =>[[:space:]]*(.+)$#\1#p' | tail -n 1)"
  printf '%s' "${summary_path}"
}

extract_phase5_summary_strict_kv() {
  local summary_file="$1"
  node - "${summary_file}" <<'NODE'
const fs = require('fs');

const summaryFile = process.argv[2];
if (!summaryFile || !fs.existsSync(summaryFile)) {
  process.exit(0);
}

let payload;
try {
  payload = JSON.parse(fs.readFileSync(summaryFile, 'utf8'));
} catch (_error) {
  process.exit(0);
}

const strict = payload?.strict_threshold_controls ?? {};
const recovery = payload?.recovery_guard ?? {};
const toCsv = (items) => (
  Array.isArray(items)
    ? items
      .map((item) => String(item ?? '').trim())
      .filter((item) => item.length > 0)
      .join(',')
    : ''
);
const emit = (key, value) => {
  console.log(`${key}=${String(value ?? '')}`);
};

emit('strict_guard_failed', strict?.strict_guard_failed ? '1' : '0');
emit('strict_failure_reasons', toCsv(strict?.strict_failure_reasons));
emit('hotspot_threshold', strict?.hotspot_duration_sec_threshold ?? '');
emit('hotspot_recommended_threshold', strict?.hotspot_recommended_threshold ?? '');
emit('hotspot_recommended_percentile', strict?.hotspot_recommended_percentile ?? '');
emit('hotspot_recommended_sample', strict?.hotspot_recommended_sample ?? '');
emit('hotspot_recommend_min_sample', strict?.hotspot_recommend_min_sample ?? '');
emit('hotspot_recommended_count', strict?.hotspot_recommended_count ?? '');
emit('hotspot_recommendation_low_confidence', strict?.hotspot_recommendation_low_confidence ? '1' : '0');
emit('hotspot_recommendation_confidence_level', strict?.hotspot_recommendation_confidence_level ?? '');
emit('hotspot_recommendation_reason_code', strict?.hotspot_recommendation_reason_code ?? '');
emit('hotspot_recommended_min_sample', strict?.hotspot_recommended_min_sample ?? '');
emit('hotspot_match_count', strict?.hotspot_match_count ?? 0);
emit('flaky_threshold', strict?.flaky_risk_score_threshold ?? '');
emit('flaky_recommended_threshold', strict?.flaky_recommended_threshold ?? '');
emit('flaky_recommended_percentile', strict?.flaky_recommended_percentile ?? '');
emit('flaky_recommended_sample', strict?.flaky_recommended_sample ?? '');
emit('flaky_recommend_min_sample', strict?.flaky_recommend_min_sample ?? '');
emit('flaky_recommended_count', strict?.flaky_recommended_count ?? '');
emit('flaky_recommendation_low_confidence', strict?.flaky_recommendation_low_confidence ? '1' : '0');
emit('flaky_recommendation_confidence_level', strict?.flaky_recommendation_confidence_level ?? '');
emit('flaky_recommendation_reason_code', strict?.flaky_recommendation_reason_code ?? '');
emit('flaky_recommended_min_sample', strict?.flaky_recommended_min_sample ?? '');
emit('flaky_match_count', strict?.flaky_risk_match_count ?? 0);
emit('recovery_warning_count', recovery?.warning_count ?? 0);
emit('recovery_missing', toCsv(recovery?.missing_events));
emit('recovery_unexpected', toCsv(recovery?.unexpected_events));
NODE
}

print_startup_failure_hints() {
  if [[ "${#FAILED_STAGE_RECORDS[@]}" -eq 0 ]]; then
    return
  fi

  local hint_static_target=0
  local hint_storage_restricted=0
  local hint_auth_timeout=0
  local hint_startup_sla_warn=0
  local hint_startup_sla_drift_warn=0
  local hint_recovery_guard_warn=0
  local hint_recovery_registry_warn=0
  local hint_recovery_registry_deterministic_warn=0
  local hint_phase5_strict_threshold_warn=0
  local phase5_summary_file_for_hints=""
  local recovery_missing_events=()
  local recovery_unexpected_events=()
  local registry_missing_events=()
  local registry_stale_events=()
  local phase5_strict_reasons=()
  local phase5_hotspot_threshold=""
  local phase5_hotspot_match_count=""
  local phase5_hotspot_recommended_threshold=""
  local phase5_hotspot_recommended_percentile=""
  local phase5_hotspot_recommended_sample=""
  local phase5_hotspot_recommend_min_sample=""
  local phase5_hotspot_recommended_count=""
  local phase5_hotspot_recommendation_low_confidence=0
  local phase5_hotspot_recommendation_confidence_level=""
  local phase5_hotspot_recommendation_reason_code=""
  local phase5_hotspot_recommended_min_sample=""
  local phase5_flaky_threshold=""
  local phase5_flaky_match_count=""
  local phase5_flaky_recommended_threshold=""
  local phase5_flaky_recommended_percentile=""
  local phase5_flaky_recommended_sample=""
  local phase5_flaky_recommend_min_sample=""
  local phase5_flaky_recommended_count=""
  local phase5_flaky_recommendation_low_confidence=0
  local phase5_flaky_recommendation_confidence_level=""
  local phase5_flaky_recommendation_reason_code=""
  local phase5_flaky_recommended_min_sample=""
  local phase5_recovery_missing_events=()
  local phase5_recovery_unexpected_events=()
  local record
  for record in "${FAILED_STAGE_RECORDS[@]}"; do
    local record_layer
    local record_label
    local record_log
    local record_rc
    IFS='|' read -r record_layer record_label record_log record_rc <<< "${record}"

    if strip_ansi_file "${record_log}" | rg -qi "detected static/prebuilt bundle target|stale prebuilt|dirty_worktree|missing_manifest|committed_source_newer_than_build"; then
      hint_static_target=1
    fi
    if strip_ansi_file "${record_log}" | rg -qi "sessionstorage|localstorage|securityerror|storage blocked"; then
      hint_storage_restricted=1
    fi
    if strip_ansi_file "${record_log}" | rg -qi "auth initialization timed out|sign-in initialization timed out|keycloak init timeout|request timed out|econnaborted"; then
      hint_auth_timeout=1
    fi
    if strip_ansi_file "${record_log}" | rg -qi "phase5_regression: startup SLA warning count: [1-9][0-9]*"; then
      hint_startup_sla_warn=1
    fi
    if strip_ansi_file "${record_log}" | rg -qi "phase5_regression: startup SLA drift warning count: [1-9][0-9]*"; then
      hint_startup_sla_drift_warn=1
    fi
    if strip_ansi_file "${record_log}" | rg -qi "phase5_regression: recovery guard warning count: [1-9][0-9]*"; then
      hint_recovery_guard_warn=1
      local missing_event
      while IFS= read -r missing_event; do
        if [[ -n "${missing_event}" ]]; then
          recovery_missing_events+=("${missing_event}")
        fi
      done < <(
        strip_ansi_file "${record_log}" \
          | sed -nE 's/^[[:space:]]*-[[:space:]]*WARN missing event:[[:space:]]*([a-z0-9_]+).*/\1/p'
      )
      local unexpected_event
      while IFS= read -r unexpected_event; do
        if [[ -n "${unexpected_event}" ]]; then
          recovery_unexpected_events+=("${unexpected_event}")
        fi
      done < <(
        strip_ansi_file "${record_log}" \
          | sed -nE 's/^[[:space:]]*-[[:space:]]*WARN unexpected event:[[:space:]]*([a-z0-9_]+).*/\1/p'
      )
    fi
    if strip_ansi_file "${record_log}" | rg -qi "registry mismatch count: [1-9][0-9]*|strict mode enabled: failing due to registry mismatch"; then
      hint_recovery_registry_warn=1
      local missing_csv_line
      while IFS= read -r missing_csv_line; do
        [[ -z "${missing_csv_line}" || "${missing_csv_line}" == "none" ]] && continue
        IFS=',' read -r -a missing_csv_items <<< "${missing_csv_line}"
        local missing_csv_event
        for missing_csv_event in "${missing_csv_items[@]}"; do
          missing_csv_event="$(printf '%s' "${missing_csv_event}" | tr -d '[:space:]')"
          [[ -z "${missing_csv_event}" || "${missing_csv_event}" == "none" ]] && continue
          registry_missing_events+=("${missing_csv_event}")
        done
      done < <(
        strip_ansi_file "${record_log}" \
          | sed -nE 's/^[[:space:]]*-[[:space:]]*DIFF missing_from_events_file_csv:[[:space:]]*([a-z0-9_,]+|none).*/\1/p'
      )
      local stale_csv_line
      while IFS= read -r stale_csv_line; do
        [[ -z "${stale_csv_line}" || "${stale_csv_line}" == "none" ]] && continue
        IFS=',' read -r -a stale_csv_items <<< "${stale_csv_line}"
        local stale_csv_event
        for stale_csv_event in "${stale_csv_items[@]}"; do
          stale_csv_event="$(printf '%s' "${stale_csv_event}" | tr -d '[:space:]')"
          [[ -z "${stale_csv_event}" || "${stale_csv_event}" == "none" ]] && continue
          registry_stale_events+=("${stale_csv_event}")
        done
      done < <(
        strip_ansi_file "${record_log}" \
          | sed -nE 's/^[[:space:]]*-[[:space:]]*DIFF stale_events_file_entries_csv:[[:space:]]*([a-z0-9_,]+|none).*/\1/p'
      )
      local missing_in_file_event
      while IFS= read -r missing_in_file_event; do
        if [[ -n "${missing_in_file_event}" ]]; then
          registry_missing_events+=("${missing_in_file_event}")
        fi
      done < <(
        strip_ansi_file "${record_log}" \
          | sed -nE 's/^[[:space:]]*-[[:space:]]*WARN marker missing from events file:[[:space:]]*([a-z0-9_]+).*/\1/p'
      )
      local stale_entry_event
      while IFS= read -r stale_entry_event; do
        if [[ -n "${stale_entry_event}" ]]; then
          registry_stale_events+=("${stale_entry_event}")
        fi
      done < <(
        strip_ansi_file "${record_log}" \
          | sed -nE 's/^[[:space:]]*-[[:space:]]*WARN events file entry not found in specs:[[:space:]]*([a-z0-9_]+).*/\1/p'
      )
    fi
    if strip_ansi_file "${record_log}" | rg -qi "phase5_sync_recovery_registry: deterministic mismatch"; then
      hint_recovery_registry_deterministic_warn=1
    fi

    local summary_file
    summary_file="$(extract_phase5_summary_path_from_log "${record_log}")"
    if [[ -n "${summary_file}" && -f "${summary_file}" ]]; then
      phase5_summary_file_for_hints="${summary_file}"
      local summary_line
      while IFS= read -r summary_line; do
        [[ -z "${summary_line}" ]] && continue
        local summary_key="${summary_line%%=*}"
        local summary_value="${summary_line#*=}"
        case "${summary_key}" in
          strict_guard_failed)
            if [[ "${summary_value}" == "1" ]]; then
              hint_phase5_strict_threshold_warn=1
            fi
            ;;
          strict_failure_reasons)
            if [[ -n "${summary_value}" ]]; then
              local summary_reason
              IFS=',' read -r -a summary_reason_items <<< "${summary_value}"
              for summary_reason in "${summary_reason_items[@]}"; do
                summary_reason="$(printf '%s' "${summary_reason}" | tr -d '[:space:]')"
                [[ -z "${summary_reason}" ]] && continue
                phase5_strict_reasons+=("${summary_reason}")
              done
            fi
            ;;
          hotspot_threshold)
            if [[ -n "${summary_value}" && "${summary_value}" != "0" ]]; then
              phase5_hotspot_threshold="${summary_value}"
            fi
            ;;
          hotspot_recommended_threshold)
            if [[ -n "${summary_value}" && "${summary_value}" != "0" ]]; then
              phase5_hotspot_recommended_threshold="${summary_value}"
            fi
            ;;
          hotspot_recommended_percentile)
            if [[ -n "${summary_value}" && "${summary_value}" != "0" ]]; then
              phase5_hotspot_recommended_percentile="${summary_value}"
            fi
            ;;
          hotspot_recommended_sample)
            if [[ -n "${summary_value}" && "${summary_value}" != "0" ]]; then
              phase5_hotspot_recommended_sample="${summary_value}"
            fi
            ;;
          hotspot_recommend_min_sample)
            if [[ -n "${summary_value}" ]]; then
              phase5_hotspot_recommend_min_sample="${summary_value}"
            fi
            ;;
          hotspot_recommended_count)
            if [[ -n "${summary_value}" ]]; then
              phase5_hotspot_recommended_count="${summary_value}"
            fi
            ;;
          hotspot_recommendation_low_confidence)
            if [[ "${summary_value}" == "1" ]]; then
              phase5_hotspot_recommendation_low_confidence=1
            fi
            ;;
          hotspot_recommendation_confidence_level)
            if [[ -n "${summary_value}" ]]; then
              phase5_hotspot_recommendation_confidence_level="${summary_value}"
            fi
            ;;
          hotspot_recommendation_reason_code)
            if [[ -n "${summary_value}" ]]; then
              phase5_hotspot_recommendation_reason_code="${summary_value}"
            fi
            ;;
          hotspot_recommended_min_sample)
            if [[ -n "${summary_value}" ]]; then
              phase5_hotspot_recommended_min_sample="${summary_value}"
            fi
            ;;
          hotspot_match_count)
            if [[ -n "${summary_value}" && "${summary_value}" != "0" ]]; then
              phase5_hotspot_match_count="${summary_value}"
            fi
            ;;
          flaky_threshold)
            if [[ -n "${summary_value}" && "${summary_value}" != "0" ]]; then
              phase5_flaky_threshold="${summary_value}"
            fi
            ;;
          flaky_recommended_threshold)
            if [[ -n "${summary_value}" && "${summary_value}" != "0" ]]; then
              phase5_flaky_recommended_threshold="${summary_value}"
            fi
            ;;
          flaky_recommended_percentile)
            if [[ -n "${summary_value}" && "${summary_value}" != "0" ]]; then
              phase5_flaky_recommended_percentile="${summary_value}"
            fi
            ;;
          flaky_recommended_sample)
            if [[ -n "${summary_value}" && "${summary_value}" != "0" ]]; then
              phase5_flaky_recommended_sample="${summary_value}"
            fi
            ;;
          flaky_recommend_min_sample)
            if [[ -n "${summary_value}" ]]; then
              phase5_flaky_recommend_min_sample="${summary_value}"
            fi
            ;;
          flaky_recommended_count)
            if [[ -n "${summary_value}" ]]; then
              phase5_flaky_recommended_count="${summary_value}"
            fi
            ;;
          flaky_recommendation_low_confidence)
            if [[ "${summary_value}" == "1" ]]; then
              phase5_flaky_recommendation_low_confidence=1
            fi
            ;;
          flaky_recommendation_confidence_level)
            if [[ -n "${summary_value}" ]]; then
              phase5_flaky_recommendation_confidence_level="${summary_value}"
            fi
            ;;
          flaky_recommendation_reason_code)
            if [[ -n "${summary_value}" ]]; then
              phase5_flaky_recommendation_reason_code="${summary_value}"
            fi
            ;;
          flaky_recommended_min_sample)
            if [[ -n "${summary_value}" ]]; then
              phase5_flaky_recommended_min_sample="${summary_value}"
            fi
            ;;
          flaky_match_count)
            if [[ -n "${summary_value}" && "${summary_value}" != "0" ]]; then
              phase5_flaky_match_count="${summary_value}"
            fi
            ;;
          recovery_missing)
            if [[ -n "${summary_value}" ]]; then
              local summary_missing_event
              IFS=',' read -r -a summary_missing_items <<< "${summary_value}"
              for summary_missing_event in "${summary_missing_items[@]}"; do
                summary_missing_event="$(printf '%s' "${summary_missing_event}" | tr -d '[:space:]')"
                [[ -z "${summary_missing_event}" ]] && continue
                phase5_recovery_missing_events+=("${summary_missing_event}")
              done
            fi
            ;;
          recovery_unexpected)
            if [[ -n "${summary_value}" ]]; then
              local summary_unexpected_event
              IFS=',' read -r -a summary_unexpected_items <<< "${summary_value}"
              for summary_unexpected_event in "${summary_unexpected_items[@]}"; do
                summary_unexpected_event="$(printf '%s' "${summary_unexpected_event}" | tr -d '[:space:]')"
                [[ -z "${summary_unexpected_event}" ]] && continue
                phase5_recovery_unexpected_events+=("${summary_unexpected_event}")
              done
            fi
            ;;
        esac
      done < <(extract_phase5_summary_strict_kv "${summary_file}")
    fi
  done

  if [[ "${hint_static_target}" -eq 0 && "${hint_storage_restricted}" -eq 0 && "${hint_auth_timeout}" -eq 0 && "${hint_startup_sla_warn}" -eq 0 && "${hint_startup_sla_drift_warn}" -eq 0 && "${hint_recovery_guard_warn}" -eq 0 && "${hint_recovery_registry_warn}" -eq 0 && "${hint_recovery_registry_deterministic_warn}" -eq 0 && "${hint_phase5_strict_threshold_warn}" -eq 0 ]]; then
    return
  fi

  echo ""
  echo "phase5_phase6_delivery_gate: startup diagnostics hints"
  if [[ "${hint_static_target}" -eq 1 ]]; then
    echo " - Static/prebuilt target may be stale. Prefer dev target (:3000) or run prebuilt sync before rerun."
  fi
  if [[ "${hint_storage_restricted}" -eq 1 ]]; then
    echo " - Storage APIs may be restricted. Verify browser privacy policy / extension isolation and rerun startup matrix."
  fi
  if [[ "${hint_auth_timeout}" -eq 1 ]]; then
    echo " - Auth bootstrap timeout symptoms detected. Check Keycloak reachability and timeout env budgets."
  fi
  if [[ "${hint_startup_sla_warn}" -eq 1 ]]; then
    echo " - Startup visibility SLA warnings detected. Review 'phase5_regression: startup SLA status' for near-threshold routes."
  fi
  if [[ "${hint_startup_sla_drift_warn}" -eq 1 ]]; then
    echo " - Startup latency drift warnings detected. Compare against baseline and investigate runtime variance/regression."
  fi
  if [[ "${hint_recovery_guard_warn}" -eq 1 ]]; then
    local missing_events_line=""
    local unexpected_events_line=""
    if [[ "${#recovery_missing_events[@]}" -gt 0 ]]; then
      missing_events_line="$(summarize_unique_events "${recovery_missing_events[@]}")"
    fi
    if [[ "${#recovery_unexpected_events[@]}" -gt 0 ]]; then
      unexpected_events_line="$(summarize_unique_events "${recovery_unexpected_events[@]}")"
    fi

    if [[ -n "${missing_events_line}" && -n "${unexpected_events_line}" ]]; then
      echo " - Recovery guard coverage appears incomplete. Missing events: ${missing_events_line}. Unexpected events: ${unexpected_events_line}."
    elif [[ -n "${missing_events_line}" ]]; then
      echo " - Recovery guard coverage appears incomplete. Missing events: ${missing_events_line}."
    elif [[ -n "${unexpected_events_line}" ]]; then
      echo " - Recovery guard coverage appears incomplete. Unexpected events: ${unexpected_events_line}."
    else
      echo " - Recovery guard coverage appears incomplete. Inspect 'phase5_regression: recovery guard status' for missing startup/error events."
    fi
  fi
  if [[ "${hint_recovery_registry_warn}" -eq 1 ]]; then
    local registry_missing_line=""
    local registry_stale_line=""
    if [[ "${#registry_missing_events[@]}" -gt 0 ]]; then
      registry_missing_line="$(summarize_unique_events "${registry_missing_events[@]}")"
    fi
    if [[ "${#registry_stale_events[@]}" -gt 0 ]]; then
      registry_stale_line="$(summarize_unique_events "${registry_stale_events[@]}")"
    fi
    if [[ -n "${registry_missing_line}" && -n "${registry_stale_line}" ]]; then
      echo " - Recovery registry mismatch detected. Missing in events file: ${registry_missing_line}. Stale entries: ${registry_stale_line}."
    elif [[ -n "${registry_missing_line}" ]]; then
      echo " - Recovery registry mismatch detected. Missing in events file: ${registry_missing_line}."
    elif [[ -n "${registry_stale_line}" ]]; then
      echo " - Recovery registry mismatch detected. Stale entries: ${registry_stale_line}."
    else
      echo " - Recovery registry mismatch detected. Inspect 'validate recovery event registry' output for mismatch details."
    fi
    if [[ "${DELIVERY_GATE_RECOVERY_REGISTRY_SYNC}" != "1" ]]; then
      echo " - To auto-sync registry in preflight, rerun with DELIVERY_GATE_RECOVERY_REGISTRY_SYNC=1 or run scripts/phase5-sync-recovery-registry.sh."
    elif [[ "${DELIVERY_GATE_RECOVERY_REGISTRY_VERIFY_IDEMPOTENT}" != "1" ]]; then
      echo " - To enforce sync determinism in preflight, rerun with DELIVERY_GATE_RECOVERY_REGISTRY_VERIFY_IDEMPOTENT=1."
    fi
  fi
  if [[ "${hint_recovery_registry_deterministic_warn}" -eq 1 ]]; then
    echo " - Registry sync deterministic check failed. Rerun scripts/phase5-sync-recovery-registry.sh and inspect emitted diff."
  fi
  if [[ "${hint_phase5_strict_threshold_warn}" -eq 1 ]]; then
    local strict_reasons_line=""
    local strict_recovery_missing_line=""
    local strict_recovery_unexpected_line=""
    if [[ "${#phase5_strict_reasons[@]}" -gt 0 ]]; then
      strict_reasons_line="$(summarize_unique_events "${phase5_strict_reasons[@]}")"
    fi
    if [[ "${#phase5_recovery_missing_events[@]}" -gt 0 ]]; then
      strict_recovery_missing_line="$(summarize_unique_events "${phase5_recovery_missing_events[@]}")"
    fi
    if [[ "${#phase5_recovery_unexpected_events[@]}" -gt 0 ]]; then
      strict_recovery_unexpected_line="$(summarize_unique_events "${phase5_recovery_unexpected_events[@]}")"
    fi
    if [[ -n "${phase5_hotspot_threshold}" ]]; then
      local hotspot_hint="hotspot threshold >=${phase5_hotspot_threshold}s"
      if [[ -n "${phase5_hotspot_match_count}" ]]; then
        hotspot_hint="${hotspot_hint} matched ${phase5_hotspot_match_count} test(s)"
      fi
      echo " - Phase5 strict threshold guard hit (${hotspot_hint})."
      if [[ -n "${phase5_hotspot_recommended_threshold}" ]]; then
        local hotspot_rec_hint="recommended threshold >=${phase5_hotspot_recommended_threshold}s"
        if [[ -n "${phase5_hotspot_recommended_percentile}" && -n "${phase5_hotspot_recommended_sample}" ]]; then
          local hotspot_percentile_label
          hotspot_percentile_label="$(awk -v p="${phase5_hotspot_recommended_percentile}" 'BEGIN { printf "p%.0f", (p * 100.0) }')"
          hotspot_rec_hint="${hotspot_rec_hint} from ${hotspot_percentile_label}=${phase5_hotspot_recommended_sample}s"
        fi
        echo " - Hotspot percentile recommendation: ${hotspot_rec_hint}."
        local hotspot_low_confidence=0
        if [[ "${phase5_hotspot_recommendation_low_confidence}" -eq 1 || "${phase5_hotspot_recommendation_confidence_level}" == "LOW" ]]; then
          hotspot_low_confidence=1
        fi
        if [[ "${hotspot_low_confidence}" -eq 1 ]]; then
          local hotspot_confidence_label="LOW"
          if [[ -n "${phase5_hotspot_recommendation_confidence_level}" ]]; then
            hotspot_confidence_label="${phase5_hotspot_recommendation_confidence_level}"
          fi
          local hotspot_confidence_hint=" - Hotspot recommendation confidence: ${hotspot_confidence_label}"
          if [[ -n "${phase5_hotspot_recommended_count}" && -n "${phase5_hotspot_recommend_min_sample}" ]]; then
            hotspot_confidence_hint="${hotspot_confidence_hint} (sample ${phase5_hotspot_recommended_count} < min ${phase5_hotspot_recommend_min_sample})"
          fi
          if [[ -n "${phase5_hotspot_recommendation_reason_code}" ]]; then
            hotspot_confidence_hint="${hotspot_confidence_hint} [reason=${phase5_hotspot_recommendation_reason_code}]"
          fi
          echo "${hotspot_confidence_hint}."
          local hotspot_can_recalibrate=0
          if [[ "${phase5_hotspot_recommendation_reason_code}" == "sample_below_min" ]]; then
            hotspot_can_recalibrate=1
          elif [[ -z "${phase5_hotspot_recommendation_reason_code}" && "${phase5_hotspot_recommended_count}" =~ ^[1-9][0-9]*$ && "${phase5_hotspot_recommend_min_sample}" =~ ^[1-9][0-9]*$ && "${phase5_hotspot_recommended_count}" -lt "${phase5_hotspot_recommend_min_sample}" ]]; then
            hotspot_can_recalibrate=1
          fi
          local hotspot_recalibration_target="${phase5_hotspot_recommended_min_sample}"
          if [[ -z "${hotspot_recalibration_target}" ]]; then
            hotspot_recalibration_target="${phase5_hotspot_recommended_count}"
          fi
          if [[ "${hotspot_can_recalibrate}" -eq 1 && -n "${hotspot_recalibration_target}" ]]; then
            echo " - Hotspot recalibration hint: rerun with recommend min sample <= ${hotspot_recalibration_target}."
          fi
        fi
      fi
    fi
    if [[ -n "${phase5_flaky_threshold}" ]]; then
      local flaky_hint="flaky-risk score threshold >=${phase5_flaky_threshold}"
      if [[ -n "${phase5_flaky_match_count}" ]]; then
        flaky_hint="${flaky_hint} matched ${phase5_flaky_match_count} test(s)"
      fi
      echo " - Phase5 strict threshold guard hit (${flaky_hint})."
      if [[ -n "${phase5_flaky_recommended_threshold}" ]]; then
        local flaky_rec_hint="recommended threshold >=${phase5_flaky_recommended_threshold}"
        if [[ -n "${phase5_flaky_recommended_percentile}" && -n "${phase5_flaky_recommended_sample}" ]]; then
          local flaky_percentile_label
          flaky_percentile_label="$(awk -v p="${phase5_flaky_recommended_percentile}" 'BEGIN { printf "p%.0f", (p * 100.0) }')"
          flaky_rec_hint="${flaky_rec_hint} from ${flaky_percentile_label}=${phase5_flaky_recommended_sample}"
        fi
        echo " - Flaky-risk percentile recommendation: ${flaky_rec_hint}."
        local flaky_low_confidence=0
        if [[ "${phase5_flaky_recommendation_low_confidence}" -eq 1 || "${phase5_flaky_recommendation_confidence_level}" == "LOW" ]]; then
          flaky_low_confidence=1
        fi
        if [[ "${flaky_low_confidence}" -eq 1 ]]; then
          local flaky_confidence_label="LOW"
          if [[ -n "${phase5_flaky_recommendation_confidence_level}" ]]; then
            flaky_confidence_label="${phase5_flaky_recommendation_confidence_level}"
          fi
          local flaky_confidence_hint=" - Flaky-risk recommendation confidence: ${flaky_confidence_label}"
          if [[ -n "${phase5_flaky_recommended_count}" && -n "${phase5_flaky_recommend_min_sample}" ]]; then
            flaky_confidence_hint="${flaky_confidence_hint} (sample ${phase5_flaky_recommended_count} < min ${phase5_flaky_recommend_min_sample})"
          fi
          if [[ -n "${phase5_flaky_recommendation_reason_code}" ]]; then
            flaky_confidence_hint="${flaky_confidence_hint} [reason=${phase5_flaky_recommendation_reason_code}]"
          fi
          echo "${flaky_confidence_hint}."
          local flaky_can_recalibrate=0
          if [[ "${phase5_flaky_recommendation_reason_code}" == "sample_below_min" ]]; then
            flaky_can_recalibrate=1
          elif [[ -z "${phase5_flaky_recommendation_reason_code}" && "${phase5_flaky_recommended_count}" =~ ^[1-9][0-9]*$ && "${phase5_flaky_recommend_min_sample}" =~ ^[1-9][0-9]*$ && "${phase5_flaky_recommended_count}" -lt "${phase5_flaky_recommend_min_sample}" ]]; then
            flaky_can_recalibrate=1
          fi
          local flaky_recalibration_target="${phase5_flaky_recommended_min_sample}"
          if [[ -z "${flaky_recalibration_target}" ]]; then
            flaky_recalibration_target="${phase5_flaky_recommended_count}"
          fi
          if [[ "${flaky_can_recalibrate}" -eq 1 && -n "${flaky_recalibration_target}" ]]; then
            echo " - Flaky-risk recalibration hint: rerun with recommend min sample <= ${flaky_recalibration_target}."
          fi
        fi
      fi
    fi
    if [[ -n "${strict_reasons_line}" ]]; then
      echo " - Strict failure reasons: ${strict_reasons_line}."
    else
      echo " - Strict threshold guard failure detected. Review phase5 summary artifact strict_threshold_controls."
    fi
    if [[ -n "${strict_recovery_missing_line}" && -n "${strict_recovery_unexpected_line}" ]]; then
      echo " - Strict recovery guard details: missing events=${strict_recovery_missing_line}; unexpected events=${strict_recovery_unexpected_line}."
    elif [[ -n "${strict_recovery_missing_line}" ]]; then
      echo " - Strict recovery guard details: missing events=${strict_recovery_missing_line}."
    elif [[ -n "${strict_recovery_unexpected_line}" ]]; then
      echo " - Strict recovery guard details: unexpected events=${strict_recovery_unexpected_line}."
    fi
    local hotspot_cmd=""
    local flaky_cmd=""
    local recovery_cmd=""
    local hotspot_recalibration_cmd=""
    local flaky_recalibration_cmd=""
    if [[ -n "${phase5_hotspot_threshold}" ]]; then
      local hotspot_relax_target="0"
      if [[ "${phase5_hotspot_recommended_threshold}" =~ ^[0-9]+([.][0-9]+)?$ ]]; then
        hotspot_relax_target="${phase5_hotspot_recommended_threshold}"
      elif [[ "${phase5_hotspot_threshold}" =~ ^[0-9]+([.][0-9]+)?$ ]]; then
        hotspot_relax_target="$(awk -v v="${phase5_hotspot_threshold}" 'BEGIN { printf "%.1f", (v + 2.0) }')"
      fi
      hotspot_cmd="DELIVERY_GATE_MODE=mocked DELIVERY_GATE_PHASE5_RECOVERY_GUARD_STRICT=1 DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_DURATION_SEC_THRESHOLD=${hotspot_relax_target} PW_WORKERS=${PW_WORKERS} bash scripts/phase5-phase6-delivery-gate.sh"
    fi
    if [[ -n "${phase5_flaky_threshold}" ]]; then
      local flaky_relax_target="0"
      if [[ "${phase5_flaky_recommended_threshold}" =~ ^[0-9]+$ ]]; then
        flaky_relax_target="${phase5_flaky_recommended_threshold}"
      elif [[ "${phase5_flaky_threshold}" =~ ^[0-9]+$ ]]; then
        flaky_relax_target="$((phase5_flaky_threshold + 1))"
      fi
      flaky_cmd="DELIVERY_GATE_MODE=mocked DELIVERY_GATE_PHASE5_RECOVERY_GUARD_STRICT=1 DELIVERY_GATE_PHASE5_STRICT_FLAKY_RISK_SCORE_THRESHOLD=${flaky_relax_target} PW_WORKERS=${PW_WORKERS} bash scripts/phase5-phase6-delivery-gate.sh"
    fi
    if [[ "${strict_reasons_line}" == *"recovery_guard"* ]]; then
      recovery_cmd="PHASE5_RECOVERY_GUARD_STRICT=1 PHASE5_STRICT_HOTSPOT_DURATION_SEC_THRESHOLD=${DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_DURATION_SEC_THRESHOLD} PHASE5_STRICT_FLAKY_RISK_SCORE_THRESHOLD=${DELIVERY_GATE_PHASE5_STRICT_FLAKY_RISK_SCORE_THRESHOLD} PHASE5_STRICT_HOTSPOT_RECOMMEND_PERCENTILE=${DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_RECOMMEND_PERCENTILE} PHASE5_STRICT_HOTSPOT_RECOMMEND_PADDING_SEC=${DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_RECOMMEND_PADDING_SEC} PHASE5_STRICT_HOTSPOT_RECOMMEND_MIN_SAMPLE=${DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_RECOMMEND_MIN_SAMPLE} PHASE5_STRICT_FLAKY_RISK_RECOMMEND_PERCENTILE=${DELIVERY_GATE_PHASE5_STRICT_FLAKY_RECOMMEND_PERCENTILE} PHASE5_STRICT_FLAKY_RISK_RECOMMEND_STEP=${DELIVERY_GATE_PHASE5_STRICT_FLAKY_RECOMMEND_STEP} PHASE5_STRICT_FLAKY_RISK_RECOMMEND_MIN_SAMPLE=${DELIVERY_GATE_PHASE5_STRICT_FLAKY_RECOMMEND_MIN_SAMPLE} bash scripts/phase5-regression.sh"
    fi
    local hotspot_recalibration_target="${phase5_hotspot_recommended_min_sample}"
    if [[ -z "${hotspot_recalibration_target}" ]]; then
      hotspot_recalibration_target="${phase5_hotspot_recommended_count}"
    fi
    local hotspot_allow_recalibration_cmd=0
    if [[ "${phase5_hotspot_recommendation_reason_code}" == "sample_below_min" ]]; then
      hotspot_allow_recalibration_cmd=1
    elif [[ -z "${phase5_hotspot_recommendation_reason_code}" && "${phase5_hotspot_recommended_count}" =~ ^[1-9][0-9]*$ && "${phase5_hotspot_recommend_min_sample}" =~ ^[1-9][0-9]*$ && "${phase5_hotspot_recommended_count}" -lt "${phase5_hotspot_recommend_min_sample}" ]]; then
      hotspot_allow_recalibration_cmd=1
    fi
    if [[ "${hotspot_allow_recalibration_cmd}" -eq 1 && "${hotspot_recalibration_target}" =~ ^[1-9][0-9]*$ ]]; then
      hotspot_recalibration_cmd="DELIVERY_GATE_MODE=mocked DELIVERY_GATE_PHASE5_RECOVERY_GUARD_STRICT=1 DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_DURATION_SEC_THRESHOLD=${DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_DURATION_SEC_THRESHOLD} DELIVERY_GATE_PHASE5_STRICT_FLAKY_RISK_SCORE_THRESHOLD=${DELIVERY_GATE_PHASE5_STRICT_FLAKY_RISK_SCORE_THRESHOLD} DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_RECOMMEND_PERCENTILE=${DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_RECOMMEND_PERCENTILE} DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_RECOMMEND_PADDING_SEC=${DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_RECOMMEND_PADDING_SEC} DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_RECOMMEND_MIN_SAMPLE=${hotspot_recalibration_target} DELIVERY_GATE_PHASE5_STRICT_FLAKY_RECOMMEND_PERCENTILE=${DELIVERY_GATE_PHASE5_STRICT_FLAKY_RECOMMEND_PERCENTILE} DELIVERY_GATE_PHASE5_STRICT_FLAKY_RECOMMEND_STEP=${DELIVERY_GATE_PHASE5_STRICT_FLAKY_RECOMMEND_STEP} DELIVERY_GATE_PHASE5_STRICT_FLAKY_RECOMMEND_MIN_SAMPLE=${DELIVERY_GATE_PHASE5_STRICT_FLAKY_RECOMMEND_MIN_SAMPLE} PW_WORKERS=${PW_WORKERS} bash scripts/phase5-phase6-delivery-gate.sh"
    fi
    local flaky_recalibration_target="${phase5_flaky_recommended_min_sample}"
    if [[ -z "${flaky_recalibration_target}" ]]; then
      flaky_recalibration_target="${phase5_flaky_recommended_count}"
    fi
    local flaky_allow_recalibration_cmd=0
    if [[ "${phase5_flaky_recommendation_reason_code}" == "sample_below_min" ]]; then
      flaky_allow_recalibration_cmd=1
    elif [[ -z "${phase5_flaky_recommendation_reason_code}" && "${phase5_flaky_recommended_count}" =~ ^[1-9][0-9]*$ && "${phase5_flaky_recommend_min_sample}" =~ ^[1-9][0-9]*$ && "${phase5_flaky_recommended_count}" -lt "${phase5_flaky_recommend_min_sample}" ]]; then
      flaky_allow_recalibration_cmd=1
    fi
    if [[ "${flaky_allow_recalibration_cmd}" -eq 1 && "${flaky_recalibration_target}" =~ ^[1-9][0-9]*$ ]]; then
      flaky_recalibration_cmd="DELIVERY_GATE_MODE=mocked DELIVERY_GATE_PHASE5_RECOVERY_GUARD_STRICT=1 DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_DURATION_SEC_THRESHOLD=${DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_DURATION_SEC_THRESHOLD} DELIVERY_GATE_PHASE5_STRICT_FLAKY_RISK_SCORE_THRESHOLD=${DELIVERY_GATE_PHASE5_STRICT_FLAKY_RISK_SCORE_THRESHOLD} DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_RECOMMEND_PERCENTILE=${DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_RECOMMEND_PERCENTILE} DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_RECOMMEND_PADDING_SEC=${DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_RECOMMEND_PADDING_SEC} DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_RECOMMEND_MIN_SAMPLE=${DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_RECOMMEND_MIN_SAMPLE} DELIVERY_GATE_PHASE5_STRICT_FLAKY_RECOMMEND_PERCENTILE=${DELIVERY_GATE_PHASE5_STRICT_FLAKY_RECOMMEND_PERCENTILE} DELIVERY_GATE_PHASE5_STRICT_FLAKY_RECOMMEND_STEP=${DELIVERY_GATE_PHASE5_STRICT_FLAKY_RECOMMEND_STEP} DELIVERY_GATE_PHASE5_STRICT_FLAKY_RECOMMEND_MIN_SAMPLE=${flaky_recalibration_target} PW_WORKERS=${PW_WORKERS} bash scripts/phase5-phase6-delivery-gate.sh"
    fi

    local strict_suggestions=()
    local strict_reason_priority
    for strict_reason_priority in recovery_guard hotspot_threshold flaky_risk_threshold; do
      local strict_reason_seen=0
      local strict_reason_item
      for strict_reason_item in "${phase5_strict_reasons[@]}"; do
        if [[ "${strict_reason_item}" == "${strict_reason_priority}" ]]; then
          strict_reason_seen=1
          break
        fi
      done
      if [[ "${strict_reason_seen}" -eq 0 ]]; then
        continue
      fi
      case "${strict_reason_priority}" in
        recovery_guard)
          if [[ -n "${recovery_cmd}" ]]; then
            strict_suggestions+=("recovery_guard|rerun_regression|recovery_guard|${recovery_cmd}")
          fi
          ;;
        hotspot_threshold)
          if [[ -n "${hotspot_recalibration_cmd}" ]]; then
            strict_suggestions+=("hotspot_threshold|recalibrate_min_sample|hotspot|${hotspot_recalibration_cmd}")
          fi
          if [[ -n "${hotspot_cmd}" ]]; then
            strict_suggestions+=("hotspot_threshold|relax_threshold|hotspot|${hotspot_cmd}")
          fi
          ;;
        flaky_risk_threshold)
          if [[ -n "${flaky_recalibration_cmd}" ]]; then
            strict_suggestions+=("flaky_risk_threshold|recalibrate_min_sample|flaky_risk|${flaky_recalibration_cmd}")
          fi
          if [[ -n "${flaky_cmd}" ]]; then
            strict_suggestions+=("flaky_risk_threshold|relax_threshold|flaky_risk|${flaky_cmd}")
          fi
          ;;
      esac
    done

    if [[ -n "${recovery_cmd}" ]]; then
      strict_suggestions+=("fallback|rerun_regression|recovery_guard|${recovery_cmd}")
    fi
    if [[ -n "${hotspot_cmd}" ]]; then
      strict_suggestions+=("fallback|relax_threshold|hotspot|${hotspot_cmd}")
    fi
    if [[ -n "${flaky_cmd}" ]]; then
      strict_suggestions+=("fallback|relax_threshold|flaky_risk|${flaky_cmd}")
    fi
    if [[ -n "${hotspot_recalibration_cmd}" ]]; then
      strict_suggestions+=("fallback|recalibrate_min_sample|hotspot|${hotspot_recalibration_cmd}")
    fi
    if [[ -n "${flaky_recalibration_cmd}" ]]; then
      strict_suggestions+=("fallback|recalibrate_min_sample|flaky_risk|${flaky_recalibration_cmd}")
    fi

    if [[ "${#strict_suggestions[@]}" -gt 0 ]]; then
      echo " - Suggested commands (priority order):"
      local printed_suggestions=()
      local strict_records_file=""
      strict_records_file="$(mktemp "/tmp/gate-strict-suggestions.XXXXXX")"
      local suggestion_idx=1
      local suggestion_line
      for suggestion_line in "${strict_suggestions[@]}"; do
        local suggestion_reason=""
        local suggestion_action=""
        local suggestion_scope=""
        local suggestion_command=""
        IFS='|' read -r suggestion_reason suggestion_action suggestion_scope suggestion_command <<< "${suggestion_line}"
        [[ -z "${suggestion_command}" ]] && continue
        local already_printed=0
        local existing_suggestion
        for existing_suggestion in "${printed_suggestions[@]}"; do
          if [[ "${existing_suggestion}" == "${suggestion_command}" ]]; then
            already_printed=1
            break
          fi
        done
        if [[ "${already_printed}" -eq 1 ]]; then
          continue
        fi
        printed_suggestions+=("${suggestion_command}")
        echo "   ${suggestion_idx}. ${suggestion_command}"
        printf '%s\t%s\t%s\t%s\t%s\n' \
          "${suggestion_idx}" \
          "${suggestion_reason}" \
          "${suggestion_action}" \
          "${suggestion_scope}" \
          "${suggestion_command}" >> "${strict_records_file}"
        suggestion_idx=$((suggestion_idx + 1))
      done
      if [[ -s "${strict_records_file}" ]]; then
        emit_strict_suggestions_artifact "${strict_records_file}" "${strict_reasons_line}" "${phase5_summary_file_for_hints}"
      fi
      rm -f "${strict_records_file}" >/dev/null 2>&1 || true
    fi
  fi
}

run_mocked_regression_stage() {
  local phase5_summary_file=""
  local phase5_rc=0
  if [[ -n "${DELIVERY_GATE_PHASE5_SUMMARY_DIR}" ]]; then
    mkdir -p "${DELIVERY_GATE_PHASE5_SUMMARY_DIR}"
    local summary_ts
    summary_ts="$(date -u +%Y%m%dT%H%M%SZ)"
    phase5_summary_file="${DELIVERY_GATE_PHASE5_SUMMARY_DIR}/phase5-regression-summary-${summary_ts}.json"
    echo "phase5_phase6_delivery_gate: mocked regression summary target => ${phase5_summary_file}"
  fi

  ECM_UI_URL="${ECM_UI_URL_MOCKED}" \
  PW_PROJECT="${PW_PROJECT}" \
  PW_WORKERS="${PW_WORKERS}" \
  PHASE5_RECOVERY_GUARD_STRICT="${DELIVERY_GATE_PHASE5_RECOVERY_GUARD_STRICT}" \
  PHASE5_STRICT_HOTSPOT_DURATION_SEC_THRESHOLD="${DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_DURATION_SEC_THRESHOLD}" \
  PHASE5_STRICT_FLAKY_RISK_SCORE_THRESHOLD="${DELIVERY_GATE_PHASE5_STRICT_FLAKY_RISK_SCORE_THRESHOLD}" \
  PHASE5_STRICT_HOTSPOT_RECOMMEND_PERCENTILE="${DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_RECOMMEND_PERCENTILE}" \
  PHASE5_STRICT_HOTSPOT_RECOMMEND_PADDING_SEC="${DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_RECOMMEND_PADDING_SEC}" \
  PHASE5_STRICT_HOTSPOT_RECOMMEND_MIN_SAMPLE="${DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_RECOMMEND_MIN_SAMPLE}" \
  PHASE5_STRICT_FLAKY_RISK_RECOMMEND_PERCENTILE="${DELIVERY_GATE_PHASE5_STRICT_FLAKY_RECOMMEND_PERCENTILE}" \
  PHASE5_STRICT_FLAKY_RISK_RECOMMEND_STEP="${DELIVERY_GATE_PHASE5_STRICT_FLAKY_RECOMMEND_STEP}" \
  PHASE5_STRICT_FLAKY_RISK_RECOMMEND_MIN_SAMPLE="${DELIVERY_GATE_PHASE5_STRICT_FLAKY_RECOMMEND_MIN_SAMPLE}" \
  PHASE5_REGRESSION_SUMMARY_JSON="${phase5_summary_file}" \
  bash scripts/phase5-regression.sh || phase5_rc=$?

  if [[ -n "${phase5_summary_file}" ]]; then
    if [[ -f "${phase5_summary_file}" ]]; then
      echo "phase5_phase6_delivery_gate: mocked regression summary generated => ${phase5_summary_file}"
    else
      echo "phase5_phase6_delivery_gate: WARN mocked regression summary not found => ${phase5_summary_file}"
    fi
  fi
  return "${phase5_rc}"
}

run_mocked_recovery_registry_preflight_stage() {
  if [[ "${DELIVERY_GATE_RECOVERY_REGISTRY_SYNC}" == "1" && "${DELIVERY_GATE_RECOVERY_REGISTRY_VERIFY_IDEMPOTENT}" == "1" ]]; then
    PHASE5_RECOVERY_EVENTS_FILE="${PHASE5_RECOVERY_EVENTS_FILE:-ecm-frontend/e2e/recovery-events.expected.txt}" \
    PHASE5_RECOVERY_REGISTRY_STRICT="${DELIVERY_GATE_RECOVERY_REGISTRY_STRICT}" \
    PHASE5_SYNC_VERIFY_IDEMPOTENT=1 \
    scripts/phase5-sync-recovery-registry.sh
    return
  fi
  ECM_UI_URL="${ECM_UI_URL_MOCKED}" \
  PW_PROJECT="${PW_PROJECT}" \
  PW_WORKERS="${PW_WORKERS}" \
  PHASE5_RECOVERY_GUARD_STRICT="${DELIVERY_GATE_PHASE5_RECOVERY_GUARD_STRICT}" \
  PHASE5_STRICT_HOTSPOT_DURATION_SEC_THRESHOLD="${DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_DURATION_SEC_THRESHOLD}" \
  PHASE5_STRICT_FLAKY_RISK_SCORE_THRESHOLD="${DELIVERY_GATE_PHASE5_STRICT_FLAKY_RISK_SCORE_THRESHOLD}" \
  PHASE5_STRICT_HOTSPOT_RECOMMEND_PERCENTILE="${DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_RECOMMEND_PERCENTILE}" \
  PHASE5_STRICT_HOTSPOT_RECOMMEND_PADDING_SEC="${DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_RECOMMEND_PADDING_SEC}" \
  PHASE5_STRICT_HOTSPOT_RECOMMEND_MIN_SAMPLE="${DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_RECOMMEND_MIN_SAMPLE}" \
  PHASE5_STRICT_FLAKY_RISK_RECOMMEND_PERCENTILE="${DELIVERY_GATE_PHASE5_STRICT_FLAKY_RECOMMEND_PERCENTILE}" \
  PHASE5_STRICT_FLAKY_RISK_RECOMMEND_STEP="${DELIVERY_GATE_PHASE5_STRICT_FLAKY_RECOMMEND_STEP}" \
  PHASE5_STRICT_FLAKY_RISK_RECOMMEND_MIN_SAMPLE="${DELIVERY_GATE_PHASE5_STRICT_FLAKY_RECOMMEND_MIN_SAMPLE}" \
  PHASE5_RECOVERY_REGISTRY_SYNC="${DELIVERY_GATE_RECOVERY_REGISTRY_SYNC}" \
  PHASE5_RECOVERY_REGISTRY_STRICT="${DELIVERY_GATE_RECOVERY_REGISTRY_STRICT}" \
  PHASE5_VALIDATE_RECOVERY_REGISTRY_ONLY=1 \
  bash scripts/phase5-regression.sh
}

run_fullstack_target_preflight_stage() {
  ALLOW_STATIC=0 scripts/check-e2e-target.sh "${ECM_UI_URL_FULLSTACK}"
}

run_prebuilt_sync_stage() {
  ECM_SYNC_PREBUILT_UI="${ECM_SYNC_PREBUILT_UI}" \
  bash scripts/sync-prebuilt-frontend-if-needed.sh "${ECM_UI_URL_FULLSTACK}"
}

run_integration_dependency_preflight_stage() {
  local dependency_failures=()
  local hints=()

  check_dependency() {
    local label="$1"
    local url="$2"
    local hint="$3"
    if curl -fsS --max-time 5 "${url}" >/dev/null 2>&1; then
      return 0
    fi
    dependency_failures+=("${label}|${url}|${hint}")
    hints+=("${hint}")
    return 1
  }

  check_dependency "backend health" "${ECM_API_URL}/actuator/health" \
    "Start ecm-core API (or set ECM_API_URL to a reachable backend)." || true
  check_dependency "keycloak discovery" "${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}/.well-known/openid-configuration" \
    "Start Keycloak realm '${KEYCLOAK_REALM}' (or set KEYCLOAK_URL/KEYCLOAK_REALM)." || true
  check_dependency "ui reachability" "${ECM_UI_URL_FULLSTACK}" \
    "Start frontend UI target (or set ECM_UI_URL_FULLSTACK)." || true

  if [[ "${#dependency_failures[@]}" -eq 0 ]]; then
    echo "phase5_phase6_delivery_gate: integration dependency preflight ok"
    return 0
  fi

  echo "phase5_phase6_delivery_gate: integration dependency preflight failed"
  local failure
  for failure in "${dependency_failures[@]}"; do
    local label
    local url
    local hint
    IFS='|' read -r label url hint <<< "${failure}"
    echo " - ${label}: ${url}"
  done

  echo "phase5_phase6_delivery_gate: remediation hints"
  local unique_hints=()
  local hint
  for hint in "${hints[@]}"; do
    local seen=0
    local existing
    for existing in "${unique_hints[@]}"; do
      if [[ "${existing}" == "${hint}" ]]; then
        seen=1
        break
      fi
    done
    if [[ "${seen}" -eq 0 ]]; then
      unique_hints+=("${hint}")
    fi
  done

  local item
  for item in "${unique_hints[@]}"; do
    echo " - ${item}"
  done
  return 1
}

run_fullstack_admin_smoke_stage() {
  ECM_UI_URL="${ECM_UI_URL_FULLSTACK}" \
  ECM_API_URL="${ECM_API_URL}" \
  KEYCLOAK_URL="${KEYCLOAK_URL}" \
  KEYCLOAK_REALM="${KEYCLOAK_REALM}" \
  ECM_E2E_USERNAME="${ECM_E2E_USERNAME}" \
  ECM_E2E_PASSWORD="${ECM_E2E_PASSWORD}" \
  PW_PROJECT="${PW_PROJECT}" \
  PW_WORKERS="${PW_WORKERS}" \
  FULLSTACK_ALLOW_STATIC="${ECM_FULLSTACK_ALLOW_STATIC}" \
  ECM_SYNC_PREBUILT_UI=0 \
  bash scripts/phase5-fullstack-smoke.sh
}

run_phase6_mail_integration_stage() {
  ECM_UI_URL="${ECM_UI_URL_FULLSTACK}" \
  ECM_API_URL="${ECM_API_URL}" \
  KEYCLOAK_URL="${KEYCLOAK_URL}" \
  KEYCLOAK_REALM="${KEYCLOAK_REALM}" \
  ECM_E2E_USERNAME="${ECM_E2E_USERNAME}" \
  ECM_E2E_PASSWORD="${ECM_E2E_PASSWORD}" \
  PW_PROJECT="${PW_PROJECT}" \
  PW_WORKERS="${PW_WORKERS}" \
  FULLSTACK_ALLOW_STATIC="${ECM_FULLSTACK_ALLOW_STATIC}" \
  ECM_SYNC_PREBUILT_UI=0 \
  bash scripts/phase6-mail-automation-integration-smoke.sh
}

run_phase5_search_integration_stage() {
  ECM_UI_URL="${ECM_UI_URL_FULLSTACK}" \
  ECM_API_URL="${ECM_API_URL}" \
  KEYCLOAK_URL="${KEYCLOAK_URL}" \
  KEYCLOAK_REALM="${KEYCLOAK_REALM}" \
  ECM_E2E_USERNAME="${ECM_E2E_USERNAME}" \
  ECM_E2E_PASSWORD="${ECM_E2E_PASSWORD}" \
  PW_PROJECT="${PW_PROJECT}" \
  PW_WORKERS="${PW_WORKERS}" \
  FULLSTACK_ALLOW_STATIC="${ECM_FULLSTACK_ALLOW_STATIC}" \
  ECM_SYNC_PREBUILT_UI=0 \
  bash scripts/phase5-search-suggestions-integration-smoke.sh
}

run_phase70_auth_route_matrix_stage() {
  ECM_UI_URL="${ECM_UI_URL_FULLSTACK}" \
  ECM_API_URL="${ECM_API_URL}" \
  KEYCLOAK_URL="${KEYCLOAK_URL}" \
  KEYCLOAK_REALM="${KEYCLOAK_REALM}" \
  PW_PROJECT="${PW_PROJECT}" \
  PW_WORKERS="${PW_WORKERS}" \
  FULLSTACK_ALLOW_STATIC="${ECM_FULLSTACK_ALLOW_STATIC}" \
  ECM_SYNC_PREBUILT_UI=0 \
  bash scripts/phase70-auth-route-matrix-smoke.sh
}

run_p1_smoke_stage() {
  echo "phase5_phase6_delivery_gate: check p1 e2e target"
  ALLOW_STATIC="${ECM_FULLSTACK_ALLOW_STATIC}" scripts/check-e2e-target.sh "${ECM_UI_URL_FULLSTACK}"
  (
    cd ecm-frontend
    ECM_UI_URL="${ECM_UI_URL_FULLSTACK}" \
    ECM_API_URL="${ECM_API_URL}" \
    KEYCLOAK_URL="${KEYCLOAK_URL}" \
    KEYCLOAK_REALM="${KEYCLOAK_REALM}" \
    ECM_E2E_USERNAME="${ECM_E2E_USERNAME}" \
    ECM_E2E_PASSWORD="${ECM_E2E_PASSWORD}" \
    npx playwright test e2e/p1-smoke.spec.ts \
      --project="${PW_PROJECT}" --workers="${PW_WORKERS}"
  )
}

for arg in "$@"; do
  case "${arg}" in
    --help|-h)
      print_usage
      exit 0
      ;;
    --plan)
      DELIVERY_GATE_PLAN_ONLY="1"
      ;;
    --no-plan)
      DELIVERY_GATE_PRINT_EXECUTION_PLAN="0"
      ;;
    --print-plan)
      DELIVERY_GATE_PRINT_EXECUTION_PLAN="1"
      ;;
    --plan-format=*)
      DELIVERY_GATE_EXECUTION_PLAN_FORMAT="${arg#--plan-format=}"
      ;;
    --plan-json)
      DELIVERY_GATE_EXECUTION_PLAN_FORMAT="json"
      ;;
    --plan-text)
      DELIVERY_GATE_EXECUTION_PLAN_FORMAT="text"
      ;;
    --plan-file=*)
      DELIVERY_GATE_EXECUTION_PLAN_FILE="${arg#--plan-file=}"
      ;;
    --print-strict-suggestions-json)
      DELIVERY_GATE_PRINT_STRICT_SUGGESTIONS_JSON="1"
      ;;
    --no-print-strict-suggestions-json)
      DELIVERY_GATE_PRINT_STRICT_SUGGESTIONS_JSON="0"
      ;;
    --strict-suggestions-file=*)
      DELIVERY_GATE_STRICT_SUGGESTIONS_FILE="${arg#--strict-suggestions-file=}"
      ;;
    --phase5-summary-dir=*)
      DELIVERY_GATE_PHASE5_SUMMARY_DIR="${arg#--phase5-summary-dir=}"
      ;;
    --phase5-strict-recovery-guard=*)
      DELIVERY_GATE_PHASE5_RECOVERY_GUARD_STRICT="${arg#--phase5-strict-recovery-guard=}"
      DELIVERY_GATE_PHASE5_RECOVERY_GUARD_STRICT_SOURCE="cli"
      ;;
    --phase5-strict-hotspot-sec=*)
      DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_DURATION_SEC_THRESHOLD="${arg#--phase5-strict-hotspot-sec=}"
      DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_DURATION_SEC_THRESHOLD_SOURCE="cli"
      ;;
    --phase5-strict-flaky-score=*)
      DELIVERY_GATE_PHASE5_STRICT_FLAKY_RISK_SCORE_THRESHOLD="${arg#--phase5-strict-flaky-score=}"
      DELIVERY_GATE_PHASE5_STRICT_FLAKY_RISK_SCORE_THRESHOLD_SOURCE="cli"
      ;;
    --phase5-hotspot-recommend-percentile=*)
      DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_RECOMMEND_PERCENTILE="${arg#--phase5-hotspot-recommend-percentile=}"
      DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_RECOMMEND_PERCENTILE_SOURCE="cli"
      ;;
    --phase5-hotspot-recommend-padding-sec=*)
      DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_RECOMMEND_PADDING_SEC="${arg#--phase5-hotspot-recommend-padding-sec=}"
      DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_RECOMMEND_PADDING_SEC_SOURCE="cli"
      ;;
    --phase5-hotspot-recommend-min-sample=*)
      DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_RECOMMEND_MIN_SAMPLE="${arg#--phase5-hotspot-recommend-min-sample=}"
      DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_RECOMMEND_MIN_SAMPLE_SOURCE="cli"
      ;;
    --phase5-flaky-recommend-percentile=*)
      DELIVERY_GATE_PHASE5_STRICT_FLAKY_RECOMMEND_PERCENTILE="${arg#--phase5-flaky-recommend-percentile=}"
      DELIVERY_GATE_PHASE5_STRICT_FLAKY_RECOMMEND_PERCENTILE_SOURCE="cli"
      ;;
    --phase5-flaky-recommend-step=*)
      DELIVERY_GATE_PHASE5_STRICT_FLAKY_RECOMMEND_STEP="${arg#--phase5-flaky-recommend-step=}"
      DELIVERY_GATE_PHASE5_STRICT_FLAKY_RECOMMEND_STEP_SOURCE="cli"
      ;;
    --phase5-flaky-recommend-min-sample=*)
      DELIVERY_GATE_PHASE5_STRICT_FLAKY_RECOMMEND_MIN_SAMPLE="${arg#--phase5-flaky-recommend-min-sample=}"
      DELIVERY_GATE_PHASE5_STRICT_FLAKY_RECOMMEND_MIN_SAMPLE_SOURCE="cli"
      ;;
    --mode=*)
      DELIVERY_GATE_MODE="${arg#--mode=}"
      ;;
    all|mocked|integration|preflight|integration-preflight)
      DELIVERY_GATE_MODE="${arg}"
      ;;
    *)
      echo "error: unsupported argument '${arg}'"
      print_usage
      exit 1
      ;;
  esac
done

ECM_UI_URL_FULLSTACK="$(resolve_fullstack_ui_url)"

echo "phase5_phase6_delivery_gate: start"
echo "ECM_UI_URL_MOCKED=${ECM_UI_URL_MOCKED}"
echo "ECM_UI_URL_FULLSTACK=${ECM_UI_URL_FULLSTACK}"
echo "ECM_API_URL=${ECM_API_URL}"
echo "KEYCLOAK_URL=${KEYCLOAK_URL} KEYCLOAK_REALM=${KEYCLOAK_REALM}"
echo "PW_PROJECT=${PW_PROJECT} PW_WORKERS=${PW_WORKERS}"
echo "ECM_FULLSTACK_ALLOW_STATIC=${ECM_FULLSTACK_ALLOW_STATIC}"
echo "ECM_SYNC_PREBUILT_UI=${ECM_SYNC_PREBUILT_UI}"
echo "DELIVERY_GATE_MODE=${DELIVERY_GATE_MODE}"
echo "DELIVERY_GATE_RECOVERY_REGISTRY_SYNC=${DELIVERY_GATE_RECOVERY_REGISTRY_SYNC}"
echo "DELIVERY_GATE_RECOVERY_REGISTRY_SYNC_SOURCE=${DELIVERY_GATE_RECOVERY_REGISTRY_SYNC_SOURCE}"
echo "DELIVERY_GATE_RECOVERY_REGISTRY_STRICT=${DELIVERY_GATE_RECOVERY_REGISTRY_STRICT}"
echo "DELIVERY_GATE_RECOVERY_REGISTRY_STRICT_SOURCE=${DELIVERY_GATE_RECOVERY_REGISTRY_STRICT_SOURCE}"
echo "DELIVERY_GATE_RECOVERY_REGISTRY_VERIFY_IDEMPOTENT=${DELIVERY_GATE_RECOVERY_REGISTRY_VERIFY_IDEMPOTENT}"
echo "DELIVERY_GATE_RECOVERY_REGISTRY_VERIFY_IDEMPOTENT_SOURCE=${DELIVERY_GATE_RECOVERY_REGISTRY_VERIFY_IDEMPOTENT_SOURCE}"
echo "DELIVERY_GATE_PRINT_EXECUTION_PLAN=${DELIVERY_GATE_PRINT_EXECUTION_PLAN}"
echo "DELIVERY_GATE_EXECUTION_PLAN_FORMAT=${DELIVERY_GATE_EXECUTION_PLAN_FORMAT}"
echo "DELIVERY_GATE_EXECUTION_PLAN_FILE=${DELIVERY_GATE_EXECUTION_PLAN_FILE:-<none>}"
echo "DELIVERY_GATE_PRINT_STRICT_SUGGESTIONS_JSON=${DELIVERY_GATE_PRINT_STRICT_SUGGESTIONS_JSON}"
echo "DELIVERY_GATE_STRICT_SUGGESTIONS_FILE=${DELIVERY_GATE_STRICT_SUGGESTIONS_FILE:-<none>}"
echo "DELIVERY_GATE_PHASE5_SUMMARY_DIR=${DELIVERY_GATE_PHASE5_SUMMARY_DIR:-<none>}"
echo "DELIVERY_GATE_PHASE5_RECOVERY_GUARD_STRICT=${DELIVERY_GATE_PHASE5_RECOVERY_GUARD_STRICT}"
echo "DELIVERY_GATE_PHASE5_RECOVERY_GUARD_STRICT_SOURCE=${DELIVERY_GATE_PHASE5_RECOVERY_GUARD_STRICT_SOURCE}"
echo "DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_DURATION_SEC_THRESHOLD=${DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_DURATION_SEC_THRESHOLD}"
echo "DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_DURATION_SEC_THRESHOLD_SOURCE=${DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_DURATION_SEC_THRESHOLD_SOURCE}"
echo "DELIVERY_GATE_PHASE5_STRICT_FLAKY_RISK_SCORE_THRESHOLD=${DELIVERY_GATE_PHASE5_STRICT_FLAKY_RISK_SCORE_THRESHOLD}"
echo "DELIVERY_GATE_PHASE5_STRICT_FLAKY_RISK_SCORE_THRESHOLD_SOURCE=${DELIVERY_GATE_PHASE5_STRICT_FLAKY_RISK_SCORE_THRESHOLD_SOURCE}"
echo "DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_RECOMMEND_PERCENTILE=${DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_RECOMMEND_PERCENTILE}"
echo "DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_RECOMMEND_PERCENTILE_SOURCE=${DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_RECOMMEND_PERCENTILE_SOURCE}"
echo "DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_RECOMMEND_PADDING_SEC=${DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_RECOMMEND_PADDING_SEC}"
echo "DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_RECOMMEND_PADDING_SEC_SOURCE=${DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_RECOMMEND_PADDING_SEC_SOURCE}"
echo "DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_RECOMMEND_MIN_SAMPLE=${DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_RECOMMEND_MIN_SAMPLE}"
echo "DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_RECOMMEND_MIN_SAMPLE_SOURCE=${DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_RECOMMEND_MIN_SAMPLE_SOURCE}"
echo "DELIVERY_GATE_PHASE5_STRICT_FLAKY_RECOMMEND_PERCENTILE=${DELIVERY_GATE_PHASE5_STRICT_FLAKY_RECOMMEND_PERCENTILE}"
echo "DELIVERY_GATE_PHASE5_STRICT_FLAKY_RECOMMEND_PERCENTILE_SOURCE=${DELIVERY_GATE_PHASE5_STRICT_FLAKY_RECOMMEND_PERCENTILE_SOURCE}"
echo "DELIVERY_GATE_PHASE5_STRICT_FLAKY_RECOMMEND_STEP=${DELIVERY_GATE_PHASE5_STRICT_FLAKY_RECOMMEND_STEP}"
echo "DELIVERY_GATE_PHASE5_STRICT_FLAKY_RECOMMEND_STEP_SOURCE=${DELIVERY_GATE_PHASE5_STRICT_FLAKY_RECOMMEND_STEP_SOURCE}"
echo "DELIVERY_GATE_PHASE5_STRICT_FLAKY_RECOMMEND_MIN_SAMPLE=${DELIVERY_GATE_PHASE5_STRICT_FLAKY_RECOMMEND_MIN_SAMPLE}"
echo "DELIVERY_GATE_PHASE5_STRICT_FLAKY_RECOMMEND_MIN_SAMPLE_SOURCE=${DELIVERY_GATE_PHASE5_STRICT_FLAKY_RECOMMEND_MIN_SAMPLE_SOURCE}"
echo "DELIVERY_GATE_PLAN_ONLY=${DELIVERY_GATE_PLAN_ONLY}"
if [[ -z "${ECM_UI_URL_FULLSTACK_INPUT}" ]]; then
  echo "ECM_UI_URL_FULLSTACK auto-detected (set ECM_UI_URL_FULLSTACK to override)"
fi

case "${DELIVERY_GATE_MODE}" in
  all|mocked|integration|preflight|integration-preflight)
    ;;
  *)
    echo "error: unsupported DELIVERY_GATE_MODE=${DELIVERY_GATE_MODE} (expected: all|mocked|integration|preflight|integration-preflight)"
    exit 1
    ;;
esac

case "${DELIVERY_GATE_EXECUTION_PLAN_FORMAT}" in
  text|json)
    ;;
  *)
    echo "error: unsupported DELIVERY_GATE_EXECUTION_PLAN_FORMAT=${DELIVERY_GATE_EXECUTION_PLAN_FORMAT} (expected: text|json)"
    exit 1
    ;;
esac
case "${DELIVERY_GATE_PRINT_STRICT_SUGGESTIONS_JSON}" in
  0|1)
    ;;
  *)
    echo "error: unsupported DELIVERY_GATE_PRINT_STRICT_SUGGESTIONS_JSON=${DELIVERY_GATE_PRINT_STRICT_SUGGESTIONS_JSON} (expected: 0|1)"
    exit 1
    ;;
esac
case "${DELIVERY_GATE_PHASE5_RECOVERY_GUARD_STRICT}" in
  0|1)
    ;;
  *)
    echo "error: unsupported DELIVERY_GATE_PHASE5_RECOVERY_GUARD_STRICT=${DELIVERY_GATE_PHASE5_RECOVERY_GUARD_STRICT} (expected: 0|1)"
    exit 1
    ;;
esac
if ! [[ "${DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_DURATION_SEC_THRESHOLD}" =~ ^[0-9]+([.][0-9]+)?$ ]]; then
  echo "error: unsupported DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_DURATION_SEC_THRESHOLD=${DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_DURATION_SEC_THRESHOLD} (expected non-negative number)"
  exit 1
fi
if ! [[ "${DELIVERY_GATE_PHASE5_STRICT_FLAKY_RISK_SCORE_THRESHOLD}" =~ ^[0-9]+$ ]]; then
  echo "error: unsupported DELIVERY_GATE_PHASE5_STRICT_FLAKY_RISK_SCORE_THRESHOLD=${DELIVERY_GATE_PHASE5_STRICT_FLAKY_RISK_SCORE_THRESHOLD} (expected non-negative integer)"
  exit 1
fi
if ! [[ "${DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_RECOMMEND_PERCENTILE}" =~ ^[0-9]+([.][0-9]+)?$ ]]; then
  echo "error: unsupported DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_RECOMMEND_PERCENTILE=${DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_RECOMMEND_PERCENTILE} (expected number in (0,1])"
  exit 1
fi
if ! awk -v v="${DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_RECOMMEND_PERCENTILE}" 'BEGIN { exit !(v > 0 && v <= 1) }'; then
  echo "error: unsupported DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_RECOMMEND_PERCENTILE=${DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_RECOMMEND_PERCENTILE} (expected number in (0,1])"
  exit 1
fi
if ! [[ "${DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_RECOMMEND_PADDING_SEC}" =~ ^[0-9]+([.][0-9]+)?$ ]]; then
  echo "error: unsupported DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_RECOMMEND_PADDING_SEC=${DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_RECOMMEND_PADDING_SEC} (expected non-negative number)"
  exit 1
fi
if ! [[ "${DELIVERY_GATE_PHASE5_STRICT_FLAKY_RECOMMEND_PERCENTILE}" =~ ^[0-9]+([.][0-9]+)?$ ]]; then
  echo "error: unsupported DELIVERY_GATE_PHASE5_STRICT_FLAKY_RECOMMEND_PERCENTILE=${DELIVERY_GATE_PHASE5_STRICT_FLAKY_RECOMMEND_PERCENTILE} (expected number in (0,1])"
  exit 1
fi
if ! awk -v v="${DELIVERY_GATE_PHASE5_STRICT_FLAKY_RECOMMEND_PERCENTILE}" 'BEGIN { exit !(v > 0 && v <= 1) }'; then
  echo "error: unsupported DELIVERY_GATE_PHASE5_STRICT_FLAKY_RECOMMEND_PERCENTILE=${DELIVERY_GATE_PHASE5_STRICT_FLAKY_RECOMMEND_PERCENTILE} (expected number in (0,1])"
  exit 1
fi
if ! [[ "${DELIVERY_GATE_PHASE5_STRICT_FLAKY_RECOMMEND_STEP}" =~ ^[0-9]+$ ]]; then
  echo "error: unsupported DELIVERY_GATE_PHASE5_STRICT_FLAKY_RECOMMEND_STEP=${DELIVERY_GATE_PHASE5_STRICT_FLAKY_RECOMMEND_STEP} (expected non-negative integer)"
  exit 1
fi
if ! [[ "${DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_RECOMMEND_MIN_SAMPLE}" =~ ^[1-9][0-9]*$ ]]; then
  echo "error: unsupported DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_RECOMMEND_MIN_SAMPLE=${DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_RECOMMEND_MIN_SAMPLE} (expected positive integer)"
  exit 1
fi
if ! [[ "${DELIVERY_GATE_PHASE5_STRICT_FLAKY_RECOMMEND_MIN_SAMPLE}" =~ ^[1-9][0-9]*$ ]]; then
  echo "error: unsupported DELIVERY_GATE_PHASE5_STRICT_FLAKY_RECOMMEND_MIN_SAMPLE=${DELIVERY_GATE_PHASE5_STRICT_FLAKY_RECOMMEND_MIN_SAMPLE} (expected positive integer)"
  exit 1
fi

if [[ -n "${DELIVERY_GATE_EXECUTION_PLAN_FILE}" && -d "${DELIVERY_GATE_EXECUTION_PLAN_FILE}" ]]; then
  echo "error: DELIVERY_GATE_EXECUTION_PLAN_FILE points to a directory: ${DELIVERY_GATE_EXECUTION_PLAN_FILE}"
  exit 1
fi
if [[ -n "${DELIVERY_GATE_STRICT_SUGGESTIONS_FILE}" && -d "${DELIVERY_GATE_STRICT_SUGGESTIONS_FILE}" ]]; then
  echo "error: DELIVERY_GATE_STRICT_SUGGESTIONS_FILE points to a directory: ${DELIVERY_GATE_STRICT_SUGGESTIONS_FILE}"
  exit 1
fi
if [[ -n "${DELIVERY_GATE_PHASE5_SUMMARY_DIR}" && -f "${DELIVERY_GATE_PHASE5_SUMMARY_DIR}" ]]; then
  echo "error: DELIVERY_GATE_PHASE5_SUMMARY_DIR points to a file: ${DELIVERY_GATE_PHASE5_SUMMARY_DIR}"
  exit 1
fi

if [[ "${DELIVERY_GATE_PRINT_EXECUTION_PLAN}" == "1" || "${DELIVERY_GATE_PLAN_ONLY}" == "1" || -n "${DELIVERY_GATE_EXECUTION_PLAN_FILE}" ]]; then
  execution_plan_payload="$(build_execution_plan_payload)"
  if [[ "${DELIVERY_GATE_PRINT_EXECUTION_PLAN}" == "1" || "${DELIVERY_GATE_PLAN_ONLY}" == "1" ]]; then
    printf '%s\n' "${execution_plan_payload}"
  fi
  write_execution_plan_file "${execution_plan_payload}"
fi
if [[ "${DELIVERY_GATE_PLAN_ONLY}" == "1" ]]; then
  echo ""
  echo "phase5_phase6_delivery_gate: plan-only mode complete"
  exit 0
fi

overall_rc=0
fast_layer_failed=0
integration_layer_failed=0

if [[ "${DELIVERY_GATE_MODE}" == "all" || "${DELIVERY_GATE_MODE}" == "mocked" || "${DELIVERY_GATE_MODE}" == "preflight" || "${DELIVERY_GATE_MODE}" == "integration-preflight" ]]; then
  echo ""
  echo "=== Layer 1/2: fast mocked regression ==="
  fast_prereq_ok=1
  if ! run_stage "fast" "mocked_registry_preflight" "mocked recovery registry preflight" run_mocked_recovery_registry_preflight_stage; then
    fast_layer_failed=1
    overall_rc=1
    fast_prereq_ok=0
    echo "phase5_phase6_delivery_gate: mocked regression stage skipped (registry preflight failed)"
  fi
  if [[ "${DELIVERY_GATE_MODE}" == "preflight" || "${DELIVERY_GATE_MODE}" == "integration-preflight" ]]; then
    if [[ "${fast_prereq_ok}" -eq 1 ]]; then
      echo "phase5_phase6_delivery_gate: mocked regression stage skipped (${DELIVERY_GATE_MODE} mode)"
    fi
  elif [[ "${fast_prereq_ok}" -eq 1 ]] && ! run_stage "fast" "mocked_regression" "mocked regression gate" run_mocked_regression_stage; then
    fast_layer_failed=1
    overall_rc=1
  fi
fi

run_integration_layer=0
if [[ "${DELIVERY_GATE_MODE}" == "integration" ]]; then
  run_integration_layer=1
elif [[ "${DELIVERY_GATE_MODE}" == "integration-preflight" && "${fast_layer_failed}" -eq 0 ]]; then
  run_integration_layer=1
elif [[ "${DELIVERY_GATE_MODE}" == "integration-preflight" ]]; then
  echo ""
  echo "phase5_phase6_delivery_gate: integration layer skipped (fast preflight layer failed)"
elif [[ "${DELIVERY_GATE_MODE}" == "all" && "${fast_layer_failed}" -eq 0 ]]; then
  run_integration_layer=1
elif [[ "${DELIVERY_GATE_MODE}" == "all" ]]; then
  echo ""
  echo "phase5_phase6_delivery_gate: integration layer skipped (fast mocked layer failed)"
fi

if [[ "${run_integration_layer}" -eq 1 ]]; then
  echo ""
  echo "=== Layer 2/2: integration + full-stack smokes ==="
  integration_prereq_ok=1

  if ! run_stage \
    "integration" \
    "integration_dependency_preflight" \
    "integration dependency preflight" \
    run_integration_dependency_preflight_stage; then
    integration_layer_failed=1
    overall_rc=1
    integration_prereq_ok=0
    echo "phase5_phase6_delivery_gate: integration stages skipped (dependency preflight failed)"
  fi

  if [[ "${integration_prereq_ok}" -eq 1 && "${ECM_FULLSTACK_ALLOW_STATIC}" == "0" ]]; then
    if ! run_stage \
      "integration" \
      "strict_fullstack_preflight" \
      "strict fullstack target preflight" \
      run_fullstack_target_preflight_stage; then
      integration_layer_failed=1
      overall_rc=1
      integration_prereq_ok=0
      echo "phase5_phase6_delivery_gate: integration stages skipped (strict preflight failed)"
    fi
  fi

  if [[ "${integration_prereq_ok}" -eq 1 ]]; then
    if ! run_stage \
      "integration" \
      "fullstack_prebuilt_sync" \
      "full-stack prebuilt sync check" \
      run_prebuilt_sync_stage; then
      integration_layer_failed=1
      overall_rc=1
    fi

    if ! run_stage \
      "integration" \
      "fullstack_admin_smoke" \
      "full-stack admin smoke" \
      run_fullstack_admin_smoke_stage; then
      integration_layer_failed=1
      overall_rc=1
    fi

    if ! run_stage \
      "integration" \
      "phase6_mail_integration_smoke" \
      "phase6 mail integration smoke" \
      run_phase6_mail_integration_stage; then
      integration_layer_failed=1
      overall_rc=1
    fi

    if ! run_stage \
      "integration" \
      "phase5_search_integration_smoke" \
      "phase5 search suggestions integration smoke" \
      run_phase5_search_integration_stage; then
      integration_layer_failed=1
      overall_rc=1
    fi

    if ! run_stage \
      "integration" \
      "phase70_auth_route_matrix_smoke" \
      "phase70 auth-route matrix smoke" \
      run_phase70_auth_route_matrix_stage; then
      integration_layer_failed=1
      overall_rc=1
    fi

    if ! run_stage \
      "integration" \
      "p1_smoke" \
      "p1 smoke" \
      run_p1_smoke_stage; then
      integration_layer_failed=1
      overall_rc=1
    fi
  fi
fi

print_gate_summary

if [[ "${overall_rc}" -ne 0 ]]; then
  print_startup_failure_hints
  if [[ "${fast_layer_failed}" -ne 0 ]]; then
    echo "phase5_phase6_delivery_gate: failed in fast mocked layer"
  elif [[ "${integration_layer_failed}" -ne 0 ]]; then
    echo "phase5_phase6_delivery_gate: failed in integration/full-stack layer"
  else
    echo "phase5_phase6_delivery_gate: failed"
  fi
  exit "${overall_rc}"
fi

echo ""
echo "phase5_phase6_delivery_gate: ok"
