# Phase367ZZP - Preview Queue Effective Summary Contract

## Goal

Make preview queue responses return rendition-backed effective preview semantics, then consume that contract in high-frequency search surfaces so operators see immediate state changes without waiting for a full refresh.

## Why

Athena had already converged many read and mutation payloads onto `RenditionResourceService.summarizeDocument(...)`, but `POST /api/v1/documents/{id}/preview/queue` still returned the raw queue status contract from `PreviewQueueService`.

That left a visible gap:

- backend queue responses exposed raw queue preview status
- frontend operator surfaces kept showing the old node/search payload until a later refresh

## Design

### 1. Wrap queue responses in a rendition-aware controller contract

File:

- `ecm-core/src/main/java/com/ecm/core/controller/DocumentController.java`

Change `queuePreview(...)` to return `PreviewQueueResponse` with:

- queue fields: `queued`, `attempts`, `nextAttemptAt`, `message`
- effective summary fields:
  - `previewStatus`
  - `previewFailureReason`
  - `previewFailureCategory`
  - `previewLastUpdated`

The controller now:

- enqueues the preview job
- reloads the document
- reads `renditionResourceService.summarizeDocument(document)`
- prefers rendition-backed semantics over the raw queue preview status

### 2. Extend the frontend queue status cache

Files:

- `ecm-frontend/src/services/nodeService.ts`
- `ecm-frontend/src/pages/SearchResults.tsx`
- `ecm-frontend/src/pages/AdvancedSearchPage.tsx`

Changes:

- extend `PreviewQueueStatus` and local queue-status caches with effective preview fields
- when queueing a preview, persist those fields in `previewQueueStatusById`
- use the local override when rendering preview chip metadata and queue detail text

## Result

After this phase, queue actions immediately reflect the effective rendition-backed preview state on ordinary search and advanced search surfaces, instead of waiting for the next full reload.
