# Phase 251 - Advanced Search Non-retryable Dead-letter Clear (Dev)

Date: 2026-03-10  
Scope: `ecm-frontend`, `ecm-core` (contract regression test)

## 1. Goals

1. Expose dead-letter governance for non-retryable failure reasons directly in Advanced Search.
2. Keep replay actions limited to retryable reasons while allowing clear actions for non-retryable buckets.
3. Avoid action-state key collisions when the same reason appears under different category/retryable dimensions.

## 2. Implementation

File: `ecm-frontend/src/pages/AdvancedSearchPage.tsx`

- Added non-retryable reason aggregation from current page preview issue scope:
  - reason normalized via `normalizePreviewFailureReason(...)`
  - category normalized to `UNSUPPORTED` or source category fallback (`PERMANENT`)
  - retryable fixed to `false`
- Added non-retryable reason rendering section:
  - reason chip + category chip
  - `Clear DL` action per reason/category bucket
  - show-more/show-less toggle for long lists
- Upgraded dead-letter action key:
  - from `action + reason`
  - to `action + reason + category + retryable`
  - removes busy-state collisions across buckets.
- Generalized reason-level dead-letter handlers:
  - `handleReplayDeadLetterReasonAllMatched(reason, { category, retryable })`
  - `handleClearDeadLetterReasonAllMatched(reason, { category, retryable })`
  - retryable section continues to pass `TEMPORARY + true`.

## 3. E2E Contract Extension

File: `ecm-frontend/e2e/advanced-search-preview-batch-scope.mock.spec.ts`

- Added unsupported/non-retryable failed preview sample on current page.
- Added user path for:
  - `Clear dead-letter all matched for non-retryable reason unsupported mime type`
- Added payload assertions for non-retryable clear:
  - `reason=unsupported mime type`
  - `category=UNSUPPORTED`
  - `retryable=false`

## 4. Backend Contract Safety Net

File: `ecm-core/src/test/java/com/ecm/core/controller/OpsRecoveryControllerSecurityTest.java`

- Added admin security/controller regression:
  - `clearByFilterForUnsupportedNonRetryable()`
- Verifies ops recovery accepts:
  - `category=UNSUPPORTED`
  - `retryable=false`
- Verifies clear result and audit path remain valid.

## 5. Design Notes

- This closes the operational gap where non-retryable failures had visibility but no inline governance action.
- Replay remains intentionally restricted to retryable lanes; non-retryable uses clear-only governance.
