# Phase 253 - Preview Failure Ledger Lifecycle Reset (Dev)

Date: 2026-03-10  
Scope: `ecm-core`, `ecm-frontend`

## 1. Goal

Implement Stream A (Day2) failure-ledger baseline beyond dead-letter:

1. Persist preview failure ledger fields (`failedAt`, `failureCount`, `lastReason`) on document.
2. Auto-clear stale failure ledger on content/version change.
3. Provide admin diagnostics/list + reset APIs (single + batch).
4. Add diagnostics UI for ledger visibility and reset operations.

## 2. Backend Design & Implementation

### 2.1 Data model

Updated `documents` model with persistent ledger fields:

- `preview_failure_count`
- `preview_failed_at`
- `preview_last_failure_reason`
- `preview_failure_content_hash` (used to detect stale-by-content-change entries)

Files:

- `ecm-core/src/main/java/com/ecm/core/entity/Document.java`
- `ecm-core/src/main/resources/db/changelog/changes/031-add-preview-failure-ledger-columns.xml`
- `ecm-core/src/main/resources/db/changelog/db.changelog-master.xml`

### 2.2 Repository queries

Added ledger query/count methods:

- `findPreviewFailureLedgerEntries(failedSince, pageable)`
- `countPreviewFailureLedgerEntries(failedSince)`

File:

- `ecm-core/src/main/java/com/ecm/core/repository/DocumentRepository.java`

### 2.3 Lifecycle behavior

#### Preview write-path

`PreviewService.updatePreviewStatus` now:

- increments ledger on `FAILED` / `UNSUPPORTED`
- clears ledger on `READY`
- stores normalized `preview_failure_content_hash`

File:

- `ecm-core/src/main/java/com/ecm/core/preview/PreviewService.java`

#### Queue exception path

`PreviewQueueService.markFailed` now writes ledger fields on queue-side terminal failures (e.g. transform exception path).

`PreviewQueueService.enqueue` now auto-clears stale ledger if
`preview_failure_content_hash != content_hash`, and resets stale failed/unsupported status markers before enqueue.

File:

- `ecm-core/src/main/java/com/ecm/core/preview/PreviewQueueService.java`

#### Content/version change reset

`VersionService` now clears preview ledger when content hash changes in:

- `createVersion(...)`
- `promoteVersion(...)`

Also invalidates stale preview status markers to avoid old failure governance blocking new content.

File:

- `ecm-core/src/main/java/com/ecm/core/service/VersionService.java`

### 2.4 Admin APIs

Added endpoints:

- `GET /api/v1/preview/diagnostics/failures/ledger`
- `POST /api/v1/preview/diagnostics/failures/ledger/{documentId}/reset`
- `POST /api/v1/preview/diagnostics/failures/ledger/reset-batch`

Added DTOs:

- diagnostics payload (`PreviewFailureLedgerDiagnosticsDto`)
- ledger item payload (`PreviewFailureLedgerItemDto`)
- reset request/response payloads

Added audit event:

- `PREVIEW_FAILURE_LEDGER_RESET`

File:

- `ecm-core/src/main/java/com/ecm/core/controller/PreviewDiagnosticsController.java`

## 3. Frontend Design & Implementation

### 3.1 Service contract

`previewDiagnosticsService` now supports:

- `getFailureLedger(limit, days)`
- `resetFailureLedger(documentId)`
- `resetFailureLedgerBatch(documentIds)`

Added corresponding TypeScript types.

File:

- `ecm-frontend/src/services/previewDiagnosticsService.ts`

### 3.2 Preview Diagnostics UI

Added **Preview Failure Ledger** panel:

- sampled/total chips
- window chip
- filter input
- row-level reset
- batch selection + `Reset Selected`
- stale-hash state chip (`Current` / `Stale by content hash`)

File:

- `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`

## 4. Test updates

Backend:

- `PreviewDiagnosticsControllerSecurityTest`
  - role-gating for new ledger endpoints
  - admin access + payload checks
  - batch reset behavior check
- `PreviewQueueServiceTest`
  - queue exception path writes ledger fields
  - stale ledger auto-clear on content hash change

Frontend:

- `admin-preview-diagnostics.mock.spec.ts`
  - ledger list mock route
  - single reset + batch reset flow
  - API payload assertions

