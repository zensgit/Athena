# Phase 207 - Advanced Search Stats API - Development

## Date
2026-03-08

## Goal
- 对标 Alfresco 搜索可观测能力中的 stats/range 聚合侧，补齐 Athena 高级搜索的聚合统计 API。
- 在不改变现有搜索结果契约的前提下，提供可独立调用的统计视图（总命中 + 关键 facet/range 分布）。

## Implemented

### 1) Backend endpoint
- Updated `ecm-core/src/main/java/com/ecm/core/controller/SearchController.java`
  - Added `POST /api/v1/search/advanced/stats`
  - Input: `SearchRequest`（允许空 body）
  - Internal flow:
    - 将请求映射为 `FacetedSearchRequest`
    - 固定 `page=0,size=1`，`highlightEnabled=false`，`includeSuggestions=false`
    - 调用 `facetedSearchService.search(...)`
  - Output fields:
    - `query`, `normalizedQuery`, `hasFilters`, `totalHits`, `facetFieldCount`
    - `previewStatusStats`, `mimeTypeStats`, `createdByStats`, `fileSizeRangeStats`, `createdDateRangeStats`
    - `generatedAt`

### 2) Stable bucket ordering and normalization
- Added helper logic in controller:
  - filter presence detection
  - facet bucket normalization (null-safe + whitespace normalization)
  - stable sort (`count desc`, then `value asc`)

### 3) Tests
- Updated `ecm-core/src/test/java/com/ecm/core/controller/SearchControllerTest.java`
  - Added endpoint test for aggregation payload + ordering + request capture checks.
- Updated `ecm-core/src/test/java/com/ecm/core/controller/SearchControllerSecurityTest.java`
  - Added authenticated USER/ADMIN access tests for `/advanced/stats`
  - Admin path verifies service invocation.

## Impact
- 高级搜索从“结果列表 + facets”扩展到“独立 stats 视图”，便于前端和运维面板直接消费。
- 为后续 DSL 扩展（pivot/stats 组合）提供稳定 API 落点。
