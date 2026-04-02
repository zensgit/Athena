#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKEND_DIR="${ROOT_DIR}/ecm-core"
FRONTEND_DIR="${ROOT_DIR}/ecm-frontend"

PW_PROJECT="${PW_PROJECT:-chromium}"
RUN_E2E="${RUN_E2E:-1}"
E2E_SPEC="${E2E_SPEC:-e2e/admin-preview-diagnostics.mock.spec.ts}"
E2E_PORT="${E2E_PORT:-5500}"
E2E_BASE_URL="${E2E_BASE_URL:-http://localhost:${E2E_PORT}}"

SERVER_PID=""

cleanup() {
  if [[ -n "${SERVER_PID}" ]]; then
    kill "${SERVER_PID}" >/dev/null 2>&1 || true
    wait "${SERVER_PID}" >/dev/null 2>&1 || true
    SERVER_PID=""
  fi
}
trap cleanup EXIT

run_step() {
  local title="$1"
  shift
  echo
  echo "==> ${title}"
  "$@"
}

start_static_server() {
  local log_file="${ROOT_DIR}/tmp/preview-day7-http-server.log"
  mkdir -p "$(dirname "${log_file}")"
  : > "${log_file}"
  (
    cd "${FRONTEND_DIR}" && \
      python3 -m http.server "${E2E_PORT}" --directory build >>"${log_file}" 2>&1
  ) &
  SERVER_PID="$!"
  sleep 1
  if ! kill -0 "${SERVER_PID}" >/dev/null 2>&1; then
    echo "Failed to start static server on port ${E2E_PORT}. See ${log_file}."
    exit 1
  fi
}

echo "phase164_preview_day7_delivery_gate: start"
echo "ROOT_DIR=${ROOT_DIR}"
echo "RUN_E2E=${RUN_E2E} E2E_SPEC=${E2E_SPEC} E2E_BASE_URL=${E2E_BASE_URL} PW_PROJECT=${PW_PROJECT}"

run_step "Backend targeted tests" \
  bash -lc "cd '${BACKEND_DIR}' && mvn -q -Dtest=PreviewDiagnosticsControllerSecurityTest,PreviewQueueServiceTest,PreviewRenditionPreventionRegistryTest,PreviewQueueServiceRedisBackendTest,PreviewDeadLetterRegistryTest,PreviewDeadLetterRegistryRedisBackendTest test"

run_step "Backend compile" \
  bash -lc "cd '${BACKEND_DIR}' && mvn -q -DskipTests compile"

run_step "Frontend lint" \
  bash -lc "cd '${FRONTEND_DIR}' && npm run lint"

run_step "Frontend build" \
  bash -lc "cd '${FRONTEND_DIR}' && npm run build"

if [[ "${RUN_E2E}" == "1" ]]; then
  run_step "Start static server" start_static_server
  run_step "Mocked Playwright diagnostics E2E" \
    bash -lc "cd '${FRONTEND_DIR}' && ECM_UI_URL='${E2E_BASE_URL}' npx playwright test '${E2E_SPEC}' --project='${PW_PROJECT}'"
else
  echo
  echo "==> Skipped mocked Playwright diagnostics E2E (RUN_E2E=${RUN_E2E})"
fi

echo
echo "phase164_preview_day7_delivery_gate: PASS"
