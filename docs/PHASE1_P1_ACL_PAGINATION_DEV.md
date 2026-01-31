# Phase 1 P1 - ACL Pagination Fix (Development)

## Goal
Prevent post-pagination ACL filtering from hiding documents by pushing ACL filtering into Elasticsearch queries and indexing effective read permissions.

## Summary of Changes
- Index effective read permissions into Elasticsearch documents.
- Apply permission filter in search and faceted queries (non-admins only).
- Keep post-query authorization filtering for safety, but rely on ES to return only readable docs for pagination accuracy.
- Extend authorities resolution to include JWT roles for the current user.
- Update search ACL tests to account for permission-aware indexing and query filters.

## Implementation Details
- Compute effective read authorities per node (`SecurityService.resolveReadAuthorities`).
- Store read authorities in `NodeDocument.permissions` during index and pipeline updates.
- Apply `permissions` terms filter in:
  - `FullTextSearchService.buildFullTextQuery`
  - `FullTextSearchService.buildAdvancedQuery`
  - `FacetedSearchService.buildFacetedQuery`
- Test fixtures updated to include permissions and consistent total hit expectations.

## Files Touched
- `ecm-core/src/main/java/com/ecm/core/service/SecurityService.java`
- `ecm-core/src/main/java/com/ecm/core/search/SearchIndexService.java`
- `ecm-core/src/main/java/com/ecm/core/pipeline/processor/SearchIndexProcessor.java`
- `ecm-core/src/main/java/com/ecm/core/search/FullTextSearchService.java`
- `ecm-core/src/main/java/com/ecm/core/search/FacetedSearchService.java`
- `ecm-core/src/test/java/com/ecm/core/search/SearchAclFilteringTest.java`
- `ecm-core/src/test/java/com/ecm/core/search/SearchAclElasticsearchTest.java`

## Index Rebuild
Rebuilt search index after changes.
- Result: `documentsIndexed=253`
