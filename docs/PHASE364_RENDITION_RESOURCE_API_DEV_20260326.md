# Phase 364: Rendition Resource API

## Goal

Continue the `Phase363` rendition-resource migration by making the new resource API operational instead of read-only.

## Delivered

- Extended `GET /api/v1/nodes/{nodeId}/renditions` with Alfresco-style collection filtering via `status=CREATED|NOT_CREATED`.
- Added `POST /api/v1/nodes/{nodeId}/renditions/{renditionKey}/requeue`.
- Added `POST /api/v1/nodes/{nodeId}/renditions/{renditionKey}/invalidate`.
- Added `RenditionResourceSyncService` so preview lifecycle writes now mirror into `rendition_resources` during queue/status transitions.
- Kept legacy `NodeController` virtual rendition relations untouched for compatibility.

## Behaviour

- `CREATED` currently maps to rendition resources whose state is not `REGISTERED`.
- `NOT_CREATED` currently maps to rendition resources still in `REGISTERED`.
- `preview` and `thumbnail` are treated as preview-linked rendition resources.
- `requeue` reuses the existing `PreviewQueueService.enqueue(...)` pipeline.
- `invalidate` reuses existing preview repair semantics:
  - `preview` invalidation calls `PreviewService.invalidateRendition(...)` and clears the thumbnail marker.
  - `thumbnail` invalidation clears the thumbnail marker without forcing preview failure state.
- Optional requeue after invalidation is supported through `requeue=true`.
- `PreviewService.updatePreviewStatus(...)` and `PreviewQueueService` state transitions now call the sync service after persisting document state, so the new resource model starts receiving live lifecycle updates instead of only on read.

## Compatibility

- Old `/api/v1/nodes/{nodeId}/relations/renditions*` APIs are unchanged.
- `Document.preview*` remains the source-of-truth lifecycle for this slice, but `RenditionResource` now updates during lifecycle writes instead of only during read-time synchronization.

## Follow-up

- Move preview generation lifecycle ownership from `Document.preview*` into `RenditionResource`.
- Add explicit rendition registration/applicability metadata similar to Alfresco registered definitions.
- Add richer mutation affordances such as `create` and stale/version-aware filtering.
