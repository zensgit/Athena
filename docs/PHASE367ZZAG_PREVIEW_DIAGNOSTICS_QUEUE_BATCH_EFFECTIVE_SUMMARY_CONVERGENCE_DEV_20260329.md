# PHASE367ZZAG Preview Diagnostics Queue Batch Effective Summary Convergence

## Goal

Continue the `preview / rendition` source-of-truth line by removing another admin-surface fork:

- `PreviewDiagnosticsController` batch queue actions should return the same effective preview summary contract already used by document queue/repair flows.
- `PreviewDiagnosticsPage` should apply that richer contract immediately to the `Rendition Resources` table instead of waiting for a full reload to reflect the new status.

## Why This Phase

Before this change, `failures/queue-batch` and `dead-letter/replay-batch` returned only:

- `previewStatus`
- `attempts`
- `nextAttemptAt`

That meant the admin preview workbench lost:

- `previewFailureReason`
- `previewFailureCategory`
- `previewLastUpdated`

even though the queue runtime and document queue APIs already had those fields.

This left `PreviewDiagnosticsPage` with a visible operator gap:

- queueing a rendition resource could change the actual effective preview state,
- but the local table could not faithfully reflect that richer state until `loadFailures()` completed.

## Scope

### Backend

Updated `PreviewQueueBatchItemDto` in `PreviewDiagnosticsController` to include:

- `previewFailureReason`
- `previewFailureCategory`
- `previewLastUpdated`

Applied the richer contract to both batch entry points:

- `POST /api/v1/preview/diagnostics/failures/queue-batch`
- `POST /api/v1/preview/diagnostics/dead-letter/replay-batch`

The controller now propagates the effective queue summary from `PreviewQueueService.PreviewQueueStatus` instead of truncating it to bare status.

### Frontend

Extended `PreviewQueueBatchItem` in `previewDiagnosticsService.ts` to match the backend contract.

Added a focused utility:

- `previewQueueBatchUtils.ts`

This utility projects a batch queue result back onto current `PreviewRenditionResource[]` rows while preserving existing reason/category when the queue response only updates status.

`PreviewDiagnosticsPage.tsx` now applies that local projection immediately after `queueFailuresBatch(...)` returns.

## Files

### Backend

- `ecm-core/src/main/java/com/ecm/core/controller/PreviewDiagnosticsController.java`
- `ecm-core/src/test/java/com/ecm/core/controller/PreviewDiagnosticsControllerSecurityTest.java`

### Frontend

- `ecm-frontend/src/services/previewDiagnosticsService.ts`
- `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`
- `ecm-frontend/src/utils/previewQueueBatchUtils.ts`
- `ecm-frontend/src/utils/previewQueueBatchUtils.test.ts`

## Outcome

Athena’s admin preview diagnostics surface now treats batch queue actions as first-class effective preview mutations rather than a reduced queue-only side channel.

This is a small but important step toward surpassing Alfresco on operator detail:

- richer queue feedback
- immediate local status reflection
- less dependence on whole-page reconciliation for obvious operator actions
