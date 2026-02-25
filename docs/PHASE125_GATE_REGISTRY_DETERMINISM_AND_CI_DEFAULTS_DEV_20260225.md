# Phase 125: Gate Registry Determinism and CI Defaults

## Date
2026-02-25

## Background
- Phase124 introduced optional delivery-gate registry auto-sync and standalone sync helper.
- Remaining gaps:
  - CI still relied on explicit opt-in env for registry sync.
  - Registry mismatch diagnostics lacked stable machine-readable diff lines.
  - Sync helper had no explicit deterministic idempotence check.

## Goals
1. Enable delivery-gate registry sync by default in CI while keeping local default unchanged.
2. Emit deterministic registry mismatch diff lines from `phase5-regression`.
3. Add idempotence verification to `phase5-sync-recovery-registry.sh`.

## Changes

### 1) CI default for registry sync in delivery gate
- `scripts/phase5-phase6-delivery-gate.sh`
  - `DELIVERY_GATE_RECOVERY_REGISTRY_SYNC` now resolves by priority:
    1. explicit env value
    2. CI default (`1` when `CI` is set)
    3. local default (`0`)
  - startup output adds:
    - `DELIVERY_GATE_RECOVERY_REGISTRY_SYNC_SOURCE=env|ci_default|local_default`

### 2) Deterministic diff output in registry validation
- `scripts/phase5-regression.sh`
  - `validate_recovery_event_registry` now emits machine-readable diff lines:
    - `DIFF missing_from_events_file_count`
    - `DIFF missing_from_events_file_csv`
    - `DIFF stale_events_file_entries_count`
    - `DIFF stale_events_file_entries_csv`
  - preserves existing WARN lines for backward-compatible diagnostics.

### 3) Delivery-gate hint parser consumes DIFF CSV
- `scripts/phase5-phase6-delivery-gate.sh`
  - registry hint parser now reads DIFF CSV lines first and merges with WARN fallback lines.
  - consolidates event rendering via shared unique-summary helper.

### 4) Sync helper deterministic idempotence check
- `scripts/phase5-sync-recovery-registry.sh`
  - supports path normalization for registry file under `ecm-frontend/`.
  - after primary sync, performs second sync into temp file and `cmp` against target.
  - fails with `deterministic mismatch` if second generation differs.
  - new env:
    - `PHASE5_SYNC_VERIFY_IDEMPOTENT` (default `1`).

## Impact
- No runtime product behavior change.
- CI gate is less fragile for registry drift.
- Registry diagnostics become easier to automate and triage.
