# Design: Suggested Filters Disabled Guard (2026-01-06)

## Goal
- Capture behavior of suggested filters when search is disabled.

## Approach
- Disable search in `FacetedSearchService` and assert only date-range suggestions are returned.
- Verify Elasticsearch is not queried.

## Files
- ecm-core/src/test/java/com/ecm/core/search/SearchAclFilteringTest.java
