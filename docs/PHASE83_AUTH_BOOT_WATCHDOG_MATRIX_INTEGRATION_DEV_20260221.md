# Phase 83: Auth Boot Watchdog Matrix Integration

## Date
2026-02-21

## Background
- Auth boot watchdog recovery had mocked coverage and mocked gate integration.
- `phase70` auth-route matrix (integration/full-stack) lacked explicit watchdog recovery scenario.

## Goal
1. Promote auth boot watchdog recovery into phase70 matrix.
2. Ensure startup hang recovery is verified in integration/full-stack path, not only mocked path.

## Changes

### 1) Matrix expansion
- File: `ecm-frontend/e2e/auth-route-recovery.matrix.spec.ts`
- Added new scenario:
  - `matrix: forced auth boot hang is recoverable via watchdog continue-to-login action`
- Scenario flow:
  1. Seed localStorage overrides:
     - `ecm_e2e_force_auth_boot_hang=1`
     - `ecm_e2e_auth_boot_watchdog_ms=1200`
  2. Navigate to protected route `/browse/root`
  3. Verify auth boot screen + watchdog alert
  4. Click `Continue to login`
  5. Assert terminal recovery on `/login` with timeout guidance
  6. Assert override keys are cleaned

### 2) No script changes required
- Existing `scripts/phase70-auth-route-matrix-smoke.sh` remains unchanged and now executes 9 matrix cases.

## Non-Functional Notes
- No backend/API contract changes.
- This closes integration coverage gap for startup watchdog recovery.
