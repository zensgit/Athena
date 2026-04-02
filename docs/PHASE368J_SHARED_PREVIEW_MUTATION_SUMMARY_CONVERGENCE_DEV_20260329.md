# PHASE368J Shared Preview Mutation Summary Convergence

## Goal

Continue the `preview / rendition` source-of-truth convergence by removing the last duplicated preview-mutation fallback logic from `DocumentController`.

This phase makes preview mutation responses resolve through one shared service path instead of each endpoint re-merging:

- rendition-backed preview summary
- queue status fallback
- version label carry-through

## Why This Phase

Before this phase, Athena had already converged several preview surfaces onto effective preview semantics, but the write paths still had a subtle split:

- `RenditionResourceService` mutations (`requeue / invalidate`) already knew how to combine live rendition summary and queue fallback
- `DocumentController` still rebuilt parts of that logic inline for:
  - `POST /documents/{id}/preview/queue`
  - `POST /documents/{id}/preview/repair`
  - hash-enforced preview decline responses

That duplication created two problems:

- controller and service mutation responses could drift again
- new preview summary fields would require patching multiple mutation builders

## Scope

### Backend

Updated [RenditionResourceService.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/RenditionResourceService.java):

- added `resolvePreviewMutationSummary(Document, PreviewQueueStatus)`
- the helper now:
  - prefers `summarizeDocument(document)` when a rendition-backed document summary exists
  - falls back to queue-status-derived `previewStatus / failureReason / failureCategory / lastUpdated`
  - preserves `currentVersionLabel` when queue fallback is used

Updated existing mutation paths in the same service:

- `requeueForNode(...)`
- `invalidateForNode(...)`

Both now reuse the shared helper instead of separately calling `summarizeDocument(...)`.

Updated [DocumentController.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/controller/DocumentController.java):

- `repairPreview(...)` now uses `resolvePreviewMutationSummary(...)`
- `queuePreview(...)` now uses the same helper
- `buildHashEnforcedDeclinedResult(...)` now starts from the same shared mutation summary instead of controller-local preview merge logic
- removed the old controller-local preview summary helper

### Tests

Updated [DocumentControllerPreviewRepairTest.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/controller/DocumentControllerPreviewRepairTest.java):

- preview queue / repair / hash-enforced tests now mock the shared mutation summary contract directly
- controller tests no longer pin old `summarizeDocument(...)` internals for mutation routes

Updated [RenditionResourceServiceTest.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/service/RenditionResourceServiceTest.java):

- added focused coverage for queue-status fallback through `resolvePreviewMutationSummary(...)`

## Files

- `ecm-core/src/main/java/com/ecm/core/service/RenditionResourceService.java`
- `ecm-core/src/main/java/com/ecm/core/controller/DocumentController.java`
- `ecm-core/src/test/java/com/ecm/core/controller/DocumentControllerPreviewRepairTest.java`
- `ecm-core/src/test/java/com/ecm/core/service/RenditionResourceServiceTest.java`

## Outcome

Athena’s preview mutation surfaces now share one fallback contract:

- rendition-backed summary when available
- queue-derived effective summary when not
- controller and service mutation responses no longer drift independently

This is a structural cleanup phase, but it matters because it reduces the remaining preview lifecycle split between read, queue, repair, and rendition mutation flows.
