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

run_mocked_regression_stage() {
  ECM_UI_URL="${ECM_UI_URL_MOCKED}" \
  PW_PROJECT="${PW_PROJECT}" \
  PW_WORKERS="${PW_WORKERS}" \
  bash scripts/phase5-regression.sh
}

run_fullstack_target_preflight_stage() {
  ALLOW_STATIC=0 scripts/check-e2e-target.sh "${ECM_UI_URL_FULLSTACK}"
}

run_prebuilt_sync_stage() {
  ECM_SYNC_PREBUILT_UI="${ECM_SYNC_PREBUILT_UI}" \
  bash scripts/sync-prebuilt-frontend-if-needed.sh "${ECM_UI_URL_FULLSTACK}"
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
if [[ -z "${ECM_UI_URL_FULLSTACK_INPUT}" ]]; then
  echo "ECM_UI_URL_FULLSTACK auto-detected (set ECM_UI_URL_FULLSTACK to override)"
fi

case "${DELIVERY_GATE_MODE}" in
  all|mocked|integration)
    ;;
  *)
    echo "error: unsupported DELIVERY_GATE_MODE=${DELIVERY_GATE_MODE} (expected: all|mocked|integration)"
    exit 1
    ;;
esac

overall_rc=0
fast_layer_failed=0
integration_layer_failed=0

if [[ "${DELIVERY_GATE_MODE}" == "all" || "${DELIVERY_GATE_MODE}" == "mocked" ]]; then
  echo ""
  echo "=== Layer 1/2: fast mocked regression ==="
  if ! run_stage "fast" "mocked_regression" "mocked regression gate" run_mocked_regression_stage; then
    fast_layer_failed=1
    overall_rc=1
  fi
fi

run_integration_layer=0
if [[ "${DELIVERY_GATE_MODE}" == "integration" ]]; then
  run_integration_layer=1
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

  if [[ "${ECM_FULLSTACK_ALLOW_STATIC}" == "0" ]]; then
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
