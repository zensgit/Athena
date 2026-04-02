# Phase 210 - Advanced Search Pivot Panel UI - Development

## Date
2026-03-08

## Goal
- 将 pivot 统计接入高级搜索页面，提供直观的 “Preview Status × MIME Type” 热点视图。
- 保证前后端接口迭代期间的契约兼容，避免因字段形态变化导致面板空白。

## Implemented

### 1) Frontend service integration
- Updated `ecm-frontend/src/services/nodeService.ts`
  - Added pivot stats request/response types.
  - Added `getAdvancedSearchPivotStats(criteria)` calling `/search/advanced/stats/pivot`.
  - Added response compatibility adapter:
    - 支持后端返回 `cells`（扁平）或 `matrix`（行列）两种形态
    - 统一归一化为前端消费的 `cells` 结构。

### 2) Advanced search page panel
- Updated `ecm-frontend/src/pages/AdvancedSearchPage.tsx`
  - `handleSearch` now runs three parallel requests:
    - search results
    - advanced stats
    - pivot stats
  - pivot/stats 任一失败均降级为 `null`，不影响主搜索结果展示。
  - Added pivot chips section under `Search Stats`:
    - 显示 `Status × MIME` top cells
    - 无数据时展示降级提示文本。

### 3) Mocked E2E updates
- Updated `ecm-frontend/e2e/advanced-search-preview-batch-scope.mock.spec.ts`
  - Added `POST /search/advanced/stats/pivot` mock for both scenarios.
  - Added pivot UI assertions.
  - Added `/search/faceted` mock branch，确保走 faceted path 时用例仍可稳定通过。

## Impact
- 高级搜索面板具备“结果 + stats + pivot”三层视角，定位失败热点更快。
- 前后端契约兼容层降低联调期风险，避免版本错位造成空白或误报。
