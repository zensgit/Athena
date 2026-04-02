# Phase 353 - Alfresco People Recent Activity Entry Consistency

## Goal

把 `Recent Activity` 也收敛到 People Directory 已经在 `sites / favorites / comments` 上采用的统一入口交互模型。

## Scope

- `Recent Activity`
  - 行级点击支持默认进入目标内容
  - secondary text 补 `node kind / action hint`
  - 行内增加明确的 `Click row to open` 提示
  - 保留右侧 quick actions

## Frontend Design

统一复用既有入口语义：

- folder 类 activity
  - row click 默认进入 browser
- document / linked item
  - row click 默认进入 preview
- 右侧 quick actions
  - 继续保留 `Preview / Discuss / Favorite / Open` 组合

文案层统一复用：

- `getNodeKindLabel`
- `getNodeActionHint`

从而让 Recent Activity 和 `Favorites / Sites / Favorite Sites / Comments` 看起来像同一套协作导航工作台。

## Files

- `ecm-frontend/src/pages/PeopleDirectoryPage.tsx`

## Result

Athena 的 People Directory 现在四类入口区块在“看起来像什么、点下去会发生什么、默认入口是什么”上更一致，降低了用户学习成本。
