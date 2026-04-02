# Phase 355 - Alfresco People Workspace Launcher Triage

## Goal

把 People Directory 中最常用的三类 launcher 列表继续推进成可筛查的工作台：

- `Favorites`
- `Sites`
- `Favorite Sites`

## Scope

- 三个区块统一增加本地 filter input
- 三个区块统一增加 node-type quick scopes
- 三个区块统一显示 `filtered/visible` 计数
- 三个区块统一补 filtered empty-state

## Frontend Design

这批能力不新增 backend contract，全部复用已加载的人员资料数据做本地 triage。

### Favorites

- 支持按 `name / nodeId / nodeType / time` 过滤
- 支持 `All / Documents / Folders / Other` quick scopes
- 保留既有 `Preview / Discuss / Add favorite / Remove favorite / Open workspace`

### Sites / Favorite Sites

- 支持按 `title / role / visibility / path / folder type` 过滤
- 支持 `All / Documents / Folders / Other` quick scopes
- 保留既有 `Open / Add favorite / Remove favorite / Request access / Edit request / Withdraw`

## Files

- `ecm-frontend/src/pages/PeopleDirectoryPage.tsx`

## Result

Athena 的 People Directory 已从“能启动协作入口”继续推进到“能快速筛查协作入口”，用户可以先缩小 favorite/site 集合，再进入目标 workspace 或文档上下文。
