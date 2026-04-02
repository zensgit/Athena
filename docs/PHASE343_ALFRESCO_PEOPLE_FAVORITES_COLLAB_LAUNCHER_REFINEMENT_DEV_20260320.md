# Phase 343 - Alfresco People Favorites Collab Launcher Refinement

## Goal

把 People Directory 的 Favorites 列表继续从“收藏列表”推进成“协作启动器”，让 favorite document 和 favorite folder 都有更直接的行动入口。

## Scope

- 文档型 favorite 统一切到 `renderDocumentQuickActions(...)`
- folder favorite 保留 `Open` / `Remove favorite` fallback
- 列表整行点击行为按 node type 对齐：
  - document -> preview
  - folder -> browse

## Frontend Design

`PeopleDirectoryPage` 的 Favorites 列表现在具备：

- document favorite：
  - `Preview`
  - `Discuss`
  - `Add favorite / Remove favorite`
- folder favorite：
  - `Open`
  - `Remove favorite`

实现上复用既有 helper 和 preview/discuss 打开逻辑，不引入新 service contract。

## Files

- `ecm-frontend/src/pages/PeopleDirectoryPage.tsx`

## Result

People Directory 现在不只是能从 comments/activity 回到文档协作面，favorites 本身也成为统一的协作入口，整体使用路径比 Alfresco 对标实现更直接。
