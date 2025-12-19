# 功能开发报告：保存的搜索 (Saved Searches)

> **版本**: 1.0
> **日期**: 2025-12-10
> **状态**: ✅ 已完成

## 1. 概述

为了提升重度用户在海量文档中检索信息的效率，我们实现了 **保存的搜索 (Saved Searches)** 功能。用户可以将复杂的查询组合（如“最近一周上传的合同且包含‘付款’关键词”）保存为快捷方式，随时一键调用。

## 2. 核心功能实现

### 2.1 数据模型

*   **Entity**: `SavedSearch`
    *   `id`: UUID
    *   `userId`: 用户标识 (String)
    *   `name`: 搜索名称 (e.g., "Monthly Invoices")
    *   `queryParams`: 搜索参数 (JSONB)，存储完整的 `FacetedSearchRequest` 对象。
    *   `createdAt`: 创建时间

### 2.2 服务层 (SavedSearchService)

*   `saveSearch(String name, Map params)`: 保存搜索配置，自动去重。
*   `getMySavedSearches()`: 获取当前用户的所有保存项。
*   `executeSavedSearch(UUID id)`: 核心逻辑。它读取保存的 JSON 配置，通过 Jackson 转换为 `FacetedSearchRequest` 对象，然后直接调用 `FacetedSearchService.search()` 执行搜索。这确保了保存的搜索逻辑与实时搜索完全一致。

### 2.3 API 接口

| 方法 | 路径 | 描述 |
|------|------|------|
| POST | `/api/v1/search/saved` | 保存当前搜索条件 |
| GET | `/api/v1/search/saved` | 列出我的保存搜索 |
| DELETE | `/api/v1/search/saved/{id}` | 删除 |
| GET | `/api/v1/search/saved/{id}/execute` | 直接执行保存的搜索 |

## 3. 验证方法

```bash
# 1. 保存一个复杂搜索
curl -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{
    "name": "Recent PDF Contracts",
    "queryParams": {
      "query": "contract",
      "filters": {
        "mimeTypes": ["application/pdf"],
        "dateFrom": "2024-01-01T00:00:00"
      }
    }
  }' \
  http://localhost:8080/api/v1/search/saved

# 2. 列出保存的搜索
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/search/saved

# 3. 执行搜索 (获取 ID 后)
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/search/saved/{ID}/execute
```

## 4. 后续计划

*   ✅ **前端集成已完成**：Advanced Search 弹窗支持 “Save Search”，并新增 `/saved-searches` 页面用于查看/执行/删除保存的搜索。
*   可选增强：支持“共享搜索”（管理员创建全局保存搜索），以及把已保存搜索加入侧边栏常驻快捷入口。
