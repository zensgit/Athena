# Phase 342 - Alfresco Workflow History Filter Workspaces

## Goal

把 workflow history 从只读列表继续推进成可筛查的工作台，在 task workbench 和 process browser 两个主界面都能快速定位目标流程记录。

## Scope

- `TasksPage` 的 `Workflow History` 增加 filter input
- `WorkflowProcessesPage` 的 `Workflow History` 增加 filter input
- 两个面板统一补充 filtered empty state

## Frontend Design

历史过滤统一按前端本地字段匹配：

- `businessKey`
- `processDefinitionKey`
- `processDefinitionName`
- `startedBy`
- `decision`
- `decisionLabel`
- `startComment`
- `comment`
- `reviewedBy`

设计原则：

- 250ms debounce，避免每次击键都重渲染完整历史面板
- 有 history 但无匹配时，明确显示 `No workflow history entries match the current filter.`
- 保留原始 workflow history 列表结构，不引入新 backend contract

## Files

- `ecm-frontend/src/pages/TasksPage.tsx`
- `ecm-frontend/src/pages/WorkflowProcessesPage.tsx`

## Result

Athena 的 workflow 历史排查已经不再依赖人工滚动浏览，task workbench 和 process browser 都具备了更高效的 history triage 能力。
