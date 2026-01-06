# Design: Search Disabled Guards (2026-01-06)

## Goal
- Ensure search services short-circuit cleanly when search is disabled.

## Approach
- Add unit tests for `FullTextSearchService.search` and `FacetedSearchService.search` with `searchEnabled = false`.
- Assert empty responses and verify Elasticsearch is not called.

## Files
- ecm-core/src/test/java/com/ecm/core/search/SearchAclFilteringTest.java
