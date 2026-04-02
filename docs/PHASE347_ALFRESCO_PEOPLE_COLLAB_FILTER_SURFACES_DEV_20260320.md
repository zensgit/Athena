# Phase 347 - Alfresco People Collaboration Filter Surfaces

## Goal

把 People Directory 的协作区块从“列表可看”推进到“列表可快速筛”，让用户能更快从 activity/comment 流里定位目标内容。

## Scope

- `Recent Activity` 增加本地 filter input
- `Authored Comments` 增加本地 filter input
- `Mentioned Comments` 增加本地 filter input
- 三个区块统一补 filtered empty-state

## Frontend Design

采用轻量本地过滤，不新增后端契约：

- `Recent Activity` 匹配 `title / summary / type / node / occurredAt`
- `Authored Comments` 匹配 `author / node / content / created / edited / mentionedUsers`
- `Mentioned Comments` 匹配 `author / node / content / created / edited / mentionedUsers`

实现方式：

- 复用 `useDeferredValue`
- 抽一个统一的文本匹配 helper
- 列表渲染切到 filtered result
- 无匹配时给出专用提示，而不是误报“没有数据”

## Files

- `ecm-frontend/src/pages/PeopleDirectoryPage.tsx`

## Result

Athena 的 People Directory 现在不只是协作入口页，也具备了轻量筛查能力，用户能从评论/活动流更快定位目标文档和讨论上下文。
