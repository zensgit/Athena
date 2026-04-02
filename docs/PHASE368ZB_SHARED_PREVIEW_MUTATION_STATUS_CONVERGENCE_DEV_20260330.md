# Phase368ZB: Shared Preview Mutation Status Convergence

## Context

Athena already had shared preview summary resolution, but mutation responses still exposed a write-side seam:

- `DocumentController` built `preview/queue` and `preview/repair` responses by combining queue fields with `RenditionSummary` inline.
- `RenditionResourceController` built rendition mutation envelopes with a separate queue-status mapper that still read raw queue status fields directly.

That meant two operator surfaces on the same preview lifecycle could drift:

- `DocumentController` mutation responses
- `RenditionResourceController` rendition mutation responses

## Goal

Introduce one shared service-level mutation contract so preview mutation responses across controllers use the same effective preview status composition.

## Implementation

### 1. Added shared `PreviewMutationStatus` to `RenditionResourceService`

File:

- `ecm-core/src/main/java/com/ecm/core/service/RenditionResourceService.java`

Added:

- `resolvePreviewMutationStatus(Document, PreviewQueueStatus)`
- `resolvePreviewMutationStatus(RenditionSummary, PreviewQueueStatus)`
- `PreviewMutationStatus` record

This contract centralizes:

- `documentId`
- `previewStatus`
- `previewFailureReason`
- `previewFailureCategory`
- `previewLastUpdated`
- `queued`
- `attempts`
- `nextAttemptAt`
- `message`

Precedence rules:

1. Prefer `RenditionSummary` effective preview fields
2. Fall back to raw queue status fields only when preview summary is absent
3. Always keep queue lifecycle fields from `PreviewQueueStatus`

### 2. `DocumentController` now consumes the shared mutation contract

File:

- `ecm-core/src/main/java/com/ecm/core/controller/DocumentController.java`

Changed:

- `repairPreview(...)`
- `queuePreview(...)`

These endpoints no longer manually stitch queue fields and preview summary fields together. They now delegate to `renditionResourceService.resolvePreviewMutationStatus(...)`.

This keeps the external API shape unchanged while removing controller-local composition logic.

### 3. `RenditionResourceController` queue-status envelope now uses the shared mutation contract

File:

- `ecm-core/src/main/java/com/ecm/core/controller/RenditionResourceController.java`

Changed:

- `toMutationResponse(...)` is now instance-based instead of static
- nested `queueStatus.previewStatus` now comes from shared mutation status resolution instead of raw queue status directly

This matters because rendition mutation responses now align with document preview mutation responses on effective preview semantics.

## Tests

### `RenditionResourceServiceTest`

Added focused coverage for:

- preview summary precedence over raw queue status
- queue-status fallback when preview summary is empty

### `DocumentControllerPreviewRepairTest`

Updated focused response tests so document preview mutation endpoints now stub the shared mutation contract directly.

### `RenditionResourceControllerTest`

Updated mutation envelope tests to validate the queue-status branch through the shared mutation contract.

## Outcome

This phase does not change endpoint shapes. It reduces mutation-response drift.

After this phase:

- document preview mutation endpoints and rendition mutation endpoints both consume the same service-owned effective preview mutation contract
- queue-status preview fields are no longer controller-specific composition
- future lifecycle-writer convergence can build on a narrower surface
