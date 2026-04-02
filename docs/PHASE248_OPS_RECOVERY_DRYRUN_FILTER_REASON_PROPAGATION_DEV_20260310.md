# Phase 248 - Ops Recovery Dry-run Filter Reason Propagation Hardening (Dev)

Date: 2026-03-10  
Scope: `ecm-frontend`, `ecm-core`

## 1. Goals

1. Fix dry-run request parity so new filter modes (`CLEAR_BY_FILTER`, `REPLAY_BY_FILTER`) correctly propagate reason filter input.
2. Eliminate stale `force=true` leakage in clear-mode dry-run/execute requests.
3. Tighten mocked e2e contract checks to guarantee mode + reason propagation.

## 2. Frontend Fixes

File: `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`

- Added mode guard to reset force state when switching to clear mode:
  - if `dryRunMode === CLEAR_BY_FILTER`, force is reset to `false`.
- Normalized dry-run request parameters:
  - reason now sent as `dryRunReason.trim() || undefined` for all modes.
  - force now normalized per mode (`CLEAR_BY_FILTER` always sends `false`).
- Normalized execute-request parameters using the same logic for all branches.

Impact:

- `Run Dry-run` and `Execute Recovery` now use identical filter intent for reason/category/retryable/window/max docs.
- No accidental force semantics in clear mode.

## 3. Mocked E2E Contract Hardening

File: `ecm-frontend/e2e/admin-preview-diagnostics.mock.spec.ts`

- Extended dry-run flow to explicitly fill reason in:
  - `CLEAR_BY_FILTER`
  - `REPLAY_BY_FILTER`
- Added assertions that `reasonDryRunCalls` includes:
  - `mode=CLEAR_BY_FILTER` with expected reason
  - `mode=REPLAY_BY_FILTER` with expected reason

This converts previously permissive mode checks into explicit request-shape validation.

## 4. Backend Test Extension

File: `ecm-core/src/test/java/com/ecm/core/controller/OpsRecoveryControllerSecurityTest.java`

- Added `dryRunSupportsReplayByFilterMode`:
  - verifies `mode=REPLAY_BY_FILTER`
  - verifies estimated queue prediction and sample predicted state/outcome
  - verifies dry-run path does not enqueue jobs.

## 5. Design Notes

- Phase 247 introduced filter dry-run modes.
- Phase 248 hardens request semantics and test contracts to prevent drift between dry-run planning and execution behavior.
