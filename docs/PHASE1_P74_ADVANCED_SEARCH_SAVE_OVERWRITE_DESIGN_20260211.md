# Phase 1 (P74) - Advanced Search Save Dialog: Overwrite Existing Saved Search

## Goal
Extend Advanced Search `Save Search` behavior from "create only" to full lifecycle editing:
- Create new saved search (existing behavior)
- Update/overwrite an existing saved search directly from the same dialog

This closes a practical gap compared with mature ECM workflows where users iteratively refine query templates instead of recreating them.

## Scope
### Frontend
- `SearchDialog` save modal now supports two modes:
  - `Create new`
  - `Update existing`
- In `Update existing` mode:
  - user selects target saved search
  - user can edit name
  - submit sends `PATCH /api/v1/search/saved/{id}` with updated `name` + current query params

### Backend
- No new backend changes in P74 (P73 endpoints are reused).

## UX Behavior
### Save Search dialog
1. Click `Save Search` in `Advanced Search` dialog.
2. Choose mode:
   - `Create new` (default)
   - `Update existing`
3. If `Update existing`:
   - choose target saved search from list
   - name auto-fills from selected saved search (editable)
4. Submit:
   - create mode -> `Saved search created`
   - update mode -> `Saved search updated`

## Validation Rules
- Name is required in both modes.
- Update mode requires selecting a target saved search.
- Submit and cancel actions are locked while request is in flight.

## Technical Notes
- `SearchDialog` loads saved search options via `savedSearchService.list()` when opening save modal.
- Reused existing frontend service:
  - `savedSearchService.update(id, { name, queryParams })` (from P73)
- Added wrapper close handlers to avoid React/MUI event type mismatch and to keep close behavior safe during submit.

## Files Changed
- `ecm-frontend/src/components/search/SearchDialog.tsx`
- `ecm-frontend/e2e/saved-search-overwrite-from-dialog.spec.ts`

## Acceptance Criteria
1. User can still create a new saved search from Advanced Search.
2. User can overwrite an existing saved search from Advanced Search.
3. Overwritten saved search is immediately executable via deep link (`savedSearchId`) and reflects updated query parameters.

