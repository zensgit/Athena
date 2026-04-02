# Phase 204 - Advanced Search Context Echo (DSL Observability) - Development

## Date
2026-03-08

## Goal
- 对标 Alfresco 搜索 DSL 的“请求可解释性”，为 Athena 增加高级搜索请求上下文回显接口。
- 让运维和测试可直接看到 query/filters/scope/page/facet 的归一化上下文，降低排查成本。

## Implemented

### 1) Backend: context echo endpoint
- Updated `ecm-core/src/main/java/com/ecm/core/controller/SearchController.java`:
  - Added:
    - `POST /api/v1/search/advanced/context`
  - Returns normalized diagnostics payload:
    - `normalizedQuery`
    - `hasFilters`
    - `activeFilterKeys`
    - `filterCounts` (per filter dimension)
    - paging/sort/highlight/facet context
    - scope fields (`folderId/includeChildren/path`)
    - `generatedAt`
  - Added helper methods for list counting, text presence checks, and whitespace normalization.

### 2) Tests
- Updated `ecm-core/src/test/java/com/ecm/core/controller/SearchControllerTest.java`:
  - Added `advancedSearchContextShouldReturnNormalizedDiagnostics`.
- Updated `ecm-core/src/test/java/com/ecm/core/controller/SearchControllerSecurityTest.java`:
  - Added authenticated-access coverage for `/search/advanced/context`.
