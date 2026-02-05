# PHASE48_SEARCH_EXPLAINABILITY_DEV_20260205

## Scope
- Add search explainability metadata (matched fields + highlight summary) to search results.
- Surface match reasons in Search Results and Advanced Search UI.
- Extend search highlight E2E to validate match reason rendering.

## Backend changes
- Added `SearchHighlightHelper` to derive match fields and highlight summary from Elasticsearch highlights.
- Extended `SearchResult` to include `matchFields` and `highlightSummary`.
- Populated explainability fields in `FullTextSearchService` and `FacetedSearchService`.

## Frontend changes
- Extended node/search models to carry `matchFields` and `highlightSummary`.
- Search Results now shows "Matched in" chips derived from highlight fields.
- Advanced Search now shows highlight summary + match reason chips.
- Updated saved search result mapping to include explainability fields.

## Notes
- `highlightSummary` prefers description/content/text fields, with fallback to the first highlight entry.
- Match fields are derived from highlight keys to stay consistent with ES scoring.
