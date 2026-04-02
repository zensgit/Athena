# Phase156 Dev: Preview Reason-Scope Batch Queue

## Date
2026-03-06

## Goal
Remove the current-list-only limitation for top-reason actions in Preview Diagnostics. Operators should be able to queue retries/rebuilds by reason across the selected diagnostics window.

## Borrowed design cues from Alfresco
- Failover/recovery should be server-driven, not UI loop-driven (`LocalFailoverTransform`).
- Error handling should expose grouped, actionable outcomes.

## Backend changes
- File: `ecm-core/src/main/java/com/ecm/core/repository/DocumentRepository.java`
  - Added windowed reason query/count:
    - `findPreviewFailuresByReasonAndWindow(...)`
    - `countPreviewFailuresByReasonAndWindow(...)`
- File: `ecm-core/src/main/java/com/ecm/core/controller/PreviewDiagnosticsController.java`
  - Added endpoint: `POST /api/v1/preview/diagnostics/failures/queue-by-reason`
  - Added bounded selection strategy:
    - max batch docs clamp: `1..500`
    - scan guardrail: `2000`
  - Added new request/response DTOs:
    - `PreviewReasonBatchQueueRequestDto`
    - `PreviewReasonBatchQueueResponseDto`
  - Refactored batch enqueue logic into shared helper `queueFailuresInternal(...)`.

## Frontend changes
- File: `ecm-frontend/src/services/previewDiagnosticsService.ts`
  - Added `queueFailuresByReason(...)` API method and types.
- File: `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`
  - Top-reason Retry/Force now calls backend reason-scope endpoint.
  - Action no longer requires `Current List > 0`; it can target full reason-window scope.
  - Toast now reports matched/queued/skipped/failed.
- File: `ecm-frontend/e2e/admin-preview-diagnostics.mock.spec.ts`
  - Added `queue-by-reason` route mock and assertions for reason/day payload.

## Behavior summary
- Same UX entry point (Top reasons table buttons), but execution scope is broader and safer:
  - server deduplication
  - bounded scan/batch guards
  - aggregated outcome report
