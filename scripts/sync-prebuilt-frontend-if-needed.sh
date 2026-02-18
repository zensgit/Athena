#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"

TARGET_UI_URL="${1:-${ECM_UI_URL:-http://localhost}}"
SYNC_MODE="${ECM_SYNC_PREBUILT_UI:-auto}"

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

if ! is_local_static_proxy_target "${TARGET_UI_URL}"; then
  echo "sync_prebuilt_frontend_if_needed: skip (non-local-static target: ${TARGET_UI_URL})"
  exit 0
fi

case "${SYNC_MODE}" in
  0|false|FALSE|no|NO)
    echo "sync_prebuilt_frontend_if_needed: skip (ECM_SYNC_PREBUILT_UI=${SYNC_MODE})"
    exit 0
    ;;
  1|true|TRUE|yes|YES)
    echo "sync_prebuilt_frontend_if_needed: force rebuild for ${TARGET_UI_URL}"
    bash scripts/rebuild-frontend-prebuilt.sh
    exit 0
    ;;
  auto|AUTO)
    if is_prebuilt_frontend_stale; then
      echo "sync_prebuilt_frontend_if_needed: stale prebuilt detected, rebuilding for ${TARGET_UI_URL}"
      bash scripts/rebuild-frontend-prebuilt.sh
    else
      echo "sync_prebuilt_frontend_if_needed: prebuilt up-to-date for ${TARGET_UI_URL}"
    fi
    exit 0
    ;;
  *)
    echo "sync_prebuilt_frontend_if_needed: unknown ECM_SYNC_PREBUILT_UI=${SYNC_MODE}, using auto"
    if is_prebuilt_frontend_stale; then
      echo "sync_prebuilt_frontend_if_needed: stale prebuilt detected, rebuilding for ${TARGET_UI_URL}"
      bash scripts/rebuild-frontend-prebuilt.sh
    else
      echo "sync_prebuilt_frontend_if_needed: prebuilt up-to-date for ${TARGET_UI_URL}"
    fi
    exit 0
    ;;
esac
