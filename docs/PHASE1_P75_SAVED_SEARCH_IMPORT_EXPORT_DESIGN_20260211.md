# Phase 1 (P75) - Saved Searches Import/Export JSON

## Goal
Add portable backup/migration for user saved searches:
- Export current saved searches to a JSON file.
- Import saved searches from JSON back into the current account.

This closes the gap between CRUD usability and cross-environment transfer.

## Scope
### Frontend
- `SavedSearchesPage` adds:
  - `Export JSON` action
  - `Import JSON` action
  - hidden file input for JSON uploads
- Import processing runs client-side using existing saved-search APIs.

### Backend
- No backend API changes required.
- Reuse existing endpoints:
  - `GET /api/v1/search/saved`
  - `POST /api/v1/search/saved`
  - `PATCH /api/v1/search/saved/{id}/pin` (optional pin restore)

## JSON Contract
Export file shape:

```json
{
  "version": 1,
  "exportedAt": "2026-02-11T10:00:00.000Z",
  "savedSearches": [
    {
      "name": "Example",
      "queryParams": { "query": "invoice", "filters": {} },
      "pinned": true
    }
  ]
}
```

Import parser supports:
- object payload with `savedSearches` array (preferred)
- direct array of entries (compatibility fallback)

## Import Rules
1. Validate JSON syntax.
2. Normalize entries:
   - `name` must be non-empty string.
   - `queryParams` must be object, default `{}`.
3. Conflict strategy:
   - dedupe by lowercased trimmed `name`
   - existing names are skipped, not overwritten
4. For imported rows with `pinned=true`, set pinned after create.
5. Show summary toast:
   - `Import complete: X imported, Y skipped, Z failed`

## UX Notes
- Export button remains available whenever page loads.
- Import button is disabled while an import is running.
- Existing refresh/rename/duplicate/delete workflows are unchanged.

## Files Changed
- `ecm-frontend/src/pages/SavedSearchesPage.tsx`
- `ecm-frontend/e2e/saved-search-import-export.spec.ts`

## Acceptance Criteria
1. User can export saved searches to local JSON.
2. Exported JSON includes `name/queryParams/pinned` and excludes runtime IDs.
3. User can import JSON and recover missing saved searches.
4. Duplicate names are skipped safely with clear feedback.
5. Existing saved-search CRUD flows remain stable.
