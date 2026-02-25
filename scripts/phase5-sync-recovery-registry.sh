#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${ROOT_DIR}"

RECOVERY_EVENTS_FILE="${PHASE5_RECOVERY_EVENTS_FILE:-ecm-frontend/e2e/recovery-events.expected.txt}"

echo "phase5_sync_recovery_registry: start"
echo "PHASE5_RECOVERY_EVENTS_FILE=${RECOVERY_EVENTS_FILE}"

PHASE5_RECOVERY_EVENTS_FILE="${RECOVERY_EVENTS_FILE#ecm-frontend/}" \
PHASE5_RECOVERY_REGISTRY_SYNC=1 \
PHASE5_RECOVERY_REGISTRY_STRICT=1 \
PHASE5_VALIDATE_RECOVERY_REGISTRY_ONLY=1 \
bash scripts/phase5-regression.sh

echo "phase5_sync_recovery_registry: ok"
