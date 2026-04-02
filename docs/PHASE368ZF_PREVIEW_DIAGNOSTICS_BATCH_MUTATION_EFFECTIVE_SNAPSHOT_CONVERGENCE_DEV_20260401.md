# Phase368ZF Preview Diagnostics Mutation Effective Snapshot Convergence

## Goal

Unify `PreviewDiagnostics` mutation responses so both batch actions and declined requeue actions no longer return raw `PreviewQueueStatus` fields directly when queue status is sparse.

## Problem

`PreviewDiagnosticsController` still had write-side seams in two clusters:

- `queueFailuresInternal(...)` wrote `previewStatus / previewFailureReason / previewFailureCategory / previewLastUpdated` straight from `PreviewQueueStatus`
- `processDeadLetterReplayBatch(...)` did the same
- `requeueDeclinedQueueTasks(...)` and `requeueDeclinedQueueTasksDryRun(...)` each reimplemented their own queue-status-plus-snapshot merge
- when queue status omitted effective preview detail, batch responses lost the richer rendition-backed semantics already used elsewhere
- when queue status explicitly advanced to a new state such as `PROCESSING`, old document failure detail could not be allowed to overwrite that fresh mutation status

## Design

Add shared controller-local mutation builders that merge:

1. explicit mutation state from `PreviewQueueStatus`
2. shared effective preview snapshot from `RenditionResourceService.resolveEffectivePreviewSnapshot(...)`

Merge rule:

- if `PreviewQueueStatus.previewStatus` is present, it is authoritative for batch mutation response
- when queue status does not provide a preview status, batch responses fall back to the shared effective preview snapshot
- `previewLastUpdated` still prefers queue status timestamp when present
- attempts and next-attempt metadata continue to come from queue status

This preserves immediate mutation feedback while still filling sparse queue responses with rendition-backed effective preview detail.

## Files

- `ecm-core/src/main/java/com/ecm/core/controller/PreviewDiagnosticsController.java`
- `ecm-core/src/test/java/com/ecm/core/controller/PreviewDiagnosticsControllerSecurityTest.java`

## Result

- `failures/queue-batch` now loads document context and emits effective preview detail when queue status is sparse
- `failures/queue-by-reason` inherits the same behavior through `queueFailuresInternal(...)`
- `dead-letter/replay-batch` now emits the same merged mutation contract
- `queue/declined/requeue` and `queue/declined/requeue/dry-run` now use the same merge rule instead of bespoke controller-local assembly
- stale document failure detail no longer overwrites an explicit queued `PROCESSING` mutation state
