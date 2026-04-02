# Phase368ZJ Ops Recovery Mutation Effective Snapshot Convergence

## Goal

Align `OpsRecoveryController` mutation responses with the same effective preview snapshot semantics already used in preview diagnostics and document preview mutation flows.

## Problem

`queue-by-reason`, `queue-by-window`, `replay-by-filter`, and `clear-by-filter` were still assembling `RecoveryBatchItemDto` preview fields inline. That left three inconsistencies:

1. Explicit queue mutation results and rendition-backed snapshot fallback were merged differently from other preview governance surfaces.
2. The same preview contract had to be maintained in multiple controller branches.
3. Legacy `failureCategory` compatibility could drift from richer preview fields during queue mutations.

## Implementation

### Shared batch item builder

Added `buildRecoveryBatchItem(...)` in `OpsRecoveryController` to centralize construction of `RecoveryBatchItemDto` for mutation responses.

### Shared mutation summary resolver

Added `resolvePreviewMutationSummary(Document, PreviewQueueService.PreviewQueueStatus)` with this merge rule:

1. If queue mutation returns an explicit `previewStatus`, treat it as authoritative.
2. Fill `previewFailureReason`, `previewFailureCategory`, and `previewLastUpdated` from queue status when present.
3. Otherwise backfill from rendition-backed `resolveEffectivePreviewSummary(document)`.

### Compatibility for legacy failure category field

`RecoveryBatchItemDto.failureCategory` remains stable for existing consumers:

1. Prefer the mutation summary category when it is explicit.
2. For `queue-by-reason` and `queue-by-window`, if the queue status is explicit but does not carry a category, preserve the request filter category as the fallback compatibility value.
3. Replay/clear mutations continue to rely on rendition-backed snapshot fallback when no queue category is present.

### Mutation paths updated

The shared builder now backs:

- `queueDocuments(...)`
- `replayBatchInternal(...)`
- `clearBatchInternal(...)`

## Result

`OpsRecoveryController` batch mutation responses now follow the same contract shape and precedence rules as the rest of the preview governance surfaces, without regressing the older `failureCategory` field expected by existing tests and clients.
