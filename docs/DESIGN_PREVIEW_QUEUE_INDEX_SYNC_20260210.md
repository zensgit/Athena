# Design: Preview Queue Search Index Sync (2026-02-10)

## Problem
Athena's search UI and preview-status filters rely on the Elasticsearch document fields for `previewStatus`, `previewAvailable`, and `previewFailureReason`.

`PreviewQueueService` updates these fields in the database when a job is enqueued and when processing retries/failures occur, but those state transitions were not guaranteed to be reflected in the search index immediately. This can cause:

- Search facets/totals showing stale preview-status counts.
- Search results showing outdated preview status until another indexing path runs (for example, a later document update) or a full reindex occurs.

## Goal
Keep the search index consistent with preview queue state transitions without making the queue brittle.

## Solution
1. Inject `SearchIndexService` into `PreviewQueueService`.
2. Whenever the queue marks a document as:
   - `PROCESSING` (enqueue and retry scheduling)
   - `FAILED` (final failure)
   …also call `searchIndexService.updateDocument(document)` after `documentRepository.save(document)`.

### Reliability / failure handling
Index updates are wrapped in a nested `try/catch` and logged at `debug` level. Queue state transitions must not fail or crash due to transient indexing issues.

## Scope / Non-goals
- No changes to public HTTP APIs.
- No changes to preview generation logic (supported/unsupported detection remains in `PreviewService`).
- No global reindex.

## Files
- Backend:
  - `ecm-core/src/main/java/com/ecm/core/preview/PreviewQueueService.java`
  - `ecm-core/src/test/java/com/ecm/core/preview/PreviewQueueServiceTest.java`

## Testing Strategy
- Unit: ensure `PreviewQueueServiceTest` compiles and verifies `SearchIndexService.updateDocument(...)` is invoked during a failure path.
- E2E: run `ecm-frontend/e2e/search-preview-status.spec.ts` to validate preview-status filters/totals behavior.

