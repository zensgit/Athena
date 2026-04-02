# PHASE368D Ops Recovery Batch Effective Summary Convergence

## Goal

Continue the `preview / rendition / search` source-of-truth line by removing another admin recovery fork:

- `OpsRecoveryController` batch queue, replay, and clear actions should return the same effective preview summary fields already used by document queue flows and preview diagnostics.
- `PreviewDiagnosticsPage` should consume those richer batch results in its operator feedback instead of collapsing everything down to `queued/skipped/failed`.

## Why This Phase

Before this change, `OpsRecoveryController` batch actions returned only:

- `previewStatus`
- `failureCategory`
- `attempts`
- `nextAttemptAt`

That left three visible gaps:

- `previewFailureReason` was lost even when rendition-backed semantics had already normalized it.
- `previewFailureCategory` was not exposed as an explicit preview field, which kept batch results weaker than the rest of the preview control-plane.
- `previewLastUpdated` was dropped, so operators could not tell whether a queue/replay/clear result was reflecting stale document state or a newer rendition-backed update.

This made `ops recovery` batch endpoints lag behind:

- `preview diagnostics` batch actions
- document queue / repair APIs
- local preview queue override flows in search and diagnostics

## Scope

### Backend

Extended `RecoveryBatchItemDto` in `OpsRecoveryController` to include:

- `previewFailureReason`
- `previewFailureCategory`
- `previewLastUpdated`

Applied that richer contract consistently to:

- `queueDocuments(...)`
- `replayBatchInternal(...)`
- `clearBatchInternal(...)`

Instead of assembling batch results from raw `PreviewQueueStatus.previewStatus()` plus a separate category guess, the controller now builds a single effective preview summary and reuses it across all three batch paths.

That summary prefers:

- rendition-backed status
- rendition-backed failure reason
- rendition-backed failure category
- rendition-backed preview last updated

and only then falls back to raw `Document.preview*`.

### Frontend

Extended `RecoveryBatchItem` in `opsRecoveryService.ts` to preserve the richer contract.

`PreviewDiagnosticsPage.tsx` now consumes that richer batch result in its toast feedback for:

- reason-scoped queue
- reason-scoped clear
- reason-scoped replay
- dead-letter replay batch
- dead-letter clear batch
- dry-run execute recovery

This does not yet add a dedicated batch result table, but it does stop throwing away effective preview detail at the operator surface that already exists.

## Files

### Backend

- `ecm-core/src/main/java/com/ecm/core/controller/OpsRecoveryController.java`
- `ecm-core/src/test/java/com/ecm/core/controller/OpsRecoveryControllerSecurityTest.java`

### Frontend

- `ecm-frontend/src/services/opsRecoveryService.ts`
- `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`

## Outcome

Athena’s recovery control-plane now treats batch queue/replay/clear results as first-class effective preview mutations rather than as a reduced side channel.

This closes another practical gap versus Alfresco-style operator work:

- richer batch recovery feedback
- consistent rendition-backed semantics
- less ambiguity about why a replay/clear action is being skipped or what preview state the item is actually in
