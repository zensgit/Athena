# Phase 240 - Preview Dead-Letter Rendition Governance + Content-Hash Binding (Dev)

Date: 2026-03-10  
Scope: `ecm-core` + `ecm-frontend`

## 1. Goals

This phase delivers three benchmark-parity hardening capabilities in one slice:

1. Dead-letter ledger moves from document-only to `(document, rendition)` granularity.
2. Preview queue dedup moves to governance-key dedup (`document + rendition + contentHash`) to avoid stale duplicate jobs.
3. READY-skipping behavior is bound to `contentHash` validity, so stale READY records are no longer trusted blindly.

## 2. Backend Design

## 2.1 Dead-letter ledger: per `(node, rendition)`

File: `ecm-core/src/main/java/com/ecm/core/preview/PreviewDeadLetterRegistry.java`

- Introduced `renditionKey` and `entryKey` (`{documentId}|{renditionKey}`).
- Registry storage is keyed by `entryKey` (memory + Redis).
- Added compatibility overloads for existing callers:
  - `record(documentId, reason, ...)` still works and defaults to rendition `preview`.
  - New primary API: `record(documentId, renditionKey, reason, ...)`.
- Added selective operations:
  - `remove(documentId, renditionKey)`
  - `removeByEntryKey(entryKey)`
  - `findByEntryKey(entryKey)`
  - `markReplayAttempt(documentId, renditionKey, at)`
  - `markReplayAttemptByEntryKey(entryKey, at)`
- Dead-letter payload now carries:
  - `entryKey`, `documentId`, `renditionKey`, `reason`, `category`, `policyKey`, `sourceStage`, `attempts`, `occurrences`, `lastReplayAt`, `replayCount`.

## 2.2 Queue dedup governance key

File: `ecm-core/src/main/java/com/ecm/core/preview/PreviewQueueService.java`

- Added governance key: `{documentId}|preview|{contentHash-or-unknown}`.
- Memory queue dedup now uses governance key, with active mapping per document:
  - same governance key => “already queued”
  - different governance key => stale queued job evicted before enqueuing new one
- Stale polled jobs are skipped if their governance key no longer matches current document content.
- Added Redis governance side-map:
  - hash key: `ecm:queue:preview:governance`
  - redis queued jobs are validated against current governance key before execution.

## 2.3 Content-hash-bound rendition validity

Files:
- `ecm-core/src/main/java/com/ecm/core/entity/Document.java`
- `ecm-core/src/main/java/com/ecm/core/preview/PreviewService.java`
- `ecm-core/src/main/resources/db/changelog/changes/030-add-preview-content-hash-column.xml`
- `ecm-core/src/main/resources/db/changelog/db.changelog-master.xml`

Changes:

- Added `documents.preview_content_hash` and entity field `previewContentHash`.
- `PreviewService` now persists `previewContentHash = document.contentHash` when preview status becomes `READY`.
- Queue skip rule for READY now checks hash validity:
  - status READY + hash match => skip as up-to-date
  - status READY + hash mismatch/missing binding => allowed to requeue.
- Queue state transitions (`PROCESSING`/`FAILED`) clear `previewContentHash`.

## 2.4 API contract upgrades

File: `ecm-core/src/main/java/com/ecm/core/controller/PreviewDiagnosticsController.java`

- Dead-letter DTO adds `entryKey` + `renditionKey`.
- Dead-letter CSV export includes `entryKey` and `renditionKey`.
- Replay batch request now supports `entryKeys` in addition to legacy `documentIds`.

File: `ecm-core/src/main/java/com/ecm/core/controller/OpsRecoveryController.java`

- Recovery replay request now supports `entryKeys` (alongside `documentIds`).
- Replay pipeline resolves entry key -> `(documentId, renditionKey)`, and:
  - removes precise dead-letter tuple on queued success
  - records replay attempt on skipped outcome.

## 3. Frontend Adaptation

Files:
- `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`
- `ecm-frontend/src/services/previewDiagnosticsService.ts`
- `ecm-frontend/src/services/opsRecoveryService.ts`
- `ecm-frontend/e2e/admin-preview-diagnostics.mock.spec.ts`

Changes:

- Dead-letter selection is now keyed by `entryKey` (fallback to `documentId` for backward compatibility).
- Replay batch in UI sends `entryKeys`.
- Dead-letter table now surfaces rendition hint under policy column.
- Service types expanded for `entryKey`/`renditionKey` and replay payload `entryKeys`.
- Mock route handling updated to accept either `documentIds` or `entryKeys`.

## 4. Compatibility Notes

- Existing clients sending `documentIds` continue to work.
- Dead-letter old Redis payloads are tolerated via compatibility parsing path where possible.
- New fields are additive in API responses.
