# Phase300 Alfresco Comments + People + Renditions（开发设计）

## 1. 目标
- 对齐 Alfresco 常用协作/目录/关系能力，补齐三组最小可用 API：
  - comments 资源接口
  - people 目录接口
  - node renditions relation 资源接口
- 将新建的 rendition relation API 接入前端现有 Advanced Search 关系详情卡片，避免后端能力悬空。

## 2. 后端实现

### 2.1 Comments API
- 文件：
  - `ecm-core/src/main/java/com/ecm/core/controller/CommentController.java`
  - `ecm-core/src/main/java/com/ecm/core/controller/UserCommentController.java`
  - `ecm-core/src/main/java/com/ecm/core/dto/CommentDto.java`
  - `ecm-core/src/main/java/com/ecm/core/dto/CommentReactionDto.java`
  - `ecm-core/src/main/java/com/ecm/core/dto/CommentStatisticsDto.java`
  - `ecm-core/src/main/java/com/ecm/core/dto/CreateCommentRequest.java`
  - `ecm-core/src/main/java/com/ecm/core/dto/UpdateCommentRequest.java`
  - `ecm-core/src/main/java/com/ecm/core/dto/CommentReactionRequest.java`
- 新增接口：
  - `POST /api/v1/nodes/{nodeId}/comments`
  - `GET /api/v1/nodes/{nodeId}/comments`
  - `GET /api/v1/nodes/{nodeId}/comments/tree`
  - `GET /api/v1/nodes/{nodeId}/comments/search?q=`
  - `GET /api/v1/nodes/{nodeId}/comments/statistics`
  - `PUT /api/v1/comments/{commentId}`
  - `DELETE /api/v1/comments/{commentId}`
  - `POST /api/v1/comments/{commentId}/reactions`
  - `DELETE /api/v1/comments/{commentId}/reactions`
  - `GET /api/v1/users/{username}/comments`
  - `GET /api/v1/users/{username}/mentioned-comments`
- 设计原则：
  - 直接复用现有 `CommentService` 业务能力，不重写 mention / reaction / statistics 逻辑。
  - 返回结构按前端 `commentService.ts` 已使用契约对齐。

### 2.2 People Directory API
- 文件：
  - `ecm-core/src/main/java/com/ecm/core/controller/PeopleController.java`
- 新增接口：
  - `GET /api/v1/people?query=&page=&size=`
  - `GET /api/v1/people/{username}`
  - `GET /api/v1/people/{username}/groups`
  - `GET /api/v1/people/{username}/favorites?page=&size=`
- 实现策略：
  - `search/get` 走 `UserGroupService`，与现有用户目录实现保持一致。
  - `groups/favorites` 走本地用户、收藏仓库聚合，先覆盖当前本地目录主场景。
  - 当前用户 favorites 复用 `FavoriteService.getMyFavorites`，其他用户走 `FavoriteRepository`。

### 2.3 Rendition Relation Resources
- 文件：
  - `ecm-core/src/main/java/com/ecm/core/controller/NodeController.java`
- 新增接口：
  - `GET /api/v1/nodes/{nodeId}/relations/renditions`
  - `GET /api/v1/nodes/{nodeId}/relations/renditions/{renditionId}`
- 返回内容：
  - 虚拟 relation resources，当前支持 `preview` 与 `thumbnail`
  - 字段：`renditionId/label/status/available/mimeType/url/downloadable/failureReason/failureCategory/previewLastUpdated/currentVersionLabel`
- 设计取舍：
  - 本轮只做只读资源枚举，不扩展 create/delete/download 动作。
  - 对 folder 等非文档节点返回空页或 404，维持资源语义清晰。

## 3. 前端实现

### 3.1 Node Relations Service 扩展
- 文件：`ecm-frontend/src/services/nodeService.ts`
- 新增：
  - `NodeRenditionRelation`
  - `NodeRenditionRelationSummary`
  - `getNodeRelationRenditions`
  - `getNodeRelationRendition`
  - `getNodeRenditionRelationSummary`

### 3.2 Advanced Search 关系详情增强
- 文件：`ecm-frontend/src/pages/AdvancedSearchPage.tsx`
- 调整：
  - 详情并行加载新增 `renditions`
  - 关系详情卡片新增 `Renditions:` 行
  - 展示格式：`Preview READY`、`Thumbnail FAILED/UNSUPPORTED` 等

### 3.3 People Service
- 文件：`ecm-frontend/src/services/peopleService.ts`
- 新增：
  - `search`
  - `get`
  - `getGroups`
  - `getFavorites`
- 目的：
  - 为 mention picker、approver picker、people profile/favorites 等后续 UI 提供稳定 API 契约。

## 4. 测试策略
- Comments：
  - `CommentControllerTest`
  - `CommentControllerSecurityTest`
- People：
  - `PeopleControllerTest`
  - `PeopleControllerSecurityTest`
- Renditions：
  - `NodeControllerRelationsTest`
- 前端：
  - lint 指向改动文件
  - production build 验证类型与打包通过

## 5. 对标价值
- 相比仅有基础文件/搜索/预览，本轮补齐了 Alfresco 中常见的三类外围协作能力：
  - 评论与讨论
  - 人员目录与个人收藏入口
  - 文档衍生 rendition 资源关系
- 这些能力同时为后续 workflow assignee picker、mentions、profile panel、rendition detail page 提供直接复用基础。
