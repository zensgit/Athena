# Design: Search ACL Regression Coverage (2026-01-10)

## Goal
- Add targeted regression coverage for ACL-filtered search results across backend and E2E flows.

## Approach
- Backend: extend search ACL unit tests to cover empty permission outcomes and available facet filtering.
- E2E: add a role-based search scenario to ensure viewer users cannot see explicitly restricted documents.

## Impact
- Prevents regressions where unauthorized results or facets leak into search responses.
- Confirms UI search results are filtered for viewer roles when ACL denies read access.

## Files
- ecm-core/src/test/java/com/ecm/core/search/SearchAclFilteringTest.java
- ecm-frontend/e2e/search-view.spec.ts
