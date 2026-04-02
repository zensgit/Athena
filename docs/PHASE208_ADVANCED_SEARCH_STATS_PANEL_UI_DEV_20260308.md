# Phase 208 - Advanced Search Stats Panel UI - Development

## Date
2026-03-08

## Goal
- 将 Phase 207 的 stats API 接入高级搜索页面，提供“结果外”的聚合可视化视图。
- 对标 Alfresco 搜索诊断体验，支持快速判断命中规模与主导类型分布。

## Implemented

### 1) Frontend service integration
- Updated `ecm-frontend/src/services/nodeService.ts`
  - Added:
    - `AdvancedSearchFacetStat` / `AdvancedSearchStats` types
    - `getAdvancedSearchStats(criteria)` calling `/search/advanced/stats`
  - Refactored filter mapping to shared helper (`buildSearchFilters`) so `searchNodes` and stats API reuse同一筛选映射逻辑。

### 2) Advanced search page panel
- Updated `ecm-frontend/src/pages/AdvancedSearchPage.tsx`
  - `handleSearch` now executes:
    - main search
    - advanced stats (parallel; stats failure degrades gracefully)
  - Added `Search Stats` panel showing:
    - total hits + facet field count
    - preview status stats chips
    - top mimeType stats chips
    - top createdBy stats chips

### 3) E2E updates
- Updated `ecm-frontend/e2e/advanced-search-preview-batch-scope.mock.spec.ts`
  - Added mock route for `POST /api/v1/search/advanced/stats` in both scenarios.
  - Added assertions for `Search Stats` panel visibility and sample bucket values.

## Impact
- 高级搜索页从“只看当前页结果”提升到“结果 + 聚合态势”双视角，缩短问题定位路径。
- 与已落地的 preview 批处理能力形成联动：先看 stats，再决定批处理动作。
