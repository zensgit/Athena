# Design: "More like this" in Search Results (2026-01-28)

## Goals
- Provide a one-click way to discover documents similar to a selected result.
- Reuse existing backend similarity endpoint without new API changes.

## Backend Notes
- Existing endpoint: `GET /api/v1/search/similar/{documentId}?maxResults=<n>`.
- Returns a list of `SearchResult` items from Elasticsearch.

## Frontend Design
- Add a "More like this" action to document cards in `SearchResults`.
- On click, fetch similar results via `nodeService.findSimilar` and swap the results list locally.
- Display an info banner indicating the source document and a "Back to results" action.
- Keep the last search results in state; returning clears the similar override.
- Use a per-card loading state so the button reflects progress.

## Trade-offs
- Similar results are not paginated; request is capped to the current page size.
- Facet filters remain visible but are not applied to the similar result set.

## Files
- `ecm-frontend/src/pages/SearchResults.tsx`
- `ecm-frontend/src/services/nodeService.ts`
