# Phase 121: Mocked Registry Preflight Stage

## Date
2026-02-25

## Background
- Recovery-event registry was externalized and validated in `phase5-regression`.
- Delivery gate still ran full mocked regression directly, even when registry mismatches could be detected earlier.

## Goals
1. Add lightweight registry-only execution mode in `phase5-regression`.
2. Add a fast registry preflight stage before mocked regression in delivery gate.
3. Fail fast on registry mismatch before expensive build/test path.

## Changes

### 1) Registry-only mode
- `scripts/phase5-regression.sh`
  - new env:
    - `PHASE5_VALIDATE_RECOVERY_REGISTRY_ONLY` (default `0`)
  - when set to `1`, script:
    - runs registry validation
    - exits early with `phase5_regression: registry-only mode complete`
    - skips build and Playwright execution.

### 2) Delivery gate fast-layer preflight
- `scripts/phase5-phase6-delivery-gate.sh`
  - new fast stage:
    - `mocked recovery registry preflight`
  - stage executes:
    - `PHASE5_VALIDATE_RECOVERY_REGISTRY_ONLY=1 bash scripts/phase5-regression.sh`
  - if preflight fails:
    - mocked regression stage is skipped
    - gate fails early with clear stage status.

## Impact
- No runtime product behavior change.
- Faster and clearer failure path for registry drift in CI/local delivery-gate runs.
