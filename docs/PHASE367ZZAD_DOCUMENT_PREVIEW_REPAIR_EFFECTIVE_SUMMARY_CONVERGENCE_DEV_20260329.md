# PHASE367ZZAD Document Preview/Repair Effective Summary Convergence

## Goal
- Make `DocumentController` preview read and repair responses speak the same rendition-backed effective preview semantics already used by queue/search/admin surfaces.

## Scope
- `ecm-core/src/main/java/com/ecm/core/controller/DocumentController.java`
- `ecm-core/src/test/java/com/ecm/core/controller/DocumentControllerPreviewRepairTest.java`
- `ecm-frontend/src/services/nodeService.ts`

## Design
- `buildHashEnforcedDeclinedResult(...)` now prefers `RenditionResourceService.summarizeDocument(document)` for `status / failureReason / failureCategory`.
- When no rendition summary is available, hash-enforced fallback no longer inherits raw document `READY`; it falls back to queue-admitted status or derived `FAILED / UNSUPPORTED`.
- `PreviewRepairResponse` now includes:
  - `previewStatus`
  - `previewFailureReason`
  - `previewFailureCategory`
  - `previewLastUpdated`
- Frontend `PreviewRepairStatus` is expanded to match the richer contract.

## Outcome
- Preview read, repair, and queue endpoints are closer to one shared effective preview contract.
- Hash-enforced repair flows no longer leak stale raw `READY` semantics back to callers.
