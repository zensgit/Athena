#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

ECM_API_URL="${ECM_API_URL:-http://localhost:7700}"
KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8180}"
KEYCLOAK_REALM="${KEYCLOAK_REALM:-ecm}"
ECM_UI_URL="${ECM_UI_URL:-http://localhost:3000}"

ECM_E2E_USERNAME="${ECM_E2E_USERNAME:-admin}"
ECM_E2E_PASSWORD="${ECM_E2E_PASSWORD:-admin}"

PW_PROJECT="${PW_PROJECT:-chromium}"
PW_WORKERS="${PW_WORKERS:-1}"
FULLSTACK_ALLOW_STATIC="${FULLSTACK_ALLOW_STATIC:-1}"
ECM_SYNC_PREBUILT_UI="${ECM_SYNC_PREBUILT_UI:-auto}"

echo "phase5_search_suggestions_integration_smoke: start"
echo "ECM_UI_URL=${ECM_UI_URL}"
echo "ECM_API_URL=${ECM_API_URL}"
echo "KEYCLOAK_URL=${KEYCLOAK_URL} KEYCLOAK_REALM=${KEYCLOAK_REALM}"
echo "PW_PROJECT=${PW_PROJECT} PW_WORKERS=${PW_WORKERS}"
echo "FULLSTACK_ALLOW_STATIC=${FULLSTACK_ALLOW_STATIC}"
echo "ECM_SYNC_PREBUILT_UI=${ECM_SYNC_PREBUILT_UI}"

case "${ECM_UI_URL}" in
  http://localhost:5500*|http://127.0.0.1:5500*)
    echo "error: ECM_UI_URL points at static build (:5500) and may not proxy /api."
    echo "hint: use dev server (:3000) or reverse-proxy (e.g. http://localhost)."
    exit 2
    ;;
esac

echo "phase5_search_suggestions_integration_smoke: prebuilt sync check"
ECM_SYNC_PREBUILT_UI="${ECM_SYNC_PREBUILT_UI}" \
bash scripts/sync-prebuilt-frontend-if-needed.sh "${ECM_UI_URL}"

echo "phase5_search_suggestions_integration_smoke: check backend health"
curl -fsS --max-time 5 "${ECM_API_URL}/actuator/health" >/dev/null

echo "phase5_search_suggestions_integration_smoke: check keycloak discovery"
curl -fsS --max-time 5 "${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}/.well-known/openid-configuration" >/dev/null

echo "phase5_search_suggestions_integration_smoke: check UI reachable"
curl -fsS --max-time 5 "${ECM_UI_URL}" >/dev/null

echo "phase5_search_suggestions_integration_smoke: check e2e target"
ALLOW_STATIC="${FULLSTACK_ALLOW_STATIC}" scripts/check-e2e-target.sh "${ECM_UI_URL}"

if [[ ! -d "ecm-frontend" ]]; then
  echo "error: missing ecm-frontend/"
  exit 1
fi

cd ecm-frontend

echo "phase5_search_suggestions_integration_smoke: run playwright"
ECM_UI_URL="${ECM_UI_URL}" \
ECM_API_URL="${ECM_API_URL}" \
KEYCLOAK_URL="${KEYCLOAK_URL}" \
KEYCLOAK_REALM="${KEYCLOAK_REALM}" \
ECM_E2E_USERNAME="${ECM_E2E_USERNAME}" \
ECM_E2E_PASSWORD="${ECM_E2E_PASSWORD}" \
npx playwright test \
  e2e/search-suggestions-save-search.integration.spec.ts \
  --project="${PW_PROJECT}" --workers="${PW_WORKERS}"

echo "phase5_search_suggestions_integration_smoke: ok"
