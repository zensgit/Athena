# Design: Search ACL Edge Case Tests (2026-01-06)

## Goal
- Cover ACL filtering edge cases for search services: admin bypass, missing node IDs, and deleted nodes.

## Approach
- Add unit tests around FullTextSearchService and FacetedSearchService filtering paths.
- Validate that admin role bypasses repository ACL checks.
- Validate that invalid/blank IDs and missing nodes are filtered safely without permissions checks.

## Files
- ecm-core/src/test/java/com/ecm/core/search/SearchAclFilteringTest.java
