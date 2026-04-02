# Phase 193 - Advanced Search All-Matched Preview Batch Actions (Development)

## Date
2026-03-07

## Goal
- Remove the current-page-only limitation for failed preview batch retry/rebuild.
- Support operator actions across all matched search results (bounded by safety cap).
- Keep current-page quick actions unchanged while adding scoped "all matched" controls.

## Implemented

### 1) All-matched failed preview collector
- Updated `ecm-frontend/src/pages/AdvancedSearchPage.tsx`:
  - Added bounded collector:
    - `collectMatchedRetryableFailedTargets(reason?)`
  - Collector behavior:
    - Uses current query + filters.
    - Enforces `previewStatuses=['FAILED']`.
    - Scans paginated search results.
    - Keeps retryable-only candidates.
    - Supports optional reason-scope.
    - Caps at `PREVIEW_BATCH_MATCHED_MAX = 200`.

### 2) New batch actions in UI
- Updated `ecm-frontend/src/pages/AdvancedSearchPage.tsx`:
  - Added global actions:
    - `Retry all matched (max 200)`
    - `Rebuild all matched (max 200)`
  - Added reason-scope global actions per reason chip:
    - `Retry all`
    - `Rebuild all`
  - Added resolving indicator:
    - "Collecting retryable failed previews across all matched results..."
  - Added scope label in batch progress/toast messages.

### 3) Date filter helper hardening
- Added `resolveModifiedFromDate(...)` helper to avoid mutable date-side effects and keep search/batch criteria consistent.

### 4) Mock E2E coverage
- Added `ecm-frontend/e2e/advanced-search-preview-batch-scope.mock.spec.ts`:
  - Verifies current-page retry still queues preview.
  - Verifies all-matched action triggers bounded background search scan with:
    - `size=50`
    - `previewStatus=FAILED`
  - Verifies search-scope request propagation through mock routes.
