# Phase163 Dev: Prevention Batch Operations

## Date
2026-03-06

## Goal
Support operator-side batch actions for rendition prevention entries so unblock/requeue can be executed in one step for queue storm recovery.

## Backend changes
- `ecm-core/src/main/java/com/ecm/core/controller/PreviewDiagnosticsController.java`
  - added batch APIs:
    - `POST /api/v1/preview/diagnostics/prevention/unblock-batch`
    - `POST /api/v1/preview/diagnostics/prevention/unblock-requeue-batch`
  - batch response includes:
    - `requested`, `deduplicated`, `unblocked`, `queued`, `failed`, `results[]`
  - batch guardrail:
    - capped at `MAX_PREVENTION_BATCH_LIMIT=500`
  - unified internal handlers:
    - single-item and batch flow share same unblock/requeue logic.

## Frontend changes
- `ecm-frontend/src/services/previewDiagnosticsService.ts`
  - added:
    - `unblockRenditionPreventionBatch(...)`
    - `unblockAndRequeueRenditionBatch(...)`
- `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`
  - prevention panel enhancements:
    - row selection checkbox + select-all
    - blocked-item filter input
    - batch actions:
      - `Unblock Selected`
      - `Requeue Selected`
    - selection is auto-pruned after refresh.

## Test updates
- `ecm-core/src/test/java/com/ecm/core/controller/PreviewDiagnosticsControllerSecurityTest.java`
  - added forbidden checks for new batch endpoints.
  - added admin batch-action behavior test.
- `ecm-frontend/e2e/admin-preview-diagnostics.mock.spec.ts`
  - added mocked routes and assertions for prevention batch unblock/requeue flows.
