# Phase 367ZZAJ: Preview Diagnostics Queue Cancel-Active Effective Summary Contract

## Goal

Bring `POST /api/v1/preview/diagnostics/queue/cancel-active` up to the same contract level as the newer preview queue mutation endpoints.

Before this slice, cancel-active returned queue outcome only:

- `documentId`
- `queueState`
- `outcome`
- `message`

That made it weaker than queue-batch, dead-letter replay, and queue summary, all of which already carried effective preview summary fields.

## Backend Scope

Files:

- `ecm-core/src/main/java/com/ecm/core/controller/PreviewDiagnosticsController.java`
- `ecm-core/src/test/java/com/ecm/core/controller/PreviewDiagnosticsControllerSecurityTest.java`

Changes:

- Extended `PreviewQueueCancelActiveItemDto` with:
  - `previewStatus`
  - `previewFailureReason`
  - `previewFailureCategory`
  - `previewLastUpdated`
- Reused already-mapped queue diagnostics candidate semantics as the source for those fields
- Kept queue cancellation outcome semantics unchanged

## Frontend Scope

Files:

- `ecm-frontend/src/services/previewDiagnosticsService.ts`
- `ecm-frontend/src/utils/previewQueueDiagnosticsUtils.ts`
- `ecm-frontend/src/utils/previewQueueDiagnosticsUtils.test.ts`

Changes:

- Extended `PreviewQueueCancelActiveItem` typing with effective preview summary fields
- Updated local queue diagnostics override projection to preserve preview summary fields from cancel-active results

## Outcome

All queue diagnostics mutation/read surfaces now share the same effective preview summary vocabulary:

- queue summary
- queue summary export
- queue batch
- dead-letter replay batch
- cancel-active
