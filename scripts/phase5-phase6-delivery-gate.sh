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

get_file_mtime_epoch() {
  local target_file="$1"
  if stat -f %m "${target_file}" >/dev/null 2>&1; then
    stat -f %m "${target_file}"
    return
  fi
  if stat -c %Y "${target_file}" >/dev/null 2>&1; then
    stat -c %Y "${target_file}"
    return
  fi
  printf '0'
}

is_local_static_proxy_target() {
  local target="${1%/}"
  case "${target}" in
    http://localhost|http://localhost:80|http://127.0.0.1|http://127.0.0.1:80)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

is_prebuilt_frontend_stale() {
  local manifest_path="ecm-frontend/build/asset-manifest.json"
  if [[ ! -f "${manifest_path}" ]]; then
    return 0
  fi

  local source_epoch
  local build_epoch
  source_epoch="$(git log -1 --format=%ct -- \
    ecm-frontend/src \
    ecm-frontend/public \
    ecm-frontend/package.json \
    ecm-frontend/package-lock.json 2>/dev/null || echo 0)"
  build_epoch="$(get_file_mtime_epoch "${manifest_path}")"

  if ! [[ "${source_epoch}" =~ ^[0-9]+$ ]]; then
    source_epoch=0
  fi
  if ! [[ "${build_epoch}" =~ ^[0-9]+$ ]]; then
    build_epoch=0
  fi

  [[ "${build_epoch}" -lt "${source_epoch}" ]]
}

sync_prebuilt_frontend_if_needed() {
  if ! is_local_static_proxy_target "${ECM_UI_URL_FULLSTACK}"; then
    return
  fi

  case "${ECM_SYNC_PREBUILT_UI}" in
    0|false|FALSE|no|NO)
      echo "phase5_phase6_delivery_gate: skip prebuilt frontend sync (ECM_SYNC_PREBUILT_UI=${ECM_SYNC_PREBUILT_UI})"
      return
      ;;
    1|true|TRUE|yes|YES)
      echo "phase5_phase6_delivery_gate: force refresh prebuilt frontend"
      bash scripts/rebuild-frontend-prebuilt.sh
      return
      ;;
    auto|AUTO)
      if is_prebuilt_frontend_stale; then
        echo "phase5_phase6_delivery_gate: stale prebuilt frontend detected, rebuilding"
        bash scripts/rebuild-frontend-prebuilt.sh
      else
        echo "phase5_phase6_delivery_gate: prebuilt frontend is up-to-date, skip rebuild"
      fi
      return
      ;;
    *)
      echo "phase5_phase6_delivery_gate: unknown ECM_SYNC_PREBUILT_UI=${ECM_SYNC_PREBUILT_UI}, treating as auto"
      if is_prebuilt_frontend_stale; then
        echo "phase5_phase6_delivery_gate: stale prebuilt frontend detected, rebuilding"
        bash scripts/rebuild-frontend-prebuilt.sh
      else
        echo "phase5_phase6_delivery_gate: prebuilt frontend is up-to-date, skip rebuild"
      fi
      ;;
  esac
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
if [[ -z "${ECM_UI_URL_FULLSTACK_INPUT}" ]]; then
  echo "ECM_UI_URL_FULLSTACK auto-detected (set ECM_UI_URL_FULLSTACK to override)"
fi

if [[ "${ECM_FULLSTACK_ALLOW_STATIC}" == "0" ]]; then
  echo "phase5_phase6_delivery_gate: strict fullstack target preflight"
  ALLOW_STATIC=0 scripts/check-e2e-target.sh "${ECM_UI_URL_FULLSTACK}"
fi

echo ""
echo "[1/5] mocked regression gate"
ECM_UI_URL="${ECM_UI_URL_MOCKED}" \
PW_PROJECT="${PW_PROJECT}" \
PW_WORKERS="${PW_WORKERS}" \
bash scripts/phase5-regression.sh

echo ""
echo "phase5_phase6_delivery_gate: full-stack prebuilt sync check"
sync_prebuilt_frontend_if_needed

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
