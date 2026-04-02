# Phase368ZA: Shared Effective Preview Snapshot Service Convergence

## Context

Athena already had multiple places that needed the same effective preview snapshot semantics:

- `DocumentController` used `RenditionResourceService.resolvePreviewMutationSummary(...)` for preview mutations.
- `PreviewDiagnosticsController` kept a private `resolveEffectivePreviewSnapshot(...)` with its own fallback logic.
- `OpsRecoveryController` kept a private `resolveEffectivePreviewSummary(...)` plus separate status/reason helpers.

That meant the `preview / rendition / ops governance` line still had a backend source-of-truth seam: controller-local fallback logic was reimplemented in multiple places instead of being owned by the rendition layer.

## Goal

Move effective preview snapshot resolution into `RenditionResourceService`, then make `PreviewDiagnosticsController` and `OpsRecoveryController` delegate to it.

The intended outcome is:

- one shared service-level resolver for `previewStatus / previewFailureReason / previewFailureCategory / previewLastUpdated`
- controller contracts unchanged
- less controller-local preview semantics drift

## Implementation

### 1. Added shared snapshot resolver to `RenditionResourceService`

File:

- `ecm-core/src/main/java/com/ecm/core/service/RenditionResourceService.java`

Added:

- `resolveEffectivePreviewSnapshot(Document, fallbackStatus, fallbackReason, fallbackCategory, fallbackLastUpdated)`
- new record `EffectivePreviewSnapshot`

Resolution order:

1. Prefer rendition-backed `summarizeDocument(...)`
2. If rendition summary is unavailable but a document exists, fall back to document-level effective semantics via `PreviewStatusSemantics` + `PreviewFailureClassifier`
3. If no document exists, use explicit fallback values

The resolver normalizes:

- `previewStatus` to uppercase when present
- `previewFailureReason` to trimmed text
- `previewFailureCategory` to uppercase when present

### 2. `PreviewDiagnosticsController` now delegates to the shared resolver

File:

- `ecm-core/src/main/java/com/ecm/core/controller/PreviewDiagnosticsController.java`

Changed:

- private `resolveEffectivePreviewSnapshot(...)` now first delegates to `renditionResourceService.resolveEffectivePreviewSnapshot(...)`
- existing local fallback remains as defensive backup if the service mock returns `null`
- `resolveEffectivePreviewStatus(...)` now preserves the old document-semantics fallback path when the shared snapshot is absent

This keeps existing controller behavior stable while moving production ownership of snapshot semantics into the service layer.

### 3. `OpsRecoveryController` now delegates to the shared resolver

File:

- `ecm-core/src/main/java/com/ecm/core/controller/OpsRecoveryController.java`

Changed:

- `resolveEffectivePreviewSummary(...)` now first uses the shared service snapshot
- `resolveEffectivePreviewStatus(...)` and `resolveEffectivePreviewFailureReason(...)` now also consult the shared service snapshot first
- enum mapping from normalized preview failure category was isolated into `toFailureCategory(...)`

This removes another copy of rendition/document fallback logic from the controller.

## Tests Added / Updated

### `RenditionResourceServiceTest`

Updated file:

- `ecm-core/src/test/java/com/ecm/core/service/RenditionResourceServiceTest.java`

Added focused coverage for:

- document-semantics fallback when rendition summary is unavailable
- explicit fallback payload handling when no document exists

### `PreviewDiagnosticsControllerSecurityTest`

Updated file:

- `ecm-core/src/test/java/com/ecm/core/controller/PreviewDiagnosticsControllerSecurityTest.java`

Updated the focused requeue/dry-run fallback case to stub the new shared service resolver directly instead of stubbing only `summarizeDocument(...)`.

### `OpsRecoveryControllerSecurityTest`

Updated file:

- `ecm-core/src/test/java/com/ecm/core/controller/OpsRecoveryControllerSecurityTest.java`

Updated the history preview-summary case to stub the new shared service resolver directly.

## Why this slice matters

This is not a user-facing feature spike. It is a source-of-truth cleanup on the preview governance line.

The value is:

- fewer controller-local preview fallback implementations
- easier future convergence for preview queue / diagnostics / recovery
- a clearer backend owner for effective preview snapshot semantics

## Follow-up

The next highest-value continuation on this line is still:

- shared preview lifecycle writer convergence across queue / repair / requeue / invalidate / diagnostics / recovery

This phase reduces read-side duplication first, making that next write-side unification simpler.
