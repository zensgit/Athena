# Phase 244 - Preview Dead-letter Clear Governance API + Diagnostics UI (Dev)

Date: 2026-03-10  
Scope: `ecm-core`, `ecm-frontend`

## 1. Goals

1. Add an admin-safe way to clear dead-letter entries without requeueing preview jobs.
2. Expose clear actions in Preview Diagnostics (single entry + batch selection).
3. Keep replay/export flows intact and covered by mocked e2e regression.

## 2. Backend Implementation

File: `ecm-core/src/main/java/com/ecm/core/controller/PreviewDiagnosticsController.java`

- Added endpoint:
  - `POST /api/v1/preview/diagnostics/dead-letter/clear-batch`
- Added clear-batch processor:
  - accepts `entryKeys` and/or `documentIds`
  - deduplicates payload
  - clears entries by `(documentId, renditionKey)`
  - returns per-entry outcome: `CLEARED` / `SKIPPED` / `FAILED`
- Added audit event:
  - `PREVIEW_DEAD_LETTER_CLEARED`
  - details include requested/deduplicated/cleared/failed counts
- Added DTOs:
  - `PreviewDeadLetterClearBatchRequestDto`
  - `PreviewDeadLetterClearBatchResponseDto`
  - `PreviewDeadLetterClearItemDto`

## 3. Frontend Implementation

### 3.1 Service contract

File: `ecm-frontend/src/services/previewDiagnosticsService.ts`

- Added clear-batch types:
  - `PreviewDeadLetterClearBatchItem`
  - `PreviewDeadLetterClearBatchResult`
- Added API method:
  - `clearDeadLetterBatch({ documentIds?, entryKeys? })`
  - calls `POST /preview/diagnostics/dead-letter/clear-batch`

### 3.2 Diagnostics page actions

File: `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`

- Added dead-letter clear handler:
  - batch and single-item clear
  - success/partial/failure toast feedback
  - refreshes diagnostics data after action
- Added UI controls:
  - toolbar button: `Clear Selected`
  - row action button: `Clear dead-letter {entryKey|documentId}`
- Preserved existing `Replay Selected` + per-row `Replay` behavior.

### 3.3 Mocked e2e updates

File: `ecm-frontend/e2e/admin-preview-diagnostics.mock.spec.ts`

- Added mocked route handling for:
  - `POST /api/v1/preview/diagnostics/dead-letter/clear-batch`
- Added interaction coverage:
  - batch clear
  - single-entry clear
- Added call assertions for clear-batch API invocation.

## 4. Design Notes

- Replay and clear are intentionally separated:
  - replay for recoverable failures
  - clear for terminal/noise dead-letter entries (for example unsupported mime patterns)
- Clear operation is idempotent:
  - missing entry returns `SKIPPED` instead of hard failure.
