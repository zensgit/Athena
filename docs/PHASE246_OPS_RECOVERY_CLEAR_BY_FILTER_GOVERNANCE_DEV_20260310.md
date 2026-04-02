# Phase 246 - Ops Recovery Dead-letter Clear-by-filter Governance (Dev)

Date: 2026-03-10  
Scope: `ecm-core`, `ecm-frontend`

## 1. Goals

1. Add filter-driven dead-letter clearing for operational governance (`reason/category/retryable/window`).
2. Expose clear-by-filter from diagnostics reason-group actions to reduce manual row-by-row cleanup.
3. Keep dead-letter replay/clear batch and history exports compatible.

## 2. Backend Implementation

File: `ecm-core/src/main/java/com/ecm/core/controller/OpsRecoveryController.java`

- Added endpoint:
  - `POST /api/v1/ops/recovery/clear-by-filter`
- Added scan logic:
  - `scanDeadLetterByFilter(...)`
  - inputs:
    - `reason`
    - `category` (`ANY` supported)
    - `retryable` (`null` means any)
    - `days` (window, `0` means all time)
    - `maxDocuments`
  - source:
    - dead-letter registry entries (`previewDeadLetterRegistry.list(MAX_BATCH_LIMIT)`)
  - output:
    - `totalMatched`, `scanned`, `matched`, `truncated`, `entryKeys`
- Reused clear executor:
  - `clearBatchInternal(...)` now executes selected matched keys
- Recovery mode extension:
  - `CLEAR_BY_FILTER` added to supported history modes
- Added request DTO:
  - `RecoveryClearByFilterRequestDto`
- Audit:
  - mode `CLEAR_BY_FILTER` logged via existing `auditRecovery(...)`
  - event type: `OPS_RECOVERY_CLEAR_BY_FILTER`

## 3. Frontend Implementation

### 3.1 Service contract

File: `ecm-frontend/src/services/opsRecoveryService.ts`

- Added method:
  - `clearByFilter(payload)`
- Added types:
  - `ClearByFilterRequest`
- Extended unions:
  - `RecoveryHistoryMode` adds `CLEAR_BY_FILTER`
  - `RecoveryHistoryEventType` adds `OPS_RECOVERY_CLEAR_BY_FILTER`

### 3.2 Diagnostics reason-group action

File: `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`

- Added action state:
  - `reasonClearActionKey`
- Added handler:
  - `handleClearDeadLetterByReason(reason, category, retryable)`
  - calls `opsRecoveryService.clearByFilter(...)`
  - refreshes diagnostics on completion
- Extended top reasons action bar:
  - new button: `Clear DL`
  - `aria-label="Clear dead-letter reason group ..."`
- Extended history filter options:
  - mode option `Clear by Filter`
  - event option `Clear by Filter Event`

### 3.3 Mocked e2e updates

File: `ecm-frontend/e2e/admin-preview-diagnostics.mock.spec.ts`

- Added route mock:
  - `POST /api/v1/ops/recovery/clear-by-filter`
- Added call capture assertions:
  - reason/category/retryable
- Added UI step:
  - click reason-group `Clear dead-letter reason group ...`
  - assert clear success toast

## 4. Design Notes

- This closes the governance gap between “detect reason group” and “act on reason group” for dead-letter backlog.
- Cleanup operations now support both:
  - explicit target selection (`clear-batch`)
  - policy-style target selection (`clear-by-filter`)
