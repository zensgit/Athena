# Design: Advanced Search CreatedBy Precedence (2026-01-06)

## Goal
- Ensure advanced search prefers `createdByList` when provided and falls back to `createdBy` otherwise.

## Approach
- Capture the Elasticsearch query for advanced search.
- Assert createdBy term filters include list values when both fields are set.
- Assert the single createdBy value is used when the list is empty.

## Files
- ecm-core/src/test/java/com/ecm/core/search/SearchAclFilteringTest.java
