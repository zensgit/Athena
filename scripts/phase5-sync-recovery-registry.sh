#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"

RECOVERY_EVENTS_FILE="${PHASE5_RECOVERY_EVENTS_FILE:-ecm-frontend/e2e/recovery-events.expected.txt}"
PHASE5_SYNC_VERIFY_IDEMPOTENT="${PHASE5_SYNC_VERIFY_IDEMPOTENT:-1}"

resolve_registry_rel_path() {
  local input_path="$1"
  local normalized="${input_path}"
  local frontend_root="${ROOT_DIR}/ecm-frontend/"

  if [[ "${normalized}" == "${frontend_root}"* ]]; then
    normalized="${normalized#"${frontend_root}"}"
  elif [[ "${normalized}" == ecm-frontend/* ]]; then
    normalized="${normalized#ecm-frontend/}"
  fi

  if [[ "${normalized}" == /* || "${normalized}" == *".."* ]]; then
    echo "error: PHASE5_RECOVERY_EVENTS_FILE must resolve under ecm-frontend/: ${input_path}" >&2
    return 1
  fi
  printf '%s' "${normalized}"
}

run_registry_sync() {
  local registry_rel="$1"
  PHASE5_RECOVERY_EVENTS_FILE="${registry_rel}" \
  PHASE5_RECOVERY_REGISTRY_SYNC=1 \
  PHASE5_RECOVERY_REGISTRY_STRICT=1 \
  PHASE5_VALIDATE_RECOVERY_REGISTRY_ONLY=1 \
  bash scripts/phase5-regression.sh
}

REGISTRY_REL_PATH="$(resolve_registry_rel_path "${RECOVERY_EVENTS_FILE}")"
REGISTRY_ABS_PATH="${ROOT_DIR}/ecm-frontend/${REGISTRY_REL_PATH}"

echo "phase5_sync_recovery_registry: start"
echo "PHASE5_RECOVERY_EVENTS_FILE=${RECOVERY_EVENTS_FILE}"
echo "REGISTRY_REL_PATH=${REGISTRY_REL_PATH}"
echo "PHASE5_SYNC_VERIFY_IDEMPOTENT=${PHASE5_SYNC_VERIFY_IDEMPOTENT}"

run_registry_sync "${REGISTRY_REL_PATH}"

if [[ "${PHASE5_SYNC_VERIFY_IDEMPOTENT}" == "1" ]]; then
  TMP_SYNC_REL_PATH="$(dirname "${REGISTRY_REL_PATH}")/.recovery-sync-check.$$.$RANDOM.tmp"
  TMP_SYNC_ABS_PATH="${ROOT_DIR}/ecm-frontend/${TMP_SYNC_REL_PATH}"
  trap 'rm -f "${TMP_SYNC_ABS_PATH}" >/dev/null 2>&1 || true' EXIT

  run_registry_sync "${TMP_SYNC_REL_PATH}"
  if ! cmp -s "${REGISTRY_ABS_PATH}" "${TMP_SYNC_ABS_PATH}"; then
    echo "phase5_sync_recovery_registry: deterministic mismatch"
    diff -u "${REGISTRY_ABS_PATH}" "${TMP_SYNC_ABS_PATH}" || true
    exit 1
  fi
  rm -f "${TMP_SYNC_ABS_PATH}" >/dev/null 2>&1 || true
  trap - EXIT
  echo "phase5_sync_recovery_registry: deterministic check passed"
fi

echo "phase5_sync_recovery_registry: ok"
