# Phase 153: Preview Diagnostics Reason Batch Actions (Development)

## Date
2026-03-06

## Goal
- Allow operators to trigger retry/rebuild in one click for a failure-reason group, instead of repeating per-row actions.

## Scope
- Frontend:
  - Top-reasons table adds per-reason actions:
    - `Retry`
    - `Force`
  - Actions apply to retryable documents in current list matching reason + category.
  - Add `Current List` count to show effective action scope.
- E2E:
  - Extend mocked preview diagnostics spec to cover:
    - summary rendering
    - days switch 7 -> 30
    - reason-group retry action

## Design
1. Reuse existing `queuePreview(documentId, force)` endpoint.
2. Match reason group by normalized reason + category + retryable flag.
3. Execute group action with `Promise.allSettled` and aggregate user feedback toast.
4. Disable conflicting row actions while reason-batch action is running.

## Changed Files
- `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`
- `ecm-frontend/e2e/admin-preview-diagnostics.mock.spec.ts`
