# Phase 69: Preview Failure Operator Loop - Development

## Date
2026-02-19

## Background
- Advanced Search already exposed retry/rebuild actions for preview failures, but operator feedback was coarse:
  - no in-panel batch progress while processing
  - reason grouping only via single-action text buttons
  - non-retryable summary text lacked explicit category counts

## Goal
1. Improve operator feedback loop during preview batch retry/rebuild.
2. Make retryable reason grouping easier to scan and act on.
3. Keep existing unsupported/permanent governance behavior intact.

## Changes

### 1) Batch operation progress feedback
- File: `ecm-frontend/src/pages/AdvancedSearchPage.tsx`
- Added batch run state:
  - operation label
  - in-progress counters (`processed/total`, `queued/skipped/failed`)
  - finished timestamp
- Added inline status alert in preview-issues panel:
  - `info` while running
  - `success` on fully successful completion
  - `warning` when failures occurred

### 2) Shared batch run executor
- File: `ecm-frontend/src/pages/AdvancedSearchPage.tsx`
- Refactored batch actions into a shared `runPreviewBatchAction(...)` callback:
  - supports both global failed set and reason-scoped targets
  - supports retry vs force rebuild mode
  - updates queue status map and progress state on each processed item
  - emits success/warning toast summary

### 3) Reason grouping polish
- File: `ecm-frontend/src/pages/AdvancedSearchPage.tsx`
- Retryable reasons now render as grouped chip + actions:
  - `Retry`
  - `Rebuild`
- Added list expansion toggle:
  - default top 4 reasons
  - `Show all reasons` / `Show fewer reasons`
- Added reason group header showing displayed count vs total.

### 4) Non-retryable summary clarity
- File: `ecm-frontend/src/pages/AdvancedSearchPage.tsx`
- Preserved existing governance message wording for compatibility.
- Added explicit category count line when all failed items are non-retryable:
  - `Unsupported X • Permanent Y`

### 5) Utility support for reusable phrasing
- File: `ecm-frontend/src/utils/previewStatusUtils.ts`
- Added:
  - `formatPreviewBatchOperationProgress(...)`
  - `buildNonRetryablePreviewSummaryMessage(...)`

## Non-Functional Notes
- No backend/API contract changes.
- Existing unsupported/permanent retry gating behavior remains unchanged.
