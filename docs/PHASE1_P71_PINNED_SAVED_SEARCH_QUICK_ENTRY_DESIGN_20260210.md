# Phase 1 (P71) - Pinned Saved Searches Quick Entry (SearchResults)

## Goal
Provide a fast, in-context way to run **pinned saved searches** directly from the `SearchResults` page, comparable to "Saved Views / Query Templates" patterns in common ECM systems.

This reduces navigation friction (no need to go to Admin Dashboard or Saved Searches page just to rerun a frequently-used query).

## Scope
- Frontend-only feature for a quick entry menu on `SearchResults`.
- Reuse existing backend Saved Search APIs.
- Small UX support actions:
  - Run a pinned saved search.
  - Unpin a saved search from the quick menu.
  - Navigate to manage saved searches.
  - Refresh pinned list.

## Non-Goals
- Changing backend Saved Search models or endpoints.
- Adding a new persistence layer or caching (beyond in-memory UI state).
- Implementing a full "Saved Search" CRUD UI inside SearchResults (that already exists in `SavedSearchesPage`).

## UX / UI Design
### Entry Point
- Add a `Saved` button (star icon) next to the existing `Advanced` button on `SearchResults`.
- Clicking `Saved` opens a dropdown menu listing pinned saved searches.

### Menu Contents
1. Header (disabled): `Pinned Saved Searches`
2. Body:
   - Loading state: spinner + `Loading pinned searches…`
   - Error state: `Failed to load pinned saved searches`
   - Empty state: `No pinned searches yet.`
   - Data state: each pinned saved search as a menu item:
     - Click item: run the saved search and update results.
     - Unpin icon button: unpin without running.
3. Footer:
   - `Manage saved searches` navigates to `/saved-searches`
   - `Refresh` reloads the pinned list

## Data Flow
### Load Pinned Saved Searches
- `GET /api/v1/search/saved`
- Client filters `pinned === true` and sorts by `createdAt desc` for stable ordering.

### Run Saved Search
- `GET /api/v1/search/saved/{id}/execute` is invoked via existing Redux thunk `executeSavedSearch`.
- `setLastSearchCriteria(buildSearchCriteriaFromSavedSearch(item))` is dispatched so the SearchResults UI (scope chip, facet selection sync, etc.) reflects the executed query.

### Unpin
- `PATCH /api/v1/search/saved/{id}/pin { pinned: false }`
- UI updates local state by removing the item from the pinned list.

## Bug Fix Included (Criteria Mapping)
When a saved search was created from Advanced Search with folder scoping, its stored `queryParams.filters.folderId/includeChildren` were not mapped back into `SearchCriteria`, causing the SearchResults scope chip to be missing/incorrect.

Fix: `buildSearchCriteriaFromSavedSearch` now maps:
- `filters.folderId -> SearchCriteria.folderId`
- `filters.includeChildren -> SearchCriteria.includeChildren`

## Security / Privacy
- No credentials or tokens are logged or written into docs/tests.
- E2E uses Keycloak password grant via the existing helper to obtain a short-lived access token (never printed).

## Files Changed
- `ecm-frontend/src/pages/SearchResults.tsx`
  - Added `Saved` menu UI and pinned list loading/running/unpin actions.
- `ecm-frontend/src/utils/savedSearchUtils.ts`
  - Added `folderId/includeChildren` mapping.

## Acceptance Criteria
- Pinned saved searches are discoverable from `SearchResults` without leaving the page.
- Selecting an item runs the saved search and updates the results list.
- Scope chip appears correctly when a saved search includes `folderId/includeChildren`.
- Unpin action removes the item from the quick menu.

