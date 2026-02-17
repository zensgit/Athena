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

extract_host() {
  local url="$1"
  printf '%s' "$url" | sed -E 's#^https?://([^/:]+).*#\1#'
}

extract_port() {
  local url="$1"
  local parsed_port
  parsed_port="$(printf '%s' "$url" | sed -nE 's#^https?://[^/:]+:([0-9]+).*$#\1#p')"
  if [[ -n "${parsed_port}" ]]; then
    printf '%s' "${parsed_port}"
    return
  fi
  if [[ "$url" == https://* ]]; then
    printf '443'
  else
    printf '80'
  fi
}

# This gate is intentionally "mocked-first" so it can run without Docker/backend.
PHASE5_SPECS=(
  "e2e/admin-preview-diagnostics.mock.spec.ts"
  "e2e/permissions-dialog-presets.mock.spec.ts"
  "e2e/admin-audit-filter-export.mock.spec.ts"
  "e2e/version-history-paging-major-only.mock.spec.ts"
  "e2e/search-suggestions-save-search.mock.spec.ts"
  "e2e/auth-session-recovery.mock.spec.ts"
  "e2e/mail-automation-trigger-fetch.mock.spec.ts"
  "e2e/mail-automation-diagnostics-export.mock.spec.ts"
  "e2e/mail-automation-processed-management.mock.spec.ts"
  "e2e/mail-automation-phase6-p1.mock.spec.ts"
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
  ui_host="$(extract_host "${ECM_UI_URL}")"
  ui_port="$(extract_port "${ECM_UI_URL}")"
  case "${ui_host}" in
    localhost|127.0.0.1)
      echo "phase5_regression: starting static SPA server on :${ui_port}"
      # Prefer `serve -s` for SPA routing; fallback to python for environments without Node package resolution.
      if command -v npx >/dev/null 2>&1; then
        npx serve -s build -l "${ui_port}" >/tmp/phase5-regression.http.log 2>&1 &
        srv_pid=$!
      else
        python3 -m http.server "${ui_port}" --directory build >/tmp/phase5-regression.http.log 2>&1 &
        srv_pid=$!
      fi
      trap 'kill "${srv_pid}" >/dev/null 2>&1 || true' EXIT

      for _ in $(seq 1 30); do
        if curl -fsS --max-time 1 "${ECM_UI_URL}" >/dev/null 2>&1; then
          break
        fi
        sleep 0.3
      done
      ;;
    *)
      echo "error: ECM_UI_URL not reachable: ${ECM_UI_URL}"
      echo "hint: start a server and set ECM_UI_URL accordingly"
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
