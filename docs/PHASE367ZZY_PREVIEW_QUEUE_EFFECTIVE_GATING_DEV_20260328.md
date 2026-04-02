# Phase367ZZY Preview Queue Effective Gating

## Goal

Move preview queue admission and dry-run gating from raw `Document.previewStatus` semantics to the same effective preview semantics already used by rendition-backed node, diagnostics, and search surfaces.

## Problem

`PreviewQueueService` still evaluated enqueue eligibility from raw document fields:

- `Document.previewStatus`
- `Document.previewFailureReason`

That left a gap where effective unsupported cases could still be treated as queueable:

- generic binary sources with no raw preview status
- documents whose effective preview state had already been normalized to `UNSUPPORTED`

The queue service is the actual lifecycle gate, so this gap mattered more than a UI-only mismatch.

## Design

### Base enqueue evaluation on effective preview status

File:

- `ecm-core/src/main/java/com/ecm/core/preview/PreviewQueueService.java`

Update:

- `enqueue(...)`
- `evaluateEnqueue(...)`
- `resolveEffectivePreviewStatusForEvaluation(...)`
- `resolveEffectiveFailureReasonForEvaluation(...)`

to use `PreviewStatusSemantics` instead of only raw document fields.

Behavior:

1. prefer effective preview status from `PreviewStatusSemantics.resolveEffectiveStatus(document)`
2. prefer effective failure reason from `PreviewStatusSemantics.resolveEffectiveFailureReason(document)`
3. preserve existing stale-failure-ledger clearing semantics by still nulling failed/unsupported status when the stored failure ledger is stale

### Add effective failure category helper for queue gating

Add `resolveEffectiveFailureCategoryForEvaluation(document)` and use it for:

- unsupported gating
- permanent failure gating
- dry-run decisions

This keeps queue admission aligned with the same status/reason/category semantics already used elsewhere.

### Keep public response shape stable

No DTO shapes changed.

`PreviewQueueStatus.previewStatus` and declined snapshot items still return the same enum field, but now that field can reflect effective unsupported semantics even when raw document status was missing.

## Result

Preview queue admission is no longer a raw-preview-status island. Generic binary and other effectively unsupported documents are now blocked consistently in both enqueue and dry-run evaluation paths, without waiting for downstream controller-level reinterpretation.
