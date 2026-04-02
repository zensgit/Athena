# Phase 341 - Alfresco People Comment And Activity Quick Actions

## Goal

把 People Directory 从“资料页”继续推进成“协作入口页”，让用户能从评论和活动流直接进入文档预览、讨论和收藏动作。

## Scope

- `Authored Comments` 增加统一文档快捷动作
- `Mentioned Comments` 增加统一文档快捷动作
- `Recent Activity` 增加统一文档快捷动作
- 对 folder 记录保留原有 `browse` fallback，而不是强行走预览

## Frontend Design

`PeopleDirectoryPage` 新增统一 helper：

- `renderCommentQuickActions(...)`
- `renderActivityQuickActions(...)`

统一动作包括：

- `Preview`
- `Discuss`
- `Add favorite` / `Remove favorite`（document record 且当前用户可编辑时）

同时保留：

- folder 类型仍直接跳转 `browse`
- discuss 动作对 comments 继续带上 `commentId`
- discuss 动作对 activity 可预填 profile owner mention

## Files

- `ecm-frontend/src/pages/PeopleDirectoryPage.tsx`

## Result

People Directory 已从静态目录页升级为更直接的 collaboration launcher，用户可以从人出发快速回到文档和讨论上下文，这一点已经明显超出基础对标范围。
