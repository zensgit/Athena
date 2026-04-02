# Phase367ZZZ Rendition Sync Effective Preview Semantics

## Goal

Move rendition resource snapshot generation away from raw `Document.preview*` fields and toward effective preview semantics so the rendition model itself becomes a stronger lifecycle source.

## Problem

`RenditionResourceSyncService` still built preview rendition snapshots from raw document fields:

- raw `Document.previewStatus`
- raw `Document.previewFailureReason`
- category reclassification from raw values

That left a structural gap:

- generic binary sources could still sync as `REGISTERED` preview resources
- preview summaries could still lag behind the effective unsupported semantics already exposed elsewhere
- the rendition model was still mirroring stale document fields instead of carrying the normalized preview interpretation itself

## Design

### Build preview snapshot status/reason/category from effective semantics

File:

- `ecm-core/src/main/java/com/ecm/core/service/RenditionResourceSyncService.java`

Update preview snapshot construction to use:

- `PreviewStatusSemantics.resolveEffectiveStatus(document)`
- `PreviewStatusSemantics.resolveEffectiveFailureReason(document)`

and classify category from those effective values.

### Let preview rendition state reflect effective unsupported semantics

`derivePreviewState(...)` now uses the effective preview status rather than raw `Document.previewStatus`.

Result:

- effective `UNSUPPORTED` preview semantics now produce `RenditionState.UNSUPPORTED`
- effective `FAILED` preview semantics remain `RenditionState.FAILED`
- applicable-but-unscheduled previews remain `REGISTERED`

### Keep thumbnail behavior conservative but source-aware

Thumbnail remains `REGISTERED` when its own definition is not applicable, but now inherits the preview rendition’s effective `sourceStatus`.

That preserves current low-risk derivative behavior while still surfacing better lineage context to operator surfaces.

### Expose unsupported semantics through rendition summary

Because `RenditionResourceService.summarizeDocument(...)` reads the preview rendition resource first, this change automatically improves:

- rendition summary
- node relation summary
- preview surfaces that consume rendition summary
- registry/operator surfaces backed by the preview rendition resource

## Result

The rendition sync layer now carries effective preview semantics itself. Generic binary and other unsupported preview cases no longer appear as merely registered preview renditions; the preview resource now encodes `UNSUPPORTED` directly, which is a real step toward making `RenditionResource` the lifecycle source of truth instead of a thin mirror of raw document fields.
