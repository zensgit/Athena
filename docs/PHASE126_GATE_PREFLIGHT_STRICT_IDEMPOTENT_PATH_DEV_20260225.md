# Phase 126: Gate Preflight Strict and Idempotent Path

## Date
2026-02-25

## Background
- Phase125 added CI default registry sync and deterministic DIFF output.
- Mocked preflight still used a single `phase5-regression` path regardless of whether idempotence verification was desired.

## Goals
1. Make registry preflight in gate support strict/idempotent execution path.
2. Add CI-friendly defaults for registry strictness and idempotent verification.
3. Improve failure hints for deterministic mismatch scenarios.

## Changes

### 1) New gate-level defaults and sources
- `scripts/phase5-phase6-delivery-gate.sh`
  - added env resolution with explicit source logging:
    - `DELIVERY_GATE_RECOVERY_REGISTRY_STRICT`
    - `DELIVERY_GATE_RECOVERY_REGISTRY_VERIFY_IDEMPOTENT`
  - priority:
    1. explicit env
    2. CI default (`1`)
    3. local default (`0`)
  - startup banner now prints both value and source for each setting.

### 2) Preflight routing to idempotent sync helper
- `scripts/phase5-phase6-delivery-gate.sh`
  - `run_mocked_recovery_registry_preflight_stage` now routes as:
    - when `DELIVERY_GATE_RECOVERY_REGISTRY_SYNC=1` and `DELIVERY_GATE_RECOVERY_REGISTRY_VERIFY_IDEMPOTENT=1`:
      - execute `scripts/phase5-sync-recovery-registry.sh`
    - otherwise:
      - execute `phase5-regression` registry-only preflight path
      - pass `PHASE5_RECOVERY_REGISTRY_STRICT="${DELIVERY_GATE_RECOVERY_REGISTRY_STRICT}"`

### 3) Deterministic mismatch hinting
- `scripts/phase5-phase6-delivery-gate.sh`
  - startup hint parser now detects:
    - `phase5_sync_recovery_registry: deterministic mismatch`
  - emits targeted remediation hint to rerun sync helper and inspect diff output.

## Impact
- No product runtime behavior change.
- Gate preflight is stricter and more deterministic in CI by default.
- Local flows remain compatible with opt-in strict/idempotent behavior.
