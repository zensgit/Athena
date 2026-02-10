# Phase 1 (P72) - Saved Search Share Link (Deep Link Execute)

## Goal
Allow a saved search to be executed directly from a URL (deep link), enabling:
- quick bookmarking in the browser
- sharing within the same user context (e.g., multiple devices, same account)
- a lightweight "query template" workflow similar to Alfresco-style query templates

## Scope
- Frontend-only deep link execution via:
  - `/search-results?savedSearchId=<id>`
- Saved Searches management page adds a "Copy link" action.

## Non-Goals
- Cross-user sharing of saved searches (saved searches are user-scoped today).
- Backend changes (no new endpoints required for this iteration).

## UX / UI
### Saved Searches Page
Add an action button in the Saved Searches table:
- `Copy saved search link`
  - Copies a URL like: `http(s)://<host>/search-results?savedSearchId=<id>`
  - Shows toast on success/failure.

### Search Results Page
On page load (and whenever `location.search` changes):
1. If `savedSearchId` exists and is new:
2. Load saved search definitions via `GET /api/v1/search/saved` and locate the id.
3. Execute it via existing thunk `executeSavedSearch(id)` (calls `/api/v1/search/saved/{id}/execute`).
4. Dispatch `setLastSearchCriteria(buildSearchCriteriaFromSavedSearch(savedSearch))` so the UI reflects scope and filters.

## Data Flow / Implementation Notes
- We intentionally reuse the existing `list()` endpoint to locate the saved search record because there is no `GET /search/saved/{id}` endpoint yet.
- A `useRef` guard prevents repeated executions for the same `savedSearchId` value.

## Security / Privacy
- No tokens are logged or embedded into URLs.
- Deep link only contains the saved search id; actual execution still requires auth.

## Files Changed
- `ecm-frontend/src/pages/SearchResults.tsx`
  - Parse `savedSearchId` from URL and execute once.
- `ecm-frontend/src/pages/SavedSearchesPage.tsx`
  - Add "Copy link" action.

## Acceptance Criteria
- Visiting `/search-results?savedSearchId=<id>` executes the saved search and renders results.
- Saved Searches page can copy the deep link to clipboard.
- Folder scope (`folderId/includeChildren`) appears correctly in SearchResults after deep link execution.

