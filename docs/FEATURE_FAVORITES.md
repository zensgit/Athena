# 功能开发报告：收藏夹 (Favorites)

> **版本**: 1.0
> **日期**: 2025-12-10
> **状态**: ✅ 已完成

## 1. 概述

为了提升用户的协作体验和常用文件的访问效率，我们借鉴了 Alfresco 和 Paperless 的设计，实现了**收藏夹 (Favorites)** 功能。用户可以将常用的文档或文件夹标记为星标（Favorite），并在统一的列表中快速访问。

## 2. 核心功能实现

### 2.1 数据模型

*   **Entity**: `Favorite`
    *   `id`: UUID
    *   `userId`: 用户标识 (String)
    *   `node`: 关联的文档/文件夹 (ManyToOne)
    *   `createdAt`: 收藏时间

### 2.2 服务层 (FavoriteService)

*   `addFavorite(UUID nodeId)`: 添加收藏，自动去重。
*   `removeFavorite(UUID nodeId)`: 取消收藏。
*   `getMyFavorites(Pageable pageable)`: 分页获取当前用户的收藏列表，按时间倒序排列。
*   `isFavorite(UUID nodeId)`: 检查特定节点是否已收藏。

### 2.3 API 接口

| 方法 | 路径 | 描述 |
|------|------|------|
| POST | `/api/v1/favorites/{nodeId}` | 添加收藏 |
| DELETE | `/api/v1/favorites/{nodeId}` | 取消收藏 |
| GET | `/api/v1/favorites` | 获取我的收藏列表 |
| GET | `/api/v1/favorites/{nodeId}/check` | 检查收藏状态 |

## 3. 验证方法

```bash
# 1. 获取文档列表以获取 ID
export TOKEN=...
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/nodes/path?path=/test.txt

# 2. 添加收藏
curl -X POST -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/favorites/{NODE_ID}

# 3. 查看收藏列表
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/favorites

# 4. 检查状态
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/favorites/{NODE_ID}/check

# 5. 取消收藏
curl -X DELETE -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/favorites/{NODE_ID}
```

## 4. 后续计划

*   **前端集成**: 在文件列表的操作栏添加“星标”图标。
*   **侧边栏入口**: 在前端侧边栏添加 "Favorites" 菜单，点击跳转到只显示收藏文件的列表页。