# Phase 1 P93 Design: Global Advanced Search Prefill from Last Search State

## Background
- `SearchDialog` is global and can be opened from multiple entry points.
- Some entries pass explicit `searchPrefill`; others only open the dialog.
- Without explicit prefill, dialog opened blank even when user had active search criteria.

## Goal
- Make Advanced Search dialog continue from the latest known search context when explicit prefill is not provided.

## Scope
- Frontend only:
  - `SearchDialog` initialization behavior.
  - E2E regression for app-bar search entry path.

## Implementation
1. Add fallback prefill source
- File: `ecm-frontend/src/components/search/SearchDialog.tsx`
- Added `lastSearchCriteria` selector from `state.node`.
- When dialog opens:
  - if `searchPrefill` exists -> use it (existing behavior),
  - else -> map from `lastSearchCriteria` into dialog fields.

2. Prevent double-initialization while dialog is open
- Added `prefillInitializedRef` guard.
- Reset guard on dialog close (`searchOpen=false`).
- Avoid overwriting user edits due to later state changes while dialog remains open.

3. Criteria mapping details
- Name/contentType/creator/date/aspects/properties/tags/categories/correspondents/preview statuses.
- `path` from `lastSearchCriteria` mapped to dialog `pathPrefix`.
- Folder scope preserved (`folderId`, `includeChildren`).

4. E2E coverage
- File: `ecm-frontend/e2e/search-dialog-active-criteria-summary.spec.ts`
- New scenario:
  - run search on `/search-results`,
  - open app-bar search button (`header [aria-label=\"Search\"]`),
  - assert dialog prefilled with prior query.

## Expected Outcome
- Users opening Advanced Search from global app-bar no longer lose active search context.
- Behavior is consistent across explicit-prefill and implicit-prefill entry points.
