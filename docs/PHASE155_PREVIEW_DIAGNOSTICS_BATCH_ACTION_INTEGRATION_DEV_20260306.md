# Phase 155: Preview Diagnostics Batch Action Integration (Development)

## Date
2026-03-06

## Goal
- Switch reason-group actions in Preview Diagnostics from per-item loop requests to backend batch queue API.

## Scope
- Frontend:
  - add `queueFailuresBatch` service call
  - reason-group actions use batch endpoint
  - toast reflects queued/skipped/failed aggregate counts
- E2E:
  - mocked route for batch endpoint
  - verify days switching + reason-group action path

## Design
1. Keep single-row retry/force behavior unchanged (`/documents/{id}/preview/queue`).
2. For reason-group action:
   - collect matched ids from current list
   - call `POST /preview/diagnostics/failures/queue-batch`
   - refresh diagnostics after response
3. Continue disabling conflicting actions while batch action runs.

## Changed Files
- `ecm-frontend/src/services/previewDiagnosticsService.ts`
- `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`
- `ecm-frontend/e2e/admin-preview-diagnostics.mock.spec.ts`
