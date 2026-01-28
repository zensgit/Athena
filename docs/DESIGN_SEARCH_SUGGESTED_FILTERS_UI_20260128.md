# Design: Suggested Filters in Search Results (2026-01-28)

## Goals
- Surface smart filter suggestions to speed up narrowing results.
- Reuse existing backend endpoint for suggested filters.

## Backend Notes
- Existing endpoint: `GET /api/v1/search/filters/suggested?q=<query>`.
- Returns `field`, `label`, `value`, `count` for common filters.

## Frontend Design
- Fetch suggestions when a query is present and not in "similar" mode.
- Render suggestions as clickable chips above search results.
- Applying a chip updates the active search criteria:
  - `mimeType` -> `mimeTypes` filter
  - `createdBy` -> `createdByList` filter
  - `dateRange` -> `createdFrom` calculated from now
- Suggested filters are hidden during "More like this" mode.

## Trade-offs
- Suggestions are based on current query only, not combined with active filters.
- Date range uses client time and ISO timestamps.

## Files
- `ecm-frontend/src/pages/SearchResults.tsx`
- `ecm-frontend/src/services/nodeService.ts`
