# Phase367ZZW Ops Recovery Rendition Convergence

## Goal

Move the preview recovery control-plane away from raw `Document.previewStatus` and onto the same rendition-backed effective preview semantics already adopted by diagnostics and ordinary node payloads.

## Problem

`OpsRecoveryController` still had a separate preview-status path for high-frequency admin recovery flows:

- `POST /api/v1/ops/recovery/queue-by-window`
- `POST /api/v1/ops/recovery/replay-by-filter`
- `POST /api/v1/ops/recovery/clear-batch`
- `POST /api/v1/ops/recovery/clear-by-filter`
- `POST /api/v1/ops/recovery/dry-run`

The controller still classified failures and emitted result-row `previewStatus` values from:

- raw `Document.previewStatus`
- raw `Document.previewFailureReason`
- queue status values when present, otherwise `null`

That left ops recovery behind the rendition-backed semantics already used elsewhere.

## Design

### Add effective preview helpers in `OpsRecoveryController`

File: `ecm-core/src/main/java/com/ecm/core/controller/OpsRecoveryController.java`

Add controller-local helpers that prefer `renditionResourceService.summarizeDocument(document)` and only fall back to raw document fields when the rendition summary is missing:

- `resolveEffectivePreviewStatus(Document document, String fallbackPreviewStatus)`
- `resolveEffectivePreviewFailureReason(Document document)`
- rendition-backed `resolveFailureCategory(Document document)`

### Use rendition-backed semantics in filter matching

Update `matchesFilters(...)` so category and retryable checks use effective failure category, and reason matching uses the effective failure reason instead of the raw document field.

This makes `queue-by-window`, `clear-by-filter`, and `replay-by-filter` behave consistently when the rendition summary has already normalized an unsupported or otherwise reclassified preview failure.

### Use fallback-aware effective preview status in batch action payloads

Update batch action result DTO construction in:

- `queueDocuments(...)`
- `replayBatchInternal(...)`
- `clearBatchInternal(...)`

Behavior:

1. prefer queue status preview status when the queue service explicitly returns one
2. otherwise prefer rendition-backed effective preview status for the document
3. otherwise fall back to raw `Document.previewStatus`

This prevents queued/replayed result rows from collapsing to `null` when the queue status omits a status but the document already has a normalized rendition summary.

### Use effective semantics in dry-run prediction and samples

Update:

- `evaluateDryRun(...)`
- `evaluateDeadLetterClearDryRun(...)`
- replay dry-run sample mapping
- `predict(...)`

to use effective preview status and effective failure category.

That keeps `UNSUPPORTED` and permanent-failure skip decisions aligned with the rendition-backed model instead of raw legacy status fields.

## Result

Ops recovery is no longer a raw-preview-status island. Filter matching, queue/replay/clear batch action results, and dry-run predictions now all inherit the same rendition-backed effective preview semantics used by the newer diagnostics and node response surfaces.
