# Phase 124: Gate Recovery Registry Sync Automation

## Date
2026-02-25

## Background
- `phase5-regression` already supports registry sync mode via `PHASE5_RECOVERY_REGISTRY_SYNC=1`.
- Delivery gate preflight still required manual pre-sync in some local/CI flows.

## Goals
1. Let delivery gate preflight opt into registry auto-sync when explicitly enabled.
2. Add a one-command helper script for operators to sync registry deterministically.
3. Keep default gate behavior unchanged unless sync mode is requested.

## Changes

### 1) Delivery gate preflight sync passthrough
- `scripts/phase5-phase6-delivery-gate.sh`
  - new env:
    - `DELIVERY_GATE_RECOVERY_REGISTRY_SYNC` (default `0`)
  - mocked registry preflight stage now passes:
    - `PHASE5_RECOVERY_REGISTRY_SYNC="${DELIVERY_GATE_RECOVERY_REGISTRY_SYNC}"`
  - startup banner includes `DELIVERY_GATE_RECOVERY_REGISTRY_SYNC`.
  - startup hints include actionable guidance when registry mismatch is detected and sync is not enabled.

### 2) Dedicated sync helper script
- `scripts/phase5-sync-recovery-registry.sh`
  - added executable helper for deterministic sync:
    - `PHASE5_RECOVERY_REGISTRY_SYNC=1`
    - `PHASE5_RECOVERY_REGISTRY_STRICT=1`
    - `PHASE5_VALIDATE_RECOVERY_REGISTRY_ONLY=1`
  - default registry target:
    - `ecm-frontend/e2e/recovery-events.expected.txt`

## Impact
- No product runtime behavior change.
- Reduces operator friction for registry drift recovery in local and CI delivery-gate flows.
