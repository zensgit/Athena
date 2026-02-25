# Phase 127: Verify-Sync Normalization and Strict Propagation

## Date
2026-02-25

## Background
- Phase126 introduced strict/idempotent preflight routing in delivery gate.
- Remaining usability gaps:
  - `DELIVERY_GATE_RECOVERY_REGISTRY_VERIFY_IDEMPOTENT=1` could be set without `DELIVERY_GATE_RECOVERY_REGISTRY_SYNC=1`, creating ambiguous intent.
  - strict mode in helper path needed explicit propagation from gate-level settings.

## Goals
1. Normalize gate config so `verify_idempotent=1` always implies `sync=1`.
2. Propagate gate-level strict setting into sync-helper preflight path.
3. Preserve backward compatibility for existing CI/local flows.

## Changes

### 1) Verify-to-sync normalization
- `scripts/phase5-phase6-delivery-gate.sh`
  - after env resolution, if:
    - `DELIVERY_GATE_RECOVERY_REGISTRY_VERIFY_IDEMPOTENT=1`
    - and `DELIVERY_GATE_RECOVERY_REGISTRY_SYNC!=1`
  - gate auto-upgrades sync to `1`.
  - sync source now reflects normalization:
    - `*_auto_verify_dependency` suffix.

### 2) Strict propagation to helper path
- `scripts/phase5-phase6-delivery-gate.sh`
  - when preflight routes to `scripts/phase5-sync-recovery-registry.sh`, now passes:
    - `PHASE5_RECOVERY_REGISTRY_STRICT="${DELIVERY_GATE_RECOVERY_REGISTRY_STRICT}"`

### 3) Sync helper strict configurability
- `scripts/phase5-sync-recovery-registry.sh`
  - adds configurable strict env:
    - `PHASE5_RECOVERY_REGISTRY_STRICT` (default `1`)
  - logs strict mode at startup.
  - uses configured strict value in both primary sync and idempotence verification sync.

## Impact
- No product runtime behavior change.
- Gate config is less error-prone and aligns with user intent when verify mode is requested.
