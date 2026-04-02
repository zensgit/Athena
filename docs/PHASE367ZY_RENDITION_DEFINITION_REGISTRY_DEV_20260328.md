# Phase367ZY Rendition Definition Registry

## Goal

Move Athena's first-class rendition API from heuristic-only state mirroring toward an explicit definition registry.

This phase adds:

- a registry-backed definition layer for built-in renditions
- persisted rendition definition metadata on `rendition_resources`
- a dedicated `/api/v1/nodes/{nodeId}/renditions/definitions` surface
- richer rendition responses with generation/dependency metadata

## Why This Slice

Phase367ZX separated "not created" from "not applicable", but the applicability rules still lived only inside sync heuristics.

That left two structural gaps:

1. Athena still had no explicit definition contract for registered renditions
2. operators could inspect live rendition resources, but not the registered definition set that produced them

This phase addresses both gaps without yet broadening into a full cross-mime rendition plugin framework.

## Implementation

### 1. Added a bounded rendition definition registry

`RenditionDefinitionRegistry` now owns the current built-in definitions:

- `preview`
- `thumbnail`

Each definition evaluation exposes:

- `renditionKey`
- `label`
- `targetMimeType`
- `generationMode`
- `downloadable`
- `sortOrder`
- `dependencyRenditionKey`
- `registered`
- `applicable`
- `applicabilityReason`

Current generation modes are intentionally bounded:

- `PREVIEW_PIPELINE`
- `PREVIEW_DERIVED`

### 2. Sync now copies registry metadata into rendition resources

`RenditionResourceSyncService` now uses registry evaluations instead of hardcoded preview/thumbnail metadata.

Each synced `RenditionResource` now persists:

- `generationMode`
- `dependencyRenditionKey`
- `applicable`
- `applicabilityReason`

Database migration:

- `037-add-rendition-resource-definition-columns.xml`

This means resource rows carry both live state and stable definition metadata.

### 3. Service layer now exposes registry-backed definition status

`RenditionResourceService` now has:

- `listDefinitionsForNode(...)`
- `listDefinitionsForDocument(...)`

These join:

- registry evaluations
- synced rendition resources

The result is a definition status view that includes both:

- definition metadata
- current resource state / availability / content URL

### 4. Controller now exposes a definition registry surface

`RenditionResourceController` now provides:

- `GET /api/v1/nodes/{nodeId}/renditions/definitions`

This returns the registered rendition set for the node, including applicability and the current state if a synced resource exists.

Existing `/renditions` and `/renditions/{renditionKey}` responses also now expose:

- `generationMode`
- `dependencyRenditionKey`

## Files

- `ecm-core/src/main/java/com/ecm/core/service/RenditionDefinitionRegistry.java`
- `ecm-core/src/main/java/com/ecm/core/service/RenditionResourceSyncService.java`
- `ecm-core/src/main/java/com/ecm/core/service/RenditionResourceService.java`
- `ecm-core/src/main/java/com/ecm/core/controller/RenditionResourceController.java`
- `ecm-core/src/main/java/com/ecm/core/entity/RenditionResource.java`
- `ecm-core/src/main/resources/db/changelog/changes/037-add-rendition-resource-definition-columns.xml`
- `ecm-core/src/main/resources/db/changelog/db.changelog-master.xml`
- `ecm-core/src/test/java/com/ecm/core/service/RenditionResourceSyncServiceTest.java`
- `ecm-core/src/test/java/com/ecm/core/service/RenditionResourceServiceTest.java`
- `ecm-core/src/test/java/com/ecm/core/controller/RenditionResourceControllerTest.java`

## Operator Impact

Operators can now distinguish three different layers:

- the rendition definition that is registered for this content
- whether that definition is applicable
- the current lifecycle state of the synced resource

This is materially closer to Alfresco's rendition-definition semantics than Athena's earlier preview-field mirror.

## Residual Gap

Athena now has a bounded definition registry, but it is still not yet a full dynamic rendition definition system.

The next structural steps are:

- move more consumer logic onto definition-backed semantics
- migrate remaining search/index preview projections off raw `Document.preview*`
- eventually make rendition resources the lifecycle source of truth instead of a synchronized mirror
