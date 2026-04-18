# P5 PR-79 RM Record-Category-Path Facet Filter Foundation Design

## Scope

`PR-79` adds the minimum end-to-end `recordCategoryPath` facet and filter foundation on top of the shipped `P5 PR-76/77/78` RM search/index work.

It stays deliberately narrow:

- no new backend endpoint
- no migration
- no new evidence surface
- no dependency on admin-only RM APIs

The slice turns already-projected RM filing data into a real search/facet constraint on ordinary search surfaces.

## Problem

Before this slice:

- browse, search, and advanced search could already show record declaration state
- search results already carried `recordCategoryPath`
- operators still could not facet or filter by that RM filing path

That meant RM category metadata was visible in projection, but not actionable as a search narrowing control.

## Design

### 1. Reuse existing indexed RM projection

The slice does not introduce a dedicated RM category endpoint or a second source of truth.

Instead it reuses the already indexed RM projection in Elasticsearch:

- `properties.rm:recordCategoryPath`

Both full-text search and faceted search now accept:

- `filters.recordCategoryPaths: string[]`

and treat it as an additive exact-match filter over the indexed RM category path.

### 2. Add record-category facets to the existing search facet pipeline

`FacetedSearchService` now includes `recordCategoryPath` in the default facet set and supports:

- aggregation-backed facet buckets when Elasticsearch aggregations are available
- in-memory fallback counting from `NodeDocument.properties["rm:recordCategoryPath"]` when aggregation fallback is used

This keeps the new facet on the same path as:

- `mimeType`
- `createdBy`
- `tags`
- `categories`

instead of opening a dedicated RM browse/search API.

### 3. Preserve current search controller semantics

`SearchController` now treats `recordCategoryPaths` as a normal advanced filter:

- `hasAnyFilters(...)`
- `filterCounts`
- filter copying/normalization

That keeps diagnostics and advanced search context consistent with the rest of the search filter surface.

### 4. Thin frontend consumption on existing search surfaces

Frontend work stays on existing search surfaces only:

- `SearchDialog`
- `SearchResults`
- `AdvancedSearchPage`

All three now reuse the existing search/facet path:

- `SearchDialog` loads `recordCategoryPath` from `/search/facets`, preserves it through prefill and saved-search serialization, and submits it on the existing `searchNodes(...)` path
- `SearchResults` and `AdvancedSearchPage` consume the returned `recordCategoryPath` facet buckets and allow multi-select filtering

URL/saved-search/prefill round-trip is extended through:

- `advancedSearchStateUtils`
- `savedSearchUtils`
- `nodeService`
- `searchPrefillUtils`
- `uiSlice`

## Files

### Backend

- `ecm-core/src/main/java/com/ecm/core/search/SearchFilters.java`
- `ecm-core/src/main/java/com/ecm/core/search/FullTextSearchService.java`
- `ecm-core/src/main/java/com/ecm/core/search/FacetedSearchService.java`
- `ecm-core/src/main/java/com/ecm/core/controller/SearchController.java`
- `ecm-core/src/test/java/com/ecm/core/controller/SearchControllerTest.java`

### Frontend

- `ecm-frontend/src/types/index.ts`
- `ecm-frontend/src/services/nodeService.ts`
- `ecm-frontend/src/components/search/SearchDialog.tsx`
- `ecm-frontend/src/pages/SearchResults.tsx`
- `ecm-frontend/src/pages/AdvancedSearchPage.tsx`
- `ecm-frontend/src/utils/advancedSearchStateUtils.ts`
- `ecm-frontend/src/utils/searchPrefillUtils.ts`
- `ecm-frontend/src/utils/savedSearchUtils.ts`
- `ecm-frontend/src/store/slices/uiSlice.ts`
- `ecm-frontend/src/services/nodeService.recordProjection.test.ts`
- `ecm-frontend/src/utils/advancedSearchStateUtils.test.ts`
- `ecm-frontend/src/utils/searchPrefillUtils.test.ts`
- `ecm-frontend/src/utils/savedSearchUtils.test.ts`

## Non-Goals

- no RM category picker service
- no admin-only `RecordsManagementController` dependency from search pages
- no new search endpoint
- no new RM evidence/audit surface beyond existing search + `Records Audit`
