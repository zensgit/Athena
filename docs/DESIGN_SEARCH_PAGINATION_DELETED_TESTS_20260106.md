# Design: Search Pagination + Deleted Filter Tests (2026-01-06)

## Goal
- Validate Elasticsearch-backed search pagination with sorted results and ensure deleted documents are excluded.

## Approach
- Add an ES integration test that indexes a larger dataset, includes deleted documents, and verifies:
  - paging behavior for page 1 with size 10
  - deterministic name sorting
  - deleted documents are not returned

## Files
- ecm-core/src/test/java/com/ecm/core/search/SearchAclElasticsearchTest.java
