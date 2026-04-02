# Phase367ZX Rendition Applicability And State Filters

## Goal

Make Athena's first-class rendition resource API behave more like a real rendition registry instead of a thin preview-field mirror.

This phase adds:

- explicit applicability metadata per rendition resource
- explicit applicability reason text
- exact rendition `state` filtering
- tighter compatibility semantics for legacy `status=CREATED|NOT_CREATED`

## Why This Slice

Athena already had stronger operator mutation surfaces than the reference implementation, but its live-node rendition collection still had two semantic gaps:

1. it could not distinguish "registered but not applicable" from "registered but not created yet"
2. it overloaded `status=CREATED|NOT_CREATED` instead of exposing exact rendition state filtering

Those gaps matter for operator detail and for long-term registry-backed rendition semantics.

## Implementation

### 1. Rendition resources now carry applicability metadata

`RenditionResource` now includes:

- `applicable`
- `applicabilityReason`

Database migration:

- `036-add-rendition-resource-applicability-columns.xml`

These fields are surfaced through `/api/v1/nodes/{nodeId}/renditions` and `/api/v1/nodes/{nodeId}/renditions/{renditionKey}`.

### 2. Sync service now derives applicability separately from state

`RenditionResourceSyncService` now computes:

- preview applicability
- thumbnail applicability

Current bounded rules:

- generic binary / empty source mime types are treated as preview-not-applicable
- thumbnail becomes not-applicable when no preview-eligible source exists and no thumbnail asset exists

This is intentionally still a small heuristic layer, not a full rendition-definition registry yet.

### 3. Exact `state` filtering is now supported

`GET /api/v1/nodes/{nodeId}/renditions` now accepts:

- `status=CREATED|NOT_CREATED`
- `state=REGISTERED|PROCESSING|READY|FAILED|UNSUPPORTED|STALE`

`state` is exact and can be comma-separated.

Examples:

- `?state=READY`
- `?status=NOT_CREATED`
- `?status=CREATED&state=FAILED,UNSUPPORTED`

### 4. Compatibility semantics for `NOT_CREATED` are stricter

Legacy alias semantics now exclude not-applicable resources:

- `CREATED` means applicable and not `REGISTERED`
- `NOT_CREATED` means applicable and `REGISTERED`

This keeps the old filter useful while avoiding the false interpretation that unsupported/generic-binary resources are merely waiting to be generated.

## Files

- `ecm-core/src/main/java/com/ecm/core/entity/RenditionResource.java`
- `ecm-core/src/main/java/com/ecm/core/service/RenditionResourceSyncService.java`
- `ecm-core/src/main/java/com/ecm/core/service/RenditionResourceService.java`
- `ecm-core/src/main/java/com/ecm/core/controller/RenditionResourceController.java`
- `ecm-core/src/main/resources/db/changelog/changes/036-add-rendition-resource-applicability-columns.xml`
- `ecm-core/src/main/resources/db/changelog/db.changelog-master.xml`
- `ecm-core/src/test/java/com/ecm/core/service/RenditionResourceServiceTest.java`
- `ecm-core/src/test/java/com/ecm/core/controller/RenditionResourceControllerTest.java`

## Operator Impact

Operators can now tell apart:

- applicable but not yet created
- not applicable for this source content
- ready
- processing
- failed
- unsupported
- stale

That is a meaningful step toward surpassing the reference implementation on live-node rendition detail, even before Athena introduces a full rendition-definition registry.

## Follow-up

The next structural step is still a true rendition-definition/applicability registry keyed by source content characteristics, not just heuristics inside the sync service.
