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
  scripts/phase5-phase6-delivery-gate.sh --mode=<mode> [--plan] [--plan-format=<text|json>] [--plan-file=<path>] [--phase5-summary-dir=<path>] [--phase5-strict-recovery-guard=<0|1>] [--phase5-strict-hotspot-sec=<num>] [--phase5-strict-flaky-score=<int>]
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
  --phase5-summary-dir=<path>
                        Write mocked phase5 regression summary JSON artifacts to directory.
  --phase5-strict-recovery-guard=<0|1>
                        Pass strict recovery guard mode to mocked phase5 regression.
  --phase5-strict-hotspot-sec=<num>
                        Pass strict hotspot duration threshold (seconds) to mocked phase5 regression.
  --phase5-strict-flaky-score=<int>
                        Pass strict flaky-risk score threshold to mocked phase5 regression.

Environment controls:
  DELIVERY_GATE_MODE
  DELIVERY_GATE_PRINT_EXECUTION_PLAN=1|0
  DELIVERY_GATE_EXECUTION_PLAN_FORMAT=text|json
  DELIVERY_GATE_EXECUTION_PLAN_FILE=<path>
  DELIVERY_GATE_PHASE5_SUMMARY_DIR=<path>
  DELIVERY_GATE_PHASE5_RECOVERY_GUARD_STRICT=1|0
  DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_DURATION_SEC_THRESHOLD=<num>
  DELIVERY_GATE_PHASE5_STRICT_FLAKY_RISK_SCORE_THRESHOLD=<int>
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
  "mocked_regression_summary_dir": "${DELIVERY_GATE_PHASE5_SUMMARY_DIR:-"(unset)"}",
  "phase5_recovery_guard_strict": "${DELIVERY_GATE_PHASE5_RECOVERY_GUARD_STRICT}",
  "phase5_strict_hotspot_duration_sec_threshold": "${DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_DURATION_SEC_THRESHOLD}",
  "phase5_strict_flaky_risk_score_threshold": "${DELIVERY_GATE_PHASE5_STRICT_FLAKY_RISK_SCORE_THRESHOLD}",
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
 - mocked regression summary dir: ${DELIVERY_GATE_PHASE5_SUMMARY_DIR:-"(unset)"}
 - phase5 recovery guard strict: ${DELIVERY_GATE_PHASE5_RECOVERY_GUARD_STRICT}
 - phase5 strict hotspot threshold (sec): ${DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_DURATION_SEC_THRESHOLD}
 - phase5 strict flaky-risk score threshold: ${DELIVERY_GATE_PHASE5_STRICT_FLAKY_RISK_SCORE_THRESHOLD}
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
emit('hotspot_match_count', strict?.hotspot_match_count ?? 0);
emit('flaky_threshold', strict?.flaky_risk_score_threshold ?? '');
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
  local recovery_missing_events=()
  local recovery_unexpected_events=()
  local registry_missing_events=()
  local registry_stale_events=()
  local phase5_strict_reasons=()
  local phase5_hotspot_threshold=""
  local phase5_hotspot_match_count=""
  local phase5_flaky_threshold=""
  local phase5_flaky_match_count=""
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
    fi
    if [[ -n "${phase5_flaky_threshold}" ]]; then
      local flaky_hint="flaky-risk score threshold >=${phase5_flaky_threshold}"
      if [[ -n "${phase5_flaky_match_count}" ]]; then
        flaky_hint="${flaky_hint} matched ${phase5_flaky_match_count} test(s)"
      fi
      echo " - Phase5 strict threshold guard hit (${flaky_hint})."
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
    if [[ -n "${phase5_hotspot_threshold}" ]]; then
      local hotspot_relax_target="0"
      if [[ "${phase5_hotspot_threshold}" =~ ^[0-9]+([.][0-9]+)?$ ]]; then
        hotspot_relax_target="$(awk -v v="${phase5_hotspot_threshold}" 'BEGIN { printf "%.1f", (v + 2.0) }')"
      fi
      hotspot_cmd="DELIVERY_GATE_MODE=mocked DELIVERY_GATE_PHASE5_RECOVERY_GUARD_STRICT=1 DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_DURATION_SEC_THRESHOLD=${hotspot_relax_target} PW_WORKERS=${PW_WORKERS} bash scripts/phase5-phase6-delivery-gate.sh"
    fi
    if [[ -n "${phase5_flaky_threshold}" ]]; then
      local flaky_relax_target="0"
      if [[ "${phase5_flaky_threshold}" =~ ^[0-9]+$ ]]; then
        flaky_relax_target="$((phase5_flaky_threshold + 1))"
      fi
      flaky_cmd="DELIVERY_GATE_MODE=mocked DELIVERY_GATE_PHASE5_RECOVERY_GUARD_STRICT=1 DELIVERY_GATE_PHASE5_STRICT_FLAKY_RISK_SCORE_THRESHOLD=${flaky_relax_target} PW_WORKERS=${PW_WORKERS} bash scripts/phase5-phase6-delivery-gate.sh"
    fi
    if [[ "${strict_reasons_line}" == *"recovery_guard"* ]]; then
      recovery_cmd="PHASE5_RECOVERY_GUARD_STRICT=1 PHASE5_STRICT_HOTSPOT_DURATION_SEC_THRESHOLD=${DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_DURATION_SEC_THRESHOLD} PHASE5_STRICT_FLAKY_RISK_SCORE_THRESHOLD=${DELIVERY_GATE_PHASE5_STRICT_FLAKY_RISK_SCORE_THRESHOLD} bash scripts/phase5-regression.sh"
    fi

    local strict_suggestions=()
    local strict_reason
    for strict_reason in "${phase5_strict_reasons[@]}"; do
      case "${strict_reason}" in
        recovery_guard)
          if [[ -n "${recovery_cmd}" ]]; then
            strict_suggestions+=("${recovery_cmd}")
          fi
          ;;
        hotspot_threshold)
          if [[ -n "${hotspot_cmd}" ]]; then
            strict_suggestions+=("${hotspot_cmd}")
          fi
          ;;
        flaky_risk_threshold)
          if [[ -n "${flaky_cmd}" ]]; then
            strict_suggestions+=("${flaky_cmd}")
          fi
          ;;
      esac
    done

    if [[ -n "${recovery_cmd}" ]]; then
      strict_suggestions+=("${recovery_cmd}")
    fi
    if [[ -n "${hotspot_cmd}" ]]; then
      strict_suggestions+=("${hotspot_cmd}")
    fi
    if [[ -n "${flaky_cmd}" ]]; then
      strict_suggestions+=("${flaky_cmd}")
    fi

    if [[ "${#strict_suggestions[@]}" -gt 0 ]]; then
      echo " - Suggested commands (priority order):"
      local printed_suggestions=()
      local suggestion_idx=1
      local suggestion_line
      for suggestion_line in "${strict_suggestions[@]}"; do
        local already_printed=0
        local existing_suggestion
        for existing_suggestion in "${printed_suggestions[@]}"; do
          if [[ "${existing_suggestion}" == "${suggestion_line}" ]]; then
            already_printed=1
            break
          fi
        done
        if [[ "${already_printed}" -eq 1 ]]; then
          continue
        fi
        printed_suggestions+=("${suggestion_line}")
        echo "   ${suggestion_idx}. ${suggestion_line}"
        suggestion_idx=$((suggestion_idx + 1))
      done
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
echo "DELIVERY_GATE_PHASE5_SUMMARY_DIR=${DELIVERY_GATE_PHASE5_SUMMARY_DIR:-<none>}"
echo "DELIVERY_GATE_PHASE5_RECOVERY_GUARD_STRICT=${DELIVERY_GATE_PHASE5_RECOVERY_GUARD_STRICT}"
echo "DELIVERY_GATE_PHASE5_RECOVERY_GUARD_STRICT_SOURCE=${DELIVERY_GATE_PHASE5_RECOVERY_GUARD_STRICT_SOURCE}"
echo "DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_DURATION_SEC_THRESHOLD=${DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_DURATION_SEC_THRESHOLD}"
echo "DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_DURATION_SEC_THRESHOLD_SOURCE=${DELIVERY_GATE_PHASE5_STRICT_HOTSPOT_DURATION_SEC_THRESHOLD_SOURCE}"
echo "DELIVERY_GATE_PHASE5_STRICT_FLAKY_RISK_SCORE_THRESHOLD=${DELIVERY_GATE_PHASE5_STRICT_FLAKY_RISK_SCORE_THRESHOLD}"
echo "DELIVERY_GATE_PHASE5_STRICT_FLAKY_RISK_SCORE_THRESHOLD_SOURCE=${DELIVERY_GATE_PHASE5_STRICT_FLAKY_RISK_SCORE_THRESHOLD_SOURCE}"
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

if [[ -n "${DELIVERY_GATE_EXECUTION_PLAN_FILE}" && -d "${DELIVERY_GATE_EXECUTION_PLAN_FILE}" ]]; then
  echo "error: DELIVERY_GATE_EXECUTION_PLAN_FILE points to a directory: ${DELIVERY_GATE_EXECUTION_PLAN_FILE}"
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
