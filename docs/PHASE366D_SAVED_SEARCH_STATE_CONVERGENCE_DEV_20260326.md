# Phase 366D: Saved Search State Convergence

## Goal

Converge saved-search replay onto the same advanced-search helper family used by `AdvancedSearchPage`, so saved-search execution and advanced-search URL/template parsing stop drifting on preview-status, query, list, numeric, and relative date-range semantics.

## Delivered

- `savedSearchUtils` now delegates its shared filter parsing to `advancedSearchStateUtils` instead of maintaining a separate query/mime/tag/category/preview/min-max normalization path.
- Added `buildAdvancedSearchStateFromSavedSearch(...)` in `savedSearchUtils` as a shared bridge for saved-search replay.
- `buildSearchCriteriaFromSavedSearch(...)` is now a thin adapter:
  - shared advanced-search state comes from the helper
  - saved-search-only fields like `aspects`, `properties`, `correspondents`, `path`, `folderId`, and `includeChildren` remain preserved
  - `dateRange` now falls back to `modifiedFrom` via the same helper logic when no explicit modified range exists
- Hardened `advancedSearchStateUtils` so it also handles:
  - JSON-string `filters` payloads
  - mixed array values where non-primitive entries should be ignored

## Design

This slice converges at the **saved-search replay contract** layer.

Why:

- Before this phase, saved-search replay and advanced-search state restore each had their own normalization logic.
- That duplicated preview-status alias handling, list parsing, and numeric coercion.
- It also created a long-term regression risk: a filter accepted by advanced-search URL/template replay could behave differently when executed from pinned or saved searches.

## Preserved Behaviour

- Call sites did not change:
  - `SavedSearchesPage`
  - `SearchResults`
  - `AdminDashboard`
- `buildSearchCriteriaFromSavedSearch(...)` keeps the same signature and return shape.
- Legacy saved-search payloads still work, including:
  - top-level `queryParams`
  - `filters/filter/criteria`
  - JSON-string payloads
  - preview-status aliases
  - old path/date aliases

## Why This Matters

After `Phase366B` and `Phase366C`, Athena had unified envelope requests and shared advanced-search state parsing, but saved-search replay still represented a second normalization path.

This phase removes that split for the overlapping filter set, which improves operator consistency and makes later saved-search deep-link / replay work safer.

## Claude Code Usage

Claude Code was used as a parallel design assistant to review the smallest safe convergence plan for saved-search replay. Final implementation, compatibility fixes, tests, and validation were completed in this workspace flow.
