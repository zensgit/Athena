# Phase 352 - Alfresco Workflow Activity Filter And Stats Surfaces

## Goal

把 workflow 的 `Process Activity Timeline` 从“只有 quick scopes”继续推进成“quick scopes + 文本过滤 + filtered/total 统计”的排障工作台。

## Scope

- `TasksPage`
  - `Process Activity Timeline` 增加本地文本过滤
  - `Process Activity Timeline` 增加 filtered/total 统计
  - `Process Task History` 头部增加 filtered/total 统计
- `WorkflowProcessesPage`
  - `Process Activity Timeline` 增加本地文本过滤
  - `Process Activity Timeline` 增加 filtered/total 统计
  - `Process Task History` 头部增加 filtered/total 统计

## Frontend Design

### Process Activity Timeline

过滤继续走本地数据，不新增 backend contract。匹配字段：

- `activityName`
- `activityId`
- `activityType`
- `executionId`
- `taskId`
- `assignee`

交互原则：

- 文本过滤与现有 quick scopes 做 AND 叠加
- 250ms debounce
- 头部显示 `filtered/total`
- scope 或 query 无匹配时，显示专用 empty-state

### Process Task History

本阶段不再增加新的 query contract，而是补工作台级统计可见性：

- 保持已有 quick scopes
- 头部显示 `filtered/total`

## Files

- `ecm-frontend/src/pages/TasksPage.tsx`
- `ecm-frontend/src/pages/WorkflowProcessesPage.tsx`

## Result

Athena 的 workflow 追踪页面现在更适合做实例排障和复盘，用户可以先按 scope 缩小，再通过文本快速锁定目标 activity/task。
