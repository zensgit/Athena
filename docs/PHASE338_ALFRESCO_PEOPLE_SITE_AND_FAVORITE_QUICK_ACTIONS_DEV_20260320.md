# Phase 338 - Alfresco People Site And Favorite Quick Actions

## Goal

把 people workspace 里的站点协作动作前移到列表项本身，减少“先打开编辑器再操作”的额外步骤。

## Scope

- `Sites` 列表增加 favorite site 快捷动作
- `Favorite Sites` 列表增加 membership request 快捷动作
- 复用现有 people APIs，不改后端契约

## Frontend Design

`PeopleDirectoryPage` 现在具备：

- `Sites` 列表中针对当前可编辑用户的 `Add to favorites` / `Remove favorite`
- `Favorite Sites` 列表中：
  - 若该站点不在当前 membership 内且无 `PENDING` 请求，显示 `Request access`
  - 若已有 pending request，显示 `Edit request` / `Withdraw`

实现策略：

- 对已存在 API 直接复用
- `Sites` 列表本身没有完整 workspace `nodeId` 时，直接打开现有 favorite-site editor 并预填站点名，减少改后端需求

## Files

- `ecm-frontend/src/pages/PeopleDirectoryPage.tsx`

## Result

People Directory 已从“信息查看页”进一步推进为“站点协作入口页”，在用户自助和协作操作上比纯对标实现更直接。
