# Phase367ZV Node Rendition Relation Service Convergence

## Goal

Stop the legacy `/api/v1/nodes/{nodeId}/relations/renditions*` endpoints from rebuilding preview and thumbnail state directly from `Document.preview*` fields inside `NodeController`.

This phase moves those legacy relation surfaces onto the first-class `RenditionResourceService` so Athena's old graph-oriented rendition APIs begin consuming the new rendition resource model instead of a duplicated preview-field projection.

## Why This Slice

- The repository already has first-class rendition APIs under `/api/v1/nodes/{nodeId}/renditions`.
- `NodeController` still carried a second rendition model built inline from `Document.previewStatus`, `previewFailureReason`, `previewLastUpdated`, and thumbnail marker heuristics.
- That duplication meant the same document could expose richer state through `/renditions` but flatter or less accurate state through `/relations/renditions`.
- Converging the old relation surface onto `RenditionResourceService` is a low-conflict way to shrink direct `Document.preview*` consumption without reopening the preview pipeline itself.

## Implementation

### 1. `RenditionResourceService` now produces a reusable summary

Added:

- `listForDocument(Document document)`
- `listForDocument(Document document, String statusFilter)`
- `summarizeDocument(Document document)`
- `RenditionSummary` record

This lets callers that already hold a `Document` avoid refetching the node and obtain:

- effective preview status
- rendition availability
- preview failure reason/category
- preview last-updated timestamp
- current version label

The summary is derived from the synced preview rendition resource, not by re-reading raw preview fields at the controller layer.

### 2. `NodeController` legacy rendition relations now reuse `RenditionResourceService`

Updated endpoints:

- `GET /api/v1/nodes/{nodeId}/relations/renditions`
- `GET /api/v1/nodes/{nodeId}/relations/renditions/{renditionId}`
- `GET /api/v1/nodes/{nodeId}/relations/renditions/summary`
- `GET /api/v1/nodes/{nodeId}/relations/summary`

Key change:

- `NodeController` no longer hand-builds virtual preview/thumbnail relation payloads from `Document.previewStatus`.
- Relation rows are now mapped from synced `RenditionResource` items.
- Relation summary and top-level node relation summary now reuse `RenditionResourceService.RenditionSummary`.

## Behavioral Impact

- Legacy relation endpoints now inherit the same normalized rendition state as the first-class rendition APIs.
- Thumbnail status no longer implicitly mirrors preview readiness when no thumbnail resource actually exists.
- Old relation endpoints become better aligned with Athena's newer rendition model without breaking their public route structure.

## Compatibility

- Route structure is unchanged.
- Response fields remain stable.
- Semantics improve because `status` now reflects the normalized rendition resource state instead of the old inline preview-only heuristic.

## Files

- `ecm-core/src/main/java/com/ecm/core/service/RenditionResourceService.java`
- `ecm-core/src/main/java/com/ecm/core/controller/NodeController.java`
- `ecm-core/src/test/java/com/ecm/core/service/RenditionResourceServiceTest.java`
- `ecm-core/src/test/java/com/ecm/core/controller/NodeControllerRelationsTest.java`
- `ecm-core/src/test/java/com/ecm/core/controller/NodeControllerLockTest.java`
- `ecm-core/src/test/java/com/ecm/core/controller/NodeControllerLockInfoTest.java`

## Follow-up

This does not make `RenditionResource` the full lifecycle source of truth yet.

Remaining higher-value follow-ups are:

1. Migrate search/index preview projection to a rendition-resource-backed summary instead of raw `Document.previewStatus`.
2. Move more operator surfaces from `previewStatus` strings to normalized rendition state.
3. Continue reducing direct controller use of `Document.preview*` in preview diagnostics and related governance views.
