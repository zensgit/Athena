# Phase 247 - Ops Recovery Dry-run Filter Modes (Dev)

Date: 2026-03-10  
Scope: `ecm-core`, `ecm-frontend`

## 1. Goals

1. Extend ops-recovery dry-run so admins can pre-estimate dead-letter replay/clear operations by filter.
2. Keep one control-plane: same filter dimensions (`reason/category/retryable/window/maxDocuments`) for dry-run and execute.
3. Preserve existing queue/rebuild dry-run behavior and history/audit continuity.

## 2. Backend Implementation

File: `ecm-core/src/main/java/com/ecm/core/controller/OpsRecoveryController.java`

- Extended `DryRunMode`:
  - `CLEAR_BY_FILTER`
  - `REPLAY_BY_FILTER`
- Extended mode resolver:
  - `resolveMode(...)` now accepts `CLEAR_BY_FILTER` and `REPLAY_BY_FILTER`.
- Extended `/api/v1/ops/recovery/dry-run` execution branch:
  - Reuses `scanDeadLetterByFilter(...)` for target discovery.
  - Computes dry-run outcomes with dedicated evaluators:
    - `evaluateDeadLetterClearDryRun(...)`
    - `evaluateDeadLetterReplayDryRun(...)`
- Added dead-letter dry-run prediction semantics:
  - Clear mode primary estimate maps to “would clear” count (`estimatedQueued` field reused as primary operation count).
  - Replay mode predicts queue/skip/fail using existing `predict(document, force)` logic.
- Existing audit pipeline remains unchanged:
  - mode recorded as `CLEAR_BY_FILTER` / `REPLAY_BY_FILTER`
  - event type stays `OPS_RECOVERY_DRY_RUN`.

## 3. Frontend Implementation

### 3.1 Service contract

File: `ecm-frontend/src/services/opsRecoveryService.ts`

- Extended `RecoveryDryRunRequest.mode` union:
  - `CLEAR_BY_FILTER`
  - `REPLAY_BY_FILTER`

### 3.2 Diagnostics Dry-run panel

File: `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`

- Added dry-run mode options:
  - `Replay by Filter`
  - `Clear by Filter`
- Extended Execute Recovery branching:
  - queue modes keep existing behavior
  - clear filter mode executes `opsRecoveryService.clearByFilter(...)`
  - replay filter mode executes `opsRecoveryService.replayByFilter(...)`
- Improved dry-run summary rendering:
  - dynamic primary metric labels:
    - `Estimated queued`
    - `Estimated replay queued`
    - `Estimated cleared`
- Force control now hidden for `CLEAR_BY_FILTER` mode (not meaningful there).
- Reason input is available for non-window-only modes and remains required for `QUEUE_BY_REASON`.

### 3.3 Mocked e2e selector hardening

File: `ecm-frontend/e2e/admin-preview-diagnostics.mock.spec.ts`

- Expanded dry-run flow coverage:
  - execute `QUEUE_BY_WINDOW`, `CLEAR_BY_FILTER`, `REPLAY_BY_FILTER` from the Ops Recovery Dry-run panel
  - assert corresponding toast semantics (`queued`, `cleared`, `replayQueued`)
  - assert dry-run mode call capture includes new modes
- Updated one assertion selector from `/Replay queued:/i` to `/^Replay queued:/i` to avoid strict-mode ambiguity after adding dead-letter replay toast text.

## 4. Test Coverage Extension

File: `ecm-core/src/test/java/com/ecm/core/controller/OpsRecoveryControllerSecurityTest.java`

- Added `dryRunSupportsClearByFilterMode`:
  - validates `mode=CLEAR_BY_FILTER`
  - validates predicted `CLEARED` sample and estimated counts
  - validates no enqueue side effect (`previewQueueService.enqueue` not invoked)

## 5. Design Notes

- This phase closes the preflight gap for filter-governed dead-letter operations:
  - before: clear/replay by filter could execute directly
  - now: same filters can be dry-run first, then executed from the same panel.
