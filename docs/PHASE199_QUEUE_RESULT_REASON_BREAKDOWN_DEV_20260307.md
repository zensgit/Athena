# Phase 199 - Queue Result Reason Breakdown (Development)

## Date
2026-03-07

## Goal
- Return reason-level breakdown in queue execution responses.
- Surface batch reason distribution in Advanced Search execution feedback.
- Keep dry-run and execution summaries aligned for operator observability.

## Implemented

### 1) Backend queue response enhancement
- Updated `ecm-core/src/main/java/com/ecm/core/controller/SearchController.java`:
  - Extended `PreviewQueueBySearchResponse` with:
    - `reasonBreakdown: List<{ reason, count }>`
  - Reused a shared helper to compute reason breakdown for:
    - queue endpoint
    - dry-run endpoint
  - Grouping behavior:
    - normalize blank reason to `UNSPECIFIED`
    - sort by `count desc`, then `reason asc`

### 2) Backend test enhancement
- Updated `ecm-core/src/test/java/com/ecm/core/controller/SearchControllerTest.java`:
  - Added queue endpoint assertions for:
    - `$.reasonBreakdown[0].reason`
    - `$.reasonBreakdown[0].count`

### 3) Frontend integration
- Updated `ecm-frontend/src/services/nodeService.ts`:
  - Extended `PreviewQueueSearchBatchResult` with `reasonBreakdown`.
- Updated `ecm-frontend/src/pages/AdvancedSearchPage.tsx`:
  - Added batch result reason state.
  - Displays `Batch reasons` chips after queue execution.
  - Server path reads response `reasonBreakdown`.
  - Fallback path computes equivalent reason breakdown from local targets.

### 4) Mock E2E alignment
- Updated `ecm-frontend/e2e/advanced-search-preview-batch-scope.mock.spec.ts`:
  - Mock queue response now includes `reasonBreakdown`.

## Notes
- This phase is additive and backward-compatible.
- Operators can now compare dry-run and execution reason distributions directly in the UI.
