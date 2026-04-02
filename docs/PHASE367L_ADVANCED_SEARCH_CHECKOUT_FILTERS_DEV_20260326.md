# Phase367L Advanced Search Checkout Filters

## Goal

Turn checkout state into a first-class operator search capability by adding `checkedOut` and `checkoutUser` filters to Athena's advanced search stack.

## Scope

Backend:

- extend `SearchFilters` with `checkedOut` and `checkoutUser`
- apply both filters in `FullTextSearchService`
- include both filters in search filter-count diagnostics

Frontend:

- extend `SearchCriteria` and `nodeService.buildSearchFilters(...)`
- extend advanced-search URL/template state with `checkoutState` and `checkoutUser`
- add `Checkout` controls to `AdvancedSearchPage`
- propagate checkout filters into preview-governance flows that reuse the current search scope

## Design

### Filter model

The UI uses:

- `checkoutState: all | checkedOut | available`
- `checkoutUser: string`

These map to backend filters as:

- `checkedOut=true` when state is `checkedOut`
- `checkedOut=false` when state is `available`
- omitted when state is `all`
- `checkoutUser=<trimmed value>` when provided

### URL and template stability

The filter is persisted through:

- `parseAdvancedSearchUrlState`
- `buildAdvancedSearchUrlSearch`
- `resolveTemplateQueryState`
- `buildSearchCriteriaFromAdvancedState`

This keeps saved-search replay and URL refresh semantics aligned with the rest of advanced search.

### Search-scope reuse

Existing preview batch flows that run against "current advanced search scope" now also inherit checkout filters, so operator tooling stays consistent with the visible search state.

## Files

- `ecm-core/src/main/java/com/ecm/core/search/SearchFilters.java`
- `ecm-core/src/main/java/com/ecm/core/search/FullTextSearchService.java`
- `ecm-core/src/main/java/com/ecm/core/controller/SearchController.java`
- `ecm-frontend/src/types/index.ts`
- `ecm-frontend/src/services/nodeService.ts`
- `ecm-frontend/src/utils/advancedSearchStateUtils.ts`
- `ecm-frontend/src/utils/advancedSearchStateUtils.test.ts`
- `ecm-frontend/src/pages/AdvancedSearchPage.tsx`

## Risk

- this phase adds search/filter semantics only; it does not yet add checkout facets or result-row checkout chips inside `AdvancedSearchPage`
- checkout filter counts are exposed, but not yet elevated into separate analytics visualizations
