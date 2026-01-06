# Design: Advanced Search Include-Deleted Filter (2026-01-06)

## Goal
- Verify advanced search toggles the deleted filter based on `includeDeleted`.

## Approach
- Capture the Elasticsearch query for advanced search.
- Assert the deleted=false term filter is present by default and omitted when `includeDeleted` is true.

## Files
- ecm-core/src/test/java/com/ecm/core/search/SearchAclFilteringTest.java
