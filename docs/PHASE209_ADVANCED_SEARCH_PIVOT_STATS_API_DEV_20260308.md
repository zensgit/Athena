# Phase 209 - Advanced Search Pivot Stats API - Development

## Date
2026-03-08

## Goal
- 对标 Alfresco 搜索里的 pivot 聚合能力，在 Athena 高级搜索中补齐“状态 × MIME”透视统计 API。
- 在不改动主搜索结果契约的情况下，提供可独立调用的透视矩阵数据，支撑运维排障与批处理决策。

## Reference alignment
- Reference repo:
  - `reference-projects/alfresco-community-repo/remote-api/src/main/java/org/alfresco/rest/api/search/context/SearchRequestContext.java`
  - `reference-projects/alfresco-community-repo/remote-api/src/main/java/org/alfresco/rest/api/search/impl/ResultMapper.java`
- Athena strategy:
  - 先做可观测可落地的 bounded pivot API（状态 × MIME），保持请求/响应简单稳定。
  - 后续可在此基础上扩展到通用 field-to-field pivot 与 stats 关联。

## Implemented

### 1) Backend pivot endpoint
- Updated `ecm-core/src/main/java/com/ecm/core/controller/SearchController.java`
  - Added `POST /api/v1/search/advanced/stats/pivot`
  - Input: optional `SearchRequest`
  - Core flow:
    - 先基于 query + filters 取 `previewStatus` / `mimeType` 顶层 buckets
    - 按稳定排序（`count desc`, `value asc`）取 topN（上限 4×4）
    - 组合生成透视矩阵并返回每个 cell 的 count

### 2) Bounded matrix strategy
- Added bounded constants:
  - `MAX_ADVANCED_STATS_PIVOT_PREVIEW_STATUS = 4`
  - `MAX_ADVANCED_STATS_PIVOT_MIME_TYPE = 4`
- 通过上限控制最坏情况调用次数（`1 + 4*4 = 17`），避免查询风暴。

### 3) DTO and helpers
- Added pivot response DTOs:
  - `AdvancedSearchPivotStatsResponse`
  - `AdvancedSearchPivotMatrixRow`
  - `AdvancedSearchPivotMatrixCell`
- Added reusable helper logic:
  - bucket 归一化 + topN 截断
  - pivot filters 组合
  - matrix build function

### 4) Tests
- Updated `ecm-core/src/test/java/com/ecm/core/controller/SearchControllerTest.java`
  - Added deterministic pivot ordering + matrix value assertions
  - Added bounded internal call-count assertions
- Updated `ecm-core/src/test/java/com/ecm/core/controller/SearchControllerSecurityTest.java`
  - Added anonymous/USER/ADMIN access checks for `/advanced/stats/pivot`

## Impact
- Athena 高级搜索从 “stats” 进一步扩展到 “pivot”，可快速识别失败状态和 MIME 热点组合。
- 与预览失败批处理链路联动更直接：先看矩阵热点，再发起 targeted retry/rebuild。
