# Phase 1 P100 Design: Preview Governance Parity (Advanced Search)

Date: 2026-02-12

## Background

- Preview status values can arrive from URL/query in legacy aliases (`WAITING`, `IN_PROGRESS`, `ERROR`, `UNSUPPORTED_MEDIA_TYPE`).
- Advanced Search page previously parsed URL preview statuses with canonical-only filtering, which could drop alias values.
- Retry governance requires clear unsupported-only behavior:
  - unsupported failures must be counted separately,
  - retry actions hidden when retryable count is zero.

## Goal

Ensure Advanced Search preview behavior is parity-safe across:
- URL status alias parsing
- unsupported failure counting
- retry action visibility rules

## Scope

- `ecm-frontend/src/utils/searchPrefillUtils.ts`
- `ecm-frontend/src/pages/AdvancedSearchPage.tsx`
- `ecm-frontend/src/utils/searchPrefillUtils.test.ts`
- `ecm-frontend/e2e/search-dialog-active-criteria-summary.spec.ts`
- `ecm-frontend/e2e/advanced-search-fallback-governance.spec.ts`

## Implementation

1. Shared status alias normalization utility
- Exported `normalizePreviewStatusTokens(...)` from `searchPrefillUtils`.
- Includes canonical and alias mapping:
  - `WAITING` -> `QUEUED`
  - `IN_PROGRESS`/`RUNNING` -> `PROCESSING`
  - `ERROR` -> `FAILED`
  - `UNSUPPORTED_*` -> `UNSUPPORTED`

2. Advanced Search URL parsing alignment
- `AdvancedSearchPage` now uses shared status normalization when reading `previewStatus` from URL.
- Keeps allowed-status guard aligned to page-supported values.

3. Governance validation additions
- Added E2E coverage for:
  - URL alias mapping into Advanced Search dialog summary
  - unsupported-only failed previews hide retry actions and show guidance message

## Expected Outcome

- Legacy URL status aliases no longer get silently dropped.
- Preview retry controls remain consistent with unsupported failure governance.
