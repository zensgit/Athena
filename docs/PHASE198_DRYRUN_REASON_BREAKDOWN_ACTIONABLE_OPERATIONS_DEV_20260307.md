# Phase 198 - Dry-Run Reason Breakdown Actionable Operations (Development)

## Date
2026-03-07

## Goal
- Turn dry-run reason breakdown into immediate operation entry points.
- Reduce operator steps from "inspect reason" to "execute all-matched action for that reason".
- Keep compatibility with existing all-matched queue flows.

## Implemented

### 1) Advanced Search dry-run actionable controls
- Updated `ecm-frontend/src/pages/AdvancedSearchPage.tsx`:
  - In dry-run summary `reasonBreakdown`, each reason row now includes:
    - `Retry all`
    - `Rebuild all`
  - Both actions invoke existing all-matched reason queue path:
    - `handleRetryFailedReasonAllMatched(reason, force)`
  - Added descriptive `aria-label` for deterministic E2E targeting:
    - `Retry all matched for reason ...`
    - `Rebuild all matched for reason ...`

### 2) Mock E2E enhancement
- Updated `ecm-frontend/e2e/advanced-search-preview-batch-scope.mock.spec.ts`:
  - After dry-run, test clicks reason-level action button.
  - Verifies queue API is called with expected `reason`.
  - Maintains existing assertions for:
    - current-page retry queue
    - all-matched queue without explicit reason.

## Notes
- This phase introduces no backend contract change.
- New controls are additive and respect existing busy/disabled state handling.
