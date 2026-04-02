# Phase 197 - Search-Scope Dry-Run Reason Breakdown (Development)

## Date
2026-03-07

## Goal
- Improve dry-run observability by returning reason-level distribution.
- Display reason breakdown directly in Advanced Search dry-run summary.
- Keep fallback dry-run path behavior aligned with backend response shape.

## Implemented

### 1) Backend dry-run response enhancement
- Updated `ecm-core/src/main/java/com/ecm/core/controller/SearchController.java`:
  - Extended dry-run payload with `reasonBreakdown`:
    - list of `{ reason, count }`
  - Breakdown generation:
    - computed from matched retryable failed previews
    - normalized blank reason to `UNSPECIFIED`
    - sorted by `count desc`, then `reason asc`

### 2) Backend test enhancement
- Updated `ecm-core/src/test/java/com/ecm/core/controller/SearchControllerTest.java`:
  - Added assertions for:
    - `$.reasonBreakdown[0].reason`
    - `$.reasonBreakdown[0].count`

### 3) Frontend API and UI enhancement
- Updated `ecm-frontend/src/services/nodeService.ts`:
  - Added dry-run type:
    - `PreviewQueueSearchReasonCount`
  - Extended dry-run result type with:
    - `reasonBreakdown: PreviewQueueSearchReasonCount[]`
- Updated `ecm-frontend/src/pages/AdvancedSearchPage.tsx`:
  - Extended dry-run summary state to include reason breakdown.
  - Backend path: consumes `reasonBreakdown` directly.
  - Fallback path: computes equivalent reason breakdown from scanned targets.
  - UI: renders top reason chips in dry-run summary panel.

## Notes
- This phase is additive and backward-compatible for existing queue/rebuild execution flows.
- All previous dry-run metrics (`matched`, `sampleCount`, `scanned`) remain unchanged.
