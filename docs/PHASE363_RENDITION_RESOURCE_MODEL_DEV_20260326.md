# Phase 363: Rendition Resource Model

## Goal

Move Athena one step away from `Document.preview*`-driven virtual relations and toward a first-class rendition resource model.

This phase does not yet rewire the entire preview generation pipeline. Instead, it establishes:

- a dedicated rendition resource table
- a dedicated rendition domain entity and repository
- a dedicated rendition service
- dedicated rendition APIs under `/api/v1/nodes/{nodeId}/renditions`

That keeps the cut low-conflict while correcting the model direction.

## Why This Phase Exists

Athena's old state was structurally inconsistent:

- domain model lived on `Document.previewAvailable`, `previewStatus`, `thumbnailId`, and failure fields
- API surface already exposed `/relations/renditions`
- diagnostics talked about rendition resources, but they were still derived from document fields rather than persisted as resources

By contrast, Alfresco's `RenditionsImpl` works with:

- registered rendition definitions
- created and not-created rendition resources
- applicability by mime type
- first-class rendition endpoints instead of ad hoc preview-field projections

This phase starts closing that gap.

## Benchmark Reference

Key Alfresco behaviors that informed this phase:

- `RenditionsImpl#getRenditions(...)` returns both created and registered-but-not-created renditions
- `RenditionsImpl#getRendition(...)` can resolve a specific rendition resource even when it has not yet been created, provided the rendition is registered and applicable
- `createRendition(...)` separates definition lookup, validation, and execution

Athena is not fully there yet, but it now has a concrete resource model to build on.

## Backend Changes

### New entity model

Added:

- `ecm-core/src/main/java/com/ecm/core/entity/RenditionState.java`
- `ecm-core/src/main/java/com/ecm/core/entity/RenditionResource.java`
- `ecm-core/src/main/java/com/ecm/core/repository/RenditionResourceRepository.java`

`RenditionResource` stores:

- `document_id`
- `rendition_key`
- `label`
- `mime_type`
- `state`
- `available`
- `downloadable`
- `content_url`
- `error_reason`
- `error_category`
- `source_status`
- `version_label`
- `source_updated_at`
- `last_synced_at`
- `sort_order`

This is still a mirror model in this phase, but it is now explicit and queryable.

### New service

Added:

- `ecm-core/src/main/java/com/ecm/core/service/RenditionResourceService.java`

Responsibilities:

- load the node through `NodeService` so existing read permission checks still apply
- derive first-class rendition resources for `preview` and `thumbnail`
- persist or refresh mirrored `RenditionResource` rows
- return a stable ordered list of resources

This phase intentionally mirrors from current document fields:

- preview readiness/failure still comes from `Document.preview*`
- thumbnail readiness still comes from `thumbnailId`

That keeps compatibility while preparing the later pipeline migration.

### New first-class API

Added:

- `ecm-core/src/main/java/com/ecm/core/controller/RenditionResourceController.java`

New endpoints:

- `GET /api/v1/nodes/{nodeId}/renditions`
- `GET /api/v1/nodes/{nodeId}/renditions/{renditionKey}`

These endpoints are separate from the existing `/relations/renditions` virtual relation surface, so no current consumer was broken.

## Migration

Added Liquibase changelog:

- `ecm-core/src/main/resources/db/changelog/changes/033-add-rendition-resources-table.xml`

And included it from:

- `ecm-core/src/main/resources/db/changelog/db.changelog-master.xml`

## Design Choice

The important choice in this phase is not "new table for its own sake". The important choice is:

- stop forcing rendition semantics to live only on document fields
- introduce a resource boundary now
- keep old preview fields as the source of truth for one compatibility phase
- migrate generation and governance logic later

That is the lowest-risk route to an Alfresco-style rendition model.

## What This Does Not Yet Solve

This phase still leaves several Alfresco-grade capabilities for later phases:

- rendition definition registry by mime type and size
- create/requeue/invalidate APIs
- version-specific rendition resources
- true not-created vs created semantics driven by generation capabilities instead of field heuristics
- moving preview generation lifecycle off `Document.preview*` and onto `RenditionResource`

## Why This Still Matters

Even with those limits, Athena now improves in three ways:

1. it has a real rendition resource table instead of only virtual relation projections
2. it has a standalone rendition API surface
3. it has a controlled migration path from preview fields to true rendition resources

That is the right platform move if the goal is to surpass Alfresco on product control plane quality rather than just stack more preview diagnostics onto a legacy field model.
