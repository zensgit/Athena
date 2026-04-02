# Phase 296 - Alfresco 对标：Saved Search Governance Templates（Dev）

## Date
- 2026-03-13

## Goal
- 提供可复用的“治理检索模板”能力，支持在 Advanced Search 中一键套用治理查询，减少人工拼装过滤条件成本。

## Scope

### Backend
- `SavedSearchService` 新增内置模板目录（静态 catalog）：
  - failed preview（7天）
  - unsupported preview（30天）
  - octet-stream risk
  - large documents（>=10MB）
  - pdf ready（7天）
  - cad failed（30天）
- 新增服务方法：
  - `listBuiltInTemplates(tag)`，支持按 tag（大小写无关）过滤。

- `SavedSearchController` 新增接口：
  - `GET /api/v1/search/saved/templates?tag=governance`
  - 返回字段：`id/name/description/queryParams/tags`

### Frontend
- `savedSearchService` 新增：
  - `SavedSearchTemplate` 类型
  - `listTemplates(tag?)`

- `AdvancedSearchPage` 新增：
  - `Governance Search Templates` 面板（结果区顶部）
  - 页面加载时拉取 `tag=governance` 模板
  - 模板 Chip 一键套用：
    - `query`
    - `previewStatus`
    - `dateRange`
    - `mimeTypes`
    - `creators`
    - `tags`
    - `categories`
    - `minSize`
    - `maxSize`
  - 套用后自动执行 `handleSearch(1, overrides)` 并提示成功 toast

## Tests Added
- `SavedSearchServiceTemplateTest`
- `SavedSearchControllerTemplateTest`

## Compatibility
- 不改变原有 saved search CRUD / execute / pin 语义。
- 模板能力为只读增量，失败时不影响现有搜索流程。
