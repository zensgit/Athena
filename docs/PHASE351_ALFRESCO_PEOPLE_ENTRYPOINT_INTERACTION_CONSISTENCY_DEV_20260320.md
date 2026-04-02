# Phase 351 - Alfresco People Entrypoint Interaction Consistency

## Goal

把 People Directory 里的 `site / favorite / comment` 三类入口继续收敛到同一套交互规则，减少“这块能点整行、那块只能点按钮”的割裂感。

## Scope

- `Favorites`
  - 行级点击统一到默认入口
  - secondary text 明确展示 node kind 与默认动作
- `Sites`
  - 行级点击统一打开 workspace
  - secondary text 补 `Workspace folder / Open in browser`
- `Favorite Sites`
  - 行级点击统一打开 workspace
  - secondary text 补 node kind / action hint / request state
- `Authored Comments`
  - 整行点击默认 `Preview`
  - secondary text 补 node kind / action hint / nodeId
- `Mentioned Comments`
  - 整行点击默认 `Preview`
  - secondary text 补 node kind / action hint / nodeId

## Frontend Design

统一抽象了两类辅助信息：

- `getNodeKindLabel`
  - `Workspace folder`
  - `Document`
  - `Linked item`
- `getNodeActionHint`
  - `Open in browser`
  - `Preview / Discuss`

交互原则：

- row click 负责默认进入目标内容
- quick action buttons 继续保留，负责更明确的二级动作
- folder/document fallback 不靠用户猜，而是直接在 secondary text 中说明
- 不新增 backend contract

## Files

- `ecm-frontend/src/pages/PeopleDirectoryPage.tsx`

## Result

Athena 的 People Directory 更接近一个真正的协作导航工作台：用户可以先看清“这是什么对象、点下去会发生什么”，再决定走 row click 还是 quick actions。
