# Phase 350 - Alfresco Workflow Activity And Task History Quick Scopes

## Goal

把 workflow workbench 里最常用的两类追踪列表继续从“可搜索”推进到“可快速分层筛查”：

- `Process Activity Timeline`
- `Process Task History`

## Scope

- `TasksPage`
  - `Process Activity Timeline` 增加 quick scopes
  - `Process Task History` 增加 quick scopes
- `WorkflowProcessesPage`
  - `Process Activity Timeline` 增加 quick scopes
  - `Process Task History` 增加 quick scopes

## Frontend Design

### Process Activity Timeline

活动时间线采用统一 scopes：

- `All`
- `Running`
- `Ended`
- `Human`
- `System`

判定原则：

- `Running / Ended` 基于 `endTime`
- `Human / System` 基于 `taskId / assignee / activityType`

### Process Task History

历史任务采用统一 scopes：

- `All`
- `Assigned`
- `Unassigned`
- `Owned`
- `Outcome`

判定原则：

- `Assigned / Unassigned` 基于 `assignee`
- `Owned` 基于 `owner`
- `Outcome` 基于 `deleteReason`

### Interaction Principles

- quick scopes 与现有文本 filter 做 AND 叠加
- chips 显示当前本地集合计数
- 不新增 backend contract
- 无匹配时给出专用 empty-state，而不是误报“没有数据”

## Files

- `ecm-frontend/src/pages/TasksPage.tsx`
- `ecm-frontend/src/pages/WorkflowProcessesPage.tsx`

## Result

Athena 的 workflow 页面现在更适合做实例追踪、问题定位和流程复盘，而不只是看静态列表。
