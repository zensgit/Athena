# Phase 349 - Alfresco People Collaboration Quick Scopes

## Goal

把 People Directory 的协作区块从“文本过滤”推进到“文本过滤 + quick scopes”，让 activity/comment 面板更像真正的协作工作台。

## Scope

- `Recent Activity`
  - 增加 node-type quick scopes
- `Authored Comments`
  - 增加 node-type quick scopes
- `Mentioned Comments`
  - 增加 node-type quick scopes

## Frontend Design

三个区块统一采用同一套 scope 模型：

- `All`
- `Documents`
- `Folders`
- `Other`

设计原则：

- scope 与现有文本 filter 做 AND 叠加
- 计数直接基于当前已加载列表计算
- 不新增后端接口
- 不改现有 `Preview / Discuss / Favorite / Open` quick actions

实现上抽了通用的：

- `getSectionNodeScope`
- `matchesSectionNodeScope`
- `getSectionNodeScopeCounts`
- `renderScopeChips`

从而保证三块协作区的一致性。

## Files

- `ecm-frontend/src/pages/PeopleDirectoryPage.tsx`

## Result

Athena 的 People Directory 进一步从“资料页”推进成“协作导航页”，用户可以先按 node type 缩小范围，再配合文本过滤快速定位目标内容。
