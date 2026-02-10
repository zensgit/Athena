# Phase 1 (P73) - Saved Search CRUD: Get-by-Id + Update (Name / Query Params)

## Goal
Close the gap from P72 (deep-link execute) by adding:
1. Backend `GET /search/saved/{id}` so the UI can resolve a single saved search without fetching the full list.
2. Backend `PATCH /search/saved/{id}` to support renaming and updating query parameters.
3. Frontend UI actions on Saved Searches:
   - Rename
   - Duplicate (create a new saved search with copied query params)

This enables a more Alfresco-like "query template" workflow:
- keep a saved search stable (id)
- evolve its name/query over time
- share a deep link that always runs the latest definition for the same user.

## Scope
### Backend
- Additive endpoints under `GET/PATCH /api/v1/search/saved/{id}`.
- User-scoped access:
  - owner can access/update
  - others get 403.
- Validation:
  - `name` cannot be blank (if provided)
  - `name` must be unique per user (if changed)
  - null request fields mean "no change".

### Frontend
- `SearchResults` deep-link flow uses `GET /search/saved/{id}` (instead of list+find).
- Saved Searches page:
  - Add actions: Rename, Duplicate
  - Use a dialog to enter the name
  - Rename uses `PATCH /search/saved/{id}`
  - Duplicate uses existing `POST /search/saved`.

## Non-Goals
- Cross-user saved search sharing (saved searches remain user-scoped).
- Partial merge patch of query params:
  - `PATCH` replaces the stored `queryParams` object when provided.

## API Contract
### Get Saved Search
- `GET /api/v1/search/saved/{id}`
- Response: `SavedSearch` (same shape as list)
- Errors:
  - 403 if not owned by current user
  - 400 if id not found (consistent with existing error mapping)

### Update Saved Search
- `PATCH /api/v1/search/saved/{id}`
- Body:
```json
{
  "name": "Optional new name",
  "queryParams": { "optional": "full query params object" }
}
```
- Behavior:
  - `name: null/omitted` -> no change
  - `queryParams: null/omitted` -> no change
  - if neither is provided -> request rejected ("Nothing to update")
- Errors:
  - 403 if not owned by current user
  - 400 if id not found or validation fails

## UX / UI
### Saved Searches Page
Add two actions per saved search row:
- `Rename saved search <name>`: opens dialog, updates name via API, updates table inline.
- `Duplicate saved search <name>`: opens dialog, creates new saved search using a deep copy of query params, refreshes list.

### Search Results Deep Link
When visiting:
- `/search-results?savedSearchId=<id>`

The page:
1. Fetches the saved search via `GET /api/v1/search/saved/{id}`
2. Executes the saved search via existing execute endpoint
3. Updates UI state (`setLastSearchCriteria`) so scope chips/filters reflect the saved search definition.

## Files Changed
### Backend
- `ecm-core/src/main/java/com/ecm/core/controller/SavedSearchController.java`
- `ecm-core/src/main/java/com/ecm/core/service/SavedSearchService.java`

### Frontend
- `ecm-frontend/src/services/savedSearchService.ts`
- `ecm-frontend/src/pages/SearchResults.tsx`
- `ecm-frontend/src/pages/SavedSearchesPage.tsx`

### E2E
- `ecm-frontend/e2e/saved-search-crud-update.spec.ts`

## Acceptance Criteria
1. Deep link uses get-by-id and runs the saved search.
2. Saved Searches page can rename a saved search and persists it.
3. Saved Searches page can duplicate a saved search.
4. Backend PATCH can update saved search query params (and deep link runs the updated query).

