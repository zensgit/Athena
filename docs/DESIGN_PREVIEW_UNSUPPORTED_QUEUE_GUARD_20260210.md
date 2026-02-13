# Design: Skip Queueing Unsupported Previews (Unless Forced)

Date: 2026-02-10

## Context
Athena ECM supports preview generation for documents. Some documents are not previewable (for example, `application/octet-stream`), and are classified as `UNSUPPORTED` (or `FAILED` with an "unsupported" failure signal that is treated as unsupported in the UI).

We observed user-facing confusion and wasted work when:
1. A document is `UNSUPPORTED`, but users can still click "Queue preview" (non-forced), which pointlessly flips the document into a processing state and consumes queue capacity.
2. The upload dialog updates an item's `previewStatus` from the queue endpoint response. The backend queue endpoint returns the document's pre-queue status, so the UI can incorrectly stay on `FAILED`/`UNSUPPORTED` even when a queue job was created.

## Goals
- Do not enqueue preview jobs for `UNSUPPORTED` documents unless `force=true`.
- In the upload dialog:
  - Compute preview labels/status based on the effective preview status.
  - Hide the normal "Queue preview" action for unsupported previews; keep "Force rebuild" available.
  - When the queue endpoint reports `queued=true`, optimistically show `PROCESSING`.

## Non-goals
- Adding new preview generators or changing supported MIME types.
- Changing search indexing semantics beyond existing `PreviewStatus` values.
- Changing the queue endpoint response schema.

## Backend Change (Core)
File: `ecm-core/src/main/java/com/ecm/core/preview/PreviewQueueService.java`

- Update `enqueue(documentId, force)` to short-circuit when:
  - `!force && status == READY` (existing behavior)
  - `!force && status == UNSUPPORTED` (new)
- Return `queued=false` in these cases and do not call `markProcessing(...)`.

Rationale: if the system already concluded a preview is unsupported, "retry" is not a meaningful operation; only an explicit admin "force rebuild" should attempt again after configuration changes.

## Frontend Change (Upload Dialog)
File: `ecm-frontend/src/components/dialogs/UploadDialog.tsx`

1. Use `getEffectivePreviewStatus(...)` for:
  - Per-item preview label/color.
  - Uploaded-items status summary counts (adds an `Unsupported` count).
2. Queue actions:
  - Show "Queue preview" only when effective status is not `READY` and not `UNSUPPORTED`.
  - Always show "Force rebuild" when effective status is not `READY`.
3. Fix optimistic state update after queue request:
  - If `queued=true`, set local `previewStatus` to `PROCESSING` (do not trust `previewStatus` returned by the endpoint, which may reflect pre-queue state).

## Tests
Backend unit test:
- `ecm-core/src/test/java/com/ecm/core/preview/PreviewQueueServiceTest.java`
  - Add coverage that `UNSUPPORTED + force=false` returns `queued=false` and does not enqueue / persist any status changes.

End-to-end regression coverage:
- Existing Playwright suites already validate that unsupported previews do not show retry actions in search results and advanced search.

## Acceptance Criteria
- Backend:
  - `PreviewQueueService.enqueue(..., force=false)` does not enqueue for `UNSUPPORTED`.
  - Unit tests pass.
- Frontend:
  - Upload dialog shows "Preview unsupported" and hides "Queue preview" for unsupported items.
  - After clicking "Force rebuild", UI shows processing while the queue is running.
- E2E:
  - Playwright suite passes against the docker prebuilt frontend (`ECM_UI_URL=http://localhost:5500`).

