#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

ECM_UI_URL="${ECM_UI_URL:-http://localhost:5500}"
PW_WORKERS="${PW_WORKERS:-1}"
PW_PROJECT="${PW_PROJECT:-chromium}"

echo "phase5_regression: start"
echo "ECM_UI_URL=${ECM_UI_URL}"
echo "PW_PROJECT=${PW_PROJECT} PW_WORKERS=${PW_WORKERS}"

# This gate is intentionally "mocked-first" so it can run without Docker/backend.
PHASE5_SPECS=(
  "e2e/admin-preview-diagnostics.mock.spec.ts"
  "e2e/permissions-dialog-presets.mock.spec.ts"
  "e2e/admin-audit-filter-export.mock.spec.ts"
  "e2e/version-history-paging-major-only.mock.spec.ts"
  "e2e/search-suggestions-save-search.mock.spec.ts"
  "e2e/mail-automation-trigger-fetch.mock.spec.ts"
  "e2e/mail-automation-diagnostics-export.mock.spec.ts"
  "e2e/mail-automation-processed-management.mock.spec.ts"
)

if [[ ! -d "ecm-frontend" ]]; then
  echo "error: missing ecm-frontend/"
  exit 1
fi

cd ecm-frontend

echo "phase5_regression: build frontend"
npm run build

echo "phase5_regression: ensure static server reachable"
if ! curl -fsS --max-time 3 "${ECM_UI_URL}" >/dev/null 2>&1; then
  case "${ECM_UI_URL}" in
    http://localhost:5500*|http://127.0.0.1:5500*)
      echo "phase5_regression: starting static server on :5500"
      python3 -m http.server 5500 --directory build >/tmp/phase5-regression.http.log 2>&1 &
      srv_pid=$!
      trap 'kill "${srv_pid}" >/dev/null 2>&1 || true' EXIT

      for _ in $(seq 1 20); do
        if curl -fsS --max-time 1 "${ECM_UI_URL}" >/dev/null 2>&1; then
          break
        fi
        sleep 0.3
      done
      ;;
    *)
      echo "error: ECM_UI_URL not reachable: ${ECM_UI_URL}"
      echo "hint: start a server (dev on :3000 or static on :5500) or set ECM_UI_URL accordingly"
      exit 1
      ;;
  esac
fi

echo "phase5_regression: check e2e target"
ALLOW_STATIC=1 ../scripts/check-e2e-target.sh "${ECM_UI_URL}" || true

echo "phase5_regression: run playwright specs"
ECM_UI_URL="${ECM_UI_URL}" npx playwright test \
  "${PHASE5_SPECS[@]}" \
  --project="${PW_PROJECT}" --workers="${PW_WORKERS}"

echo "phase5_regression: ok"
