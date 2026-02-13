# Phase 1 P92 Design: Search Results -> Advanced Dialog Prefill Continuity

## Background
- Users can run quick search and facet filters from `SearchResults`.
- Clicking `Advanced` opened the global search dialog, but dialog fields could appear empty.
- This created a context break: users lost visibility of the active query while refining criteria.

## Goal
- Preserve user context by prefilling the Advanced Search dialog with current search-results state.

## Scope
- Frontend only.
- Entry point: `SearchResults` `Advanced` button.
- Regression coverage in Playwright E2E.

## Implementation
1. Wire prefill dispatch before opening dialog
- File: `ecm-frontend/src/pages/SearchResults.tsx`
- Updated `handleAdvancedSearch`:
  - Build a `searchPrefill` payload from current page state.
  - Dispatch `setSearchPrefill(...)` and then `setSearchOpen(true)`.

2. Prefill mapping rules
- `name`: current quick-search input (fallback to last criteria name).
- `contentType`: current mime filter only when exactly one mime is selected.
- `createdBy`: current creator filter only when exactly one creator is selected.
- Preserve existing criteria where UI is not directly editable from quick filter bar:
  - date ranges, aspects, properties, size, path, folder scope.
- Keep selected chips for:
  - tags, categories, correspondents, preview statuses.

3. E2E regression
- File: `ecm-frontend/e2e/search-dialog-active-criteria-summary.spec.ts`
- Added scenario:
  - Run quick search on `/search-results`.
  - Open `Advanced`.
  - Verify `Name contains` and `Active Criteria` summary carry the current query.

## Expected Outcome
- Advanced dialog opens with meaningful current criteria instead of empty state.
- Users can continue refining without retyping.
