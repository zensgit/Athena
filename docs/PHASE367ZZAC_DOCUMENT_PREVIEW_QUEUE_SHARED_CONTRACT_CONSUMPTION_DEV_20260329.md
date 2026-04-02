# PHASE367ZZAC Document Preview Queue Shared Contract Consumption

## Goal
- Remove the local `DocumentPreview` queue-preview contract fork and reuse the shared `nodeService.queuePreview(...)` response.

## Scope
- `ecm-frontend/src/components/preview/DocumentPreview.tsx`
- `ecm-frontend/src/services/nodeService.ts`

## Design
- Replace the local ad-hoc queue request with `nodeService.queuePreview(nodeId, force)`.
- Reuse the shared `PreviewQueueStatus` type so `previewFailureReason / previewFailureCategory / previewLastUpdated` are available to the preview surface.
- Apply the returned effective preview status/reason directly to local overrides instead of always forcing `PROCESSING`.

## Outcome
- `DocumentPreview` no longer bypasses the shared queue-preview contract.
- The preview dialog immediately reflects richer effective semantics after queue actions.
