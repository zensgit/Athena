# Phase 259 - Preview Queue Declined Governance (Dev)

Date: 2026-03-10  
Scope: Stream B (queue governance), aligned with Day2-7 plan

## Goal

Close the `DECLINED` governance loop in preview queue operations:

1. Persist declined queueing decisions from `enqueue(...)` (queue disabled / quiet period / unsupported / permanent failure / prevention block).
2. Expose declined diagnostics via admin API.
3. Provide filter-aligned governance actions: export, requeue, clear.
4. Surface these operations in Preview Diagnostics UI with mocked e2e coverage.

## Backend delivery

### 1) `PreviewQueueService` declined lifecycle

File:
- `ecm-core/src/main/java/com/ecm/core/preview/PreviewQueueService.java`

Implemented:
- Added declined in-memory registry:
  - `declinedItemsByDocument`
  - `declinedItemOrder`
  - bounded by `MAX_DECLINED_HISTORY=2000`
- Added new APIs:
  - `declinedSnapshot(int limit)`
  - `clearDeclined(UUID documentId)`
- Added declined records:
  - `PreviewQueueDeclinedSnapshot`
  - `PreviewQueueDeclinedItem`
- Added decline recording helper flow:
  - `declineEnqueue(...)`
  - `recordDeclined(...)`
  - `trimDeclinedHistory()`
  - `clearDeclinedEntry(...)`
- Hooked declined recording into `enqueue(...)` non-queued branches:
  - queue disabled
  - prevention blocked
  - ready + up-to-date
  - unsupported
  - permanent failure needing `force=true`
  - quiet period (with `nextEligibleAt`)
- Enqueue success now clears stale declined marker for the same document.

### 2) `PreviewDiagnosticsController` declined control plane

File:
- `ecm-core/src/main/java/com/ecm/core/controller/PreviewDiagnosticsController.java`

New endpoints:
- `GET /api/v1/preview/diagnostics/queue/declined`
- `GET /api/v1/preview/diagnostics/queue/declined/export`
- `POST /api/v1/preview/diagnostics/queue/declined/requeue`
- `POST /api/v1/preview/diagnostics/queue/declined/clear`

Capabilities:
- category/query filtering on declined snapshot
- metadata enrichment (`name/path/mimeType/previewStatus`)
- filter-aligned CSV export
- filter-aligned batch governance actions
  - requeue (force toggle)
  - clear declined entries

Audit events added:
- `PREVIEW_QUEUE_DECLINED_EXPORTED`
- `PREVIEW_QUEUE_DECLINED_REQUEUE`
- `PREVIEW_QUEUE_DECLINED_CLEAR`

### 3) Backend tests

Files:
- `ecm-core/src/test/java/com/ecm/core/preview/PreviewQueueServiceTest.java`
- `ecm-core/src/test/java/com/ecm/core/controller/PreviewDiagnosticsControllerSecurityTest.java`

Added coverage:
- queue disabled -> declined tracked + clearable
- quiet period -> declined includes `nextEligibleAt`
- security forbids USER access to new declined endpoints
- ADMIN can query/export/requeue/clear declined items with audit assertions

## Frontend delivery

### 1) Service layer

File:
- `ecm-frontend/src/services/previewDiagnosticsService.ts`

Added models:
- `PreviewQueueDeclinedItem`
- `PreviewQueueDeclinedSummary`
- `PreviewQueueDeclinedRequeueResult`
- `PreviewQueueDeclinedClearResult`

Added API methods:
- `getQueueDeclinedSummary(...)`
- `exportQueueDeclinedCsv(...)`
- `requeueQueueDeclined(...)`
- `clearQueueDeclined(...)`

### 2) Diagnostics UI panel

File:
- `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`

Added new panel: **Preview Queue Declined**

Features:
- summary chips (`Declined`, `Sample`, queue enabled state)
- filters:
  - category filter
  - query filter
- actions:
  - `Requeue Declined`
  - `Clear Declined`
  - `Export Declined CSV`
  - `Refresh Declined`
- result table:
  - document metadata
  - category / reason
  - declined timestamp
  - next eligible time
  - force-required marker

### 3) Mocked e2e integration

File:
- `ecm-frontend/e2e/admin-preview-diagnostics.mock.spec.ts`

Added mocked routes and assertions for:
- declined summary
- declined export
- declined requeue
- declined clear

## Out-of-scope in this phase

- Redis persistence for declined registry (current implementation is memory-governed).
- Cross-node declined synchronization.
- Historical declined trend analytics (time-bucket summaries).

