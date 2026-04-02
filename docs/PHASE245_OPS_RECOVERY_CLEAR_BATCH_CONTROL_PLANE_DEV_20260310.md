# Phase 245 - Ops Recovery Dead-letter Clear Batch Control-plane (Dev)

Date: 2026-03-10  
Scope: `ecm-core`, `ecm-frontend`

## 1. Goals

1. Unify dead-letter clear operation into Ops Recovery control-plane (`/ops/recovery`) alongside replay/dry-run.
2. Keep Preview Diagnostics dead-letter clear UI aligned to unified control-plane API path.
3. Extend recovery history filtering model to include clear-batch executions.

## 2. Backend Implementation

File: `ecm-core/src/main/java/com/ecm/core/controller/OpsRecoveryController.java`

- Added endpoint:
  - `POST /api/v1/ops/recovery/clear-batch`
- Added mode support:
  - `CLEAR_BATCH` included in `SUPPORTED_HISTORY_MODES`
- Added internal executor:
  - `clearBatchInternal(documentIds, entryKeys)`
  - behavior:
    - deduplicate entry targets
    - resolve dead-letter entry keys
    - clear from registry via `(documentId, renditionKey)`
    - per-item outcomes: `CLEARED`, `SKIPPED`, `FAILED`
- Added request DTO:
  - `RecoveryClearBatchRequestDto`
- Extended job state:
  - `JobState.CLEARED`
- Audit integration:
  - event emitted as `OPS_RECOVERY_CLEAR_BATCH` through existing `auditRecovery(...)`

## 3. Frontend Implementation

### 3.1 Ops recovery service contract

File: `ecm-frontend/src/services/opsRecoveryService.ts`

- Added `clearBatch(payload)` method to call:
  - `POST /ops/recovery/clear-batch`
- Extended typings:
  - `RecoveryJobState` adds `CLEARED`
  - `RecoveryHistoryMode` adds `CLEAR_BATCH`
  - `RecoveryHistoryEventType` adds `OPS_RECOVERY_CLEAR_BATCH`
  - `ClearBatchRequest`

### 3.2 Preview diagnostics integration

File: `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`

- Updated dead-letter clear action source:
  - switched from diagnostics-specific clear API to `opsRecoveryService.clearBatch(...)`
- Updated user feedback:
  - uses response `queued` as cleared-count for unified recovery response shape
- Extended history filter options:
  - Mode: `Clear Batch`
  - Event type: `Clear Batch Event`

### 3.3 Mocked e2e alignment

File: `ecm-frontend/e2e/admin-preview-diagnostics.mock.spec.ts`

- Added mock route:
  - `POST /api/v1/ops/recovery/clear-batch`
- Added call tracking:
  - `deadLetterClearBatchCalls`
- Assertions verify clear-batch requests include selected dead-letter ids.

## 4. Design Notes

- Replay and clear now share the same ops recovery domain contract (`PREVIEW`), reducing split governance APIs.
- Recovery audit timeline can now contain `CLEAR_BATCH` mode records, making remediation operations more traceable.
