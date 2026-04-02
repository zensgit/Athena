# Phase 250 - Advanced Search Reason-level Dead-letter Governance Actions (Dev)

Date: 2026-03-10  
Scope: `ecm-frontend`

## 1. Goals

1. Bring dead-letter governance actions into Advanced Search reason scope.
2. Avoid context switching to Preview Diagnostics for common replay/clear actions.
3. Keep filter semantics aligned with search window and reason buckets.

## 2. Implementation

File: `ecm-frontend/src/pages/AdvancedSearchPage.tsx`

- Added `opsRecoveryService` integration.
- Added window-day mapper:
  - `resolveWindowDaysFromDateRange(...)`
- Added reason-level governance handlers:
  - `handleReplayDeadLetterReasonAllMatched(reason)`
  - `handleClearDeadLetterReasonAllMatched(reason)`
- Added action-state tracking:
  - `reasonDeadLetterActionKey`
- Extended retry-reasons action row with:
  - `Replay DL`
  - `Clear DL`
- Added accessibility labels:
  - `Replay dead-letter all matched for reason ...`
  - `Clear dead-letter all matched for reason ...`

Behavior:

- Reuses reason bucket from current search result summary.
- Applies search date-range as ops window days (`today=1`, `week=7`, `month=30`, `all=0`).
- Uses temporary/retryable dead-letter filter defaults for retryable reason scope.

## 3. E2E Contract Extension

File: `ecm-frontend/e2e/advanced-search-preview-batch-scope.mock.spec.ts`

- Added mocked routes:
  - `POST /api/v1/ops/recovery/replay-by-filter`
  - `POST /api/v1/ops/recovery/clear-by-filter`
- Added UI steps for new buttons:
  - click `Replay dead-letter all matched for reason ...`
  - click `Clear dead-letter all matched for reason ...`
- Added payload assertions for reason/category/retryable/force semantics.

## 4. Design Notes

- This phase extends “retry by reason” into “govern by reason” for dead-letter backlog.
- It closes an operational loop inside the search cockpit and improves admin throughput.
