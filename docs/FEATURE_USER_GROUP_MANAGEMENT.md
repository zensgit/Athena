# 功能开发报告：用户与组管理 (User & Group Management)

> **版本**: 1.0
> **日期**: 2025-12-10
> **状态**: ✅ 已完成

## 1. 概述

为了填补 `ECM_FEATURE_COMPARISON.md` 中 P1 级缺失功能（用户/组管理 API），我们实现了完整的用户和组管理模块。这允许管理员在不直接访问数据库或 Keycloak 控制台的情况下，直接通过 ECM API 管理本地用户映射和权限组。

## 2. 核心功能实现

### 2.1 服务层 (`UserGroupService`)

*   **用户管理**:
    *   `searchUsers(query)`: 支持按用户名或邮箱模糊搜索，用于权限设置时的自动补全。
    *   `createUser` / `updateUser`: 简单的 CRUD。
*   **组管理**:
    *   `createGroup` / `deleteGroup`: 创建和删除权限组（如 "Finance_Dept"）。
    *   `addUserToGroup` / `removeUserFromGroup`: 管理组成员关系。

### 2.2 API 接口

| 模块 | 方法 | 路径 | 描述 |
|------|------|------|------|
| **Users** | GET | `/api/v1/users?query=xxx` | 搜索用户 (Autocomplete) |
| **Users** | GET | `/api/v1/users/{username}` | 获取用户详情 |
| **Groups**| GET | `/api/v1/groups` | 列出所有组 |
| **Groups**| POST | `/api/v1/groups` | 创建组 |
| **Members**| POST | `/api/v1/groups/{g}/members/{u}` | 添加成员 |

## 3. 验证方法

```bash
# 1. 搜索用户
curl -H "Authorization: Bearer $TOKEN" "http://localhost:8080/api/v1/users?query=admin"

# 2. 创建一个新组
curl -X POST -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{"name": "Engineering", "displayName": "Engineering Department"}' \
  http://localhost:8080/api/v1/groups

# 3. 将用户加入组
curl -X POST -H "Authorization: Bearer $ADMIN_TOKEN" \
  http://localhost:8080/api/v1/groups/Engineering/members/john.doe
```

## 4. 价值

虽然系统支持 Keycloak 集成，但拥有本地的 API 接口对于前端实现“权限设置对话框” (Permission Dialog) 至关重要。前端现在可以通过调用 `/api/v1/users` 来实现用户搜索下拉框，而不是猜测用户名。
