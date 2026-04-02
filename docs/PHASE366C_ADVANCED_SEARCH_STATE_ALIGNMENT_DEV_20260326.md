# Phase 366C: Advanced Search State Alignment

## Goal

Centralize `AdvancedSearchPage` URL parsing, template restore, criteria-key building, and request shaping into one shared helper so the unified search envelope path does not keep duplicating state semantics inside the page.

## Delivered

- Added shared helper: `ecm-frontend/src/utils/advancedSearchStateUtils.ts`
- Moved these concerns out of `AdvancedSearchPage`:
  - URL state parse
  - URL state serialize
  - template query-state resolve
  - criteria activity detection
  - fallback criteria key generation
  - search request criteria build
  - date-range to `modifiedFrom` conversion
- Updated `AdvancedSearchPage` to consume the shared helper for:
  - initial URL restore
  - URL sync after search
  - template apply
  - primary envelope request build
  - active preview filter restoration
- Updated `searchPrefillUtils` to reuse the same parsed advanced-search URL state instead of maintaining a separate date-range / number / list parsing path.

## Design

This slice converges at the **state contract** layer.

Why:

- `AdvancedSearchPage` had multiple locally defined parsers and builders that duplicated semantics already needed by other search entrypoints.
- `Phase366B` unified the network request, but page state still had separate URL/template/manual parsing rules.
- Pulling these rules into one helper reduces behavioral drift when the search envelope keeps expanding.

## Shared Helper Contract

The new helper exposes:

- `parseAdvancedSearchUrlState(search)`
- `buildAdvancedSearchUrlSearch(state)`
- `resolveTemplateQueryState(queryParamsRaw)`
- `buildSearchCriteriaFromAdvancedState(state, page, size)`
- `buildAdvancedSearchCriteriaKey(state)`
- `hasActiveAdvancedSearchCriteria(state)`
- `hasRestorableAdvancedSearchState(state)`
- `resolveModifiedFromDate(range)`

## Preserved Behaviour

- Query, preview status, date range, mime types, creators, tags, categories, and size filters keep the same URL keys.
- Template application still restores the same visible filter controls before executing search.
- Fallback-result criteria matching still keys off the same effective filter dimensions.
- Existing preview-batch and relation-detail workflows remain untouched.

## Why This Matters

Athena now has a cleaner bridge between:

- direct URL entry,
- saved/template search replay,
- manual page interaction,
- and the unified `/api/v1/search/query` envelope.

That is a detail-level improvement over the previous page-local implementation and is necessary groundwork for later saved-search / URL replay convergence.

## Claude Code Usage

Claude Code was used as a parallel design assistant to cross-check the smallest safe extraction boundary for `AdvancedSearchPage` state convergence. Final code changes, integration, and validation were completed in this workspace flow.
