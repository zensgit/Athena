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
ECM_FULLSTACK_ALLOW_STATIC="${ECM_FULLSTACK_ALLOW_STATIC:-1}"

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

ECM_UI_URL_FULLSTACK="$(resolve_fullstack_ui_url)"

echo "phase5_phase6_delivery_gate: start"
echo "ECM_UI_URL_MOCKED=${ECM_UI_URL_MOCKED}"
echo "ECM_UI_URL_FULLSTACK=${ECM_UI_URL_FULLSTACK}"
echo "ECM_API_URL=${ECM_API_URL}"
echo "KEYCLOAK_URL=${KEYCLOAK_URL} KEYCLOAK_REALM=${KEYCLOAK_REALM}"
echo "PW_PROJECT=${PW_PROJECT} PW_WORKERS=${PW_WORKERS}"
echo "ECM_FULLSTACK_ALLOW_STATIC=${ECM_FULLSTACK_ALLOW_STATIC}"
if [[ -z "${ECM_UI_URL_FULLSTACK_INPUT}" ]]; then
  echo "ECM_UI_URL_FULLSTACK auto-detected (set ECM_UI_URL_FULLSTACK to override)"
fi

echo ""
echo "[1/5] mocked regression gate"
ECM_UI_URL="${ECM_UI_URL_MOCKED}" \
PW_PROJECT="${PW_PROJECT}" \
PW_WORKERS="${PW_WORKERS}" \
bash scripts/phase5-regression.sh

echo ""
echo "[2/5] full-stack admin smoke"
ECM_UI_URL="${ECM_UI_URL_FULLSTACK}" \
ECM_API_URL="${ECM_API_URL}" \
KEYCLOAK_URL="${KEYCLOAK_URL}" \
KEYCLOAK_REALM="${KEYCLOAK_REALM}" \
ECM_E2E_USERNAME="${ECM_E2E_USERNAME}" \
ECM_E2E_PASSWORD="${ECM_E2E_PASSWORD}" \
PW_PROJECT="${PW_PROJECT}" \
PW_WORKERS="${PW_WORKERS}" \
FULLSTACK_ALLOW_STATIC="${ECM_FULLSTACK_ALLOW_STATIC}" \
bash scripts/phase5-fullstack-smoke.sh

echo ""
echo "[3/5] phase6 mail integration smoke"
ECM_UI_URL="${ECM_UI_URL_FULLSTACK}" \
ECM_API_URL="${ECM_API_URL}" \
KEYCLOAK_URL="${KEYCLOAK_URL}" \
KEYCLOAK_REALM="${KEYCLOAK_REALM}" \
ECM_E2E_USERNAME="${ECM_E2E_USERNAME}" \
ECM_E2E_PASSWORD="${ECM_E2E_PASSWORD}" \
PW_PROJECT="${PW_PROJECT}" \
PW_WORKERS="${PW_WORKERS}" \
FULLSTACK_ALLOW_STATIC="${ECM_FULLSTACK_ALLOW_STATIC}" \
bash scripts/phase6-mail-automation-integration-smoke.sh

echo ""
echo "[4/5] phase5 search suggestions integration smoke"
ECM_UI_URL="${ECM_UI_URL_FULLSTACK}" \
ECM_API_URL="${ECM_API_URL}" \
KEYCLOAK_URL="${KEYCLOAK_URL}" \
KEYCLOAK_REALM="${KEYCLOAK_REALM}" \
ECM_E2E_USERNAME="${ECM_E2E_USERNAME}" \
ECM_E2E_PASSWORD="${ECM_E2E_PASSWORD}" \
PW_PROJECT="${PW_PROJECT}" \
PW_WORKERS="${PW_WORKERS}" \
FULLSTACK_ALLOW_STATIC="${ECM_FULLSTACK_ALLOW_STATIC}" \
bash scripts/phase5-search-suggestions-integration-smoke.sh

echo ""
echo "[5/5] p1 smoke"
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

echo ""
echo "phase5_phase6_delivery_gate: ok"
