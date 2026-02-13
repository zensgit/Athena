# Phase 1 P83 - Search Dialog Preview Status Filter Parity (Design) - 2026-02-11

## Background
- `SearchResults` and `AdvancedSearchPage` already support preview-status filtering.
- `SearchDialog` (Advanced Search modal) did not expose preview status, so users could not build/save this filter from the modal entry path.

## Scope
- Frontend-only parity enhancement.
- Add regression coverage for request payload propagation and saved-search persistence.

## Changes
1. Add Preview Status selector to search dialog
- File: `ecm-frontend/src/components/search/SearchDialog.tsx`
- Added `Preview Status` multi-select with options:
  - `READY`, `PROCESSING`, `QUEUED`, `FAILED`, `UNSUPPORTED`, `PENDING`.
- Selection is shown as chips in the control.

2. Wire preview status into advanced search execution
- File: `ecm-frontend/src/components/search/SearchDialog.tsx`
- `handleSearch` now sends `previewStatuses` in `SearchCriteria`.
- Ensures modal-triggered advanced search uses same preview status semantics as search pages.

3. Persist preview status in saved search create/update
- File: `ecm-frontend/src/components/search/SearchDialog.tsx`
- `buildSavedSearchQueryParams()` now writes:
  - `filters.previewStatuses`.

4. Restore preview status from prefill / saved-search load
- Files:
  - `ecm-frontend/src/components/search/SearchDialog.tsx`
  - `ecm-frontend/src/utils/savedSearchUtils.ts`
- `SearchDialog` prefill now reads `searchPrefill.previewStatuses`.
- `buildSearchCriteriaFromSavedSearch` now maps `filters.previewStatuses` to `SearchCriteria.previewStatuses`.

5. Add tests
- Unit:
  - `ecm-frontend/src/utils/savedSearchUtils.test.ts`
  - Asserts `previewStatuses` mapping from saved query filters.
- E2E:
  - `ecm-frontend/e2e/search-dialog-preview-status.spec.ts`
  - Verifies modal selection:
    - is persisted into saved search
    - is propagated to `/api/v1/search` via `previewStatus` query parameter.

6. E2E stability hardening
- Files:
  - `ecm-frontend/e2e/helpers/login.ts`
  - `ecm-frontend/e2e/saved-search-overwrite-from-dialog.spec.ts`
- Added shared suppression/removal for the dev overlay iframe (`webpack-dev-server-client-overlay`) in login helper so top-bar actions (including Search) are not blocked during automated runs.
- Added explicit lint directive for Testing Library preference rule in the overwrite spec to keep the Playwright-focused locator style consistent.

## Risk and Mitigation
- Risk: modal form validity may block search/save unexpectedly.
  - Mitigation: `isSearchValid` now treats preview-status-only criteria as valid.
- Risk: saved-search schema drift.
  - Mitigation: mapping is additive and backward-compatible (`previewStatuses` optional).

## Rollback
- Revert `SearchDialog` preview-status UI and criteria wiring.
- Revert saved-search mapping change.
- Remove new P83 tests.
