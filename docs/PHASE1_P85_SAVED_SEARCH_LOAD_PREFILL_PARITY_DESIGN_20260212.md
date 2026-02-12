# Phase 1 P85 - Saved Search Load Prefill Parity (Design) - 2026-02-12

## Background
- `Saved Searches` page supports "Load to Advanced Search".
- Before this change, prefill mapping in `SavedSearchesPage` omitted:
  - `previewStatuses`
  - `folderId` / `includeChildren`
- Result: loading a saved search into the dialog could lose important filters.

## Scope
- Frontend-only parity fix.
- Keep mapping behavior centralized to reduce drift.

## Changes
1. Extend prefill type with preview status
- File: `ecm-frontend/src/store/slices/uiSlice.ts`
- `SearchPrefill` now includes:
  - `previewStatuses?: string[]`

2. Reuse saved-search-to-criteria mapper in load flow
- File: `ecm-frontend/src/pages/SavedSearchesPage.tsx`
- Replaced manual `queryParams.filters` extraction with:
  - `buildSearchCriteriaFromSavedSearch(item)`
- `setSearchPrefill(...)` now forwards:
  - `previewStatuses`
  - `folderId`
  - `includeChildren`
  - existing fields (`name`, date ranges, tags/categories/correspondents, size, pathPrefix)

3. Add E2E coverage for prefill parity
- File: `ecm-frontend/e2e/saved-search-load-prefill.spec.ts`
- Verifies:
  - load action opens Advanced Search with restored query text
  - folder scope chip appears and `Include subfolders` reflects saved value
  - path field is disabled while folder scope is active
  - preview status options are preselected in the dialog

## Risk and Mitigation
- Risk: changing load mapping may alter existing prefill behavior.
  - Mitigation: use shared utility (`buildSearchCriteriaFromSavedSearch`) already used by run flow, reducing divergence.

## Rollback
- Revert `SavedSearchesPage` load mapping to previous manual extraction.
- Remove `previewStatuses` from `SearchPrefill`.
- Remove the P85 E2E spec.
