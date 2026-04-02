# Phase 366B: Advanced Search Single Envelope

## Goal

Reduce `AdvancedSearchPage` search orchestration from three backend requests to one unified search envelope request while preserving existing page state and UI rendering.

## Delivered

- Added `searchNodesEnvelope(...)` to `nodeService`.
- Kept the existing service methods intact:
  - `searchNodes(...)`
  - `getAdvancedSearchStats(...)`
  - `getAdvancedSearchPivotStats(...)`
- Migrated the main `AdvancedSearchPage` search execution path to a single envelope call returning:
  - results
  - facets
  - stats
  - pivot

## Design

This slice converges at the **service boundary** first.

Why:

- `AdvancedSearchPage` already has stable state setters for `results`, `facets`, `searchStats`, and `searchPivotStats`.
- Replacing the three-request `Promise.all(...)` with one `nodeService.searchNodesEnvelope(...)` call keeps the UI state model unchanged.
- This avoids a broader page refactor while immediately improving request coordination and contract consistency.

## Request Shape

`searchNodesEnvelope(...)` builds one `/api/v1/search/query` request with:

- `include: ['results', 'facets', 'stats', 'pivot']`
- current search filters
- current paging
- current sort fields

## Preserved Behaviour

- Result mapping to `SearchResult[]` stays unchanged.
- Facet normalization stays in `AdvancedSearchPage`.
- Existing retry / preview batch / saved search logic is not touched.
- `SearchResults` page remains on its own service path for now.

## Why This Matters

Before this phase, `AdvancedSearchPage` paid for three network trips and three parallel backend contracts for one interaction.

After this phase:

- one request drives the main advanced-search workspace,
- backend normalization is shared,
- and page-level orchestration becomes simpler without altering UI behavior.

## Claude Code Usage

Claude Code was used as a parallel design/code assistant for migration strategy review against the current page and service layer. Final code changes, integration, and validation were completed in this workspace flow.
