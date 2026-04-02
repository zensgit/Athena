#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
FRONTEND_DIR="${REPO_ROOT}/ecm-frontend"

UI_URL="${ECM_UI_URL:-http://localhost:3000}"
UI_HEALTH_URL="${ECM_UI_HEALTH_URL:-${UI_URL}}"
PLAYWRIGHT_PROJECT="${ECM_E2E_PROJECT:-chromium}"
MAX_WAIT_SECONDS="${ECM_UI_WAIT_SECONDS:-180}"

if [ ! -d "${FRONTEND_DIR}" ]; then
  echo "frontend directory not found: ${FRONTEND_DIR}" >&2
  exit 1
fi

cd "${FRONTEND_DIR}"

TEST_FILES=("$@")
if [ ${#TEST_FILES[@]} -eq 0 ]; then
  TEST_FILES=(
    "e2e/admin-preview-diagnostics.mock.spec.ts"
    "e2e/advanced-search-preview-batch-scope.mock.spec.ts"
  )
fi

CI=true BROWSER=none npm start > /tmp/athena-phase235-e2e-devserver.log 2>&1 &
DEV_SERVER_PID=$!

cleanup() {
  kill "${DEV_SERVER_PID}" >/dev/null 2>&1 || true
}
trap cleanup EXIT

for ((i = 1; i <= MAX_WAIT_SECONDS; i += 1)); do
  if curl -fsS "${UI_HEALTH_URL}" >/dev/null 2>&1; then
    break
  fi
  sleep 1
  if [ "${i}" -eq "${MAX_WAIT_SECONDS}" ]; then
    echo "frontend dev server not ready within ${MAX_WAIT_SECONDS}s: ${UI_HEALTH_URL}" >&2
    exit 1
  fi
done

ECM_UI_URL="${UI_URL}" npx playwright test "${TEST_FILES[@]}" --project="${PLAYWRIGHT_PROJECT}"
