# Design: Available Facets Disabled Guard (2026-01-06)

## Goal
- Ensure `getAvailableFacets` short-circuits when search is disabled.

## Approach
- Disable search in `FacetedSearchService` and assert an empty facets map.
- Verify no Elasticsearch calls are made.

## Files
- ecm-core/src/test/java/com/ecm/core/search/SearchAclFilteringTest.java
