# Phase 344 - Alfresco Workflow History Scopes And Task Variable Filter

## Goal

继续把 Athena 的 workflow workbench 从“可查看”推进到“可快速筛查、可治理”的工作台。

## Scope

- `TasksPage` 的 `Workflow History` 增加 quick scopes
- `WorkflowProcessesPage` 的 `Workflow History` 增加 quick scopes
- `TasksPage` 的 `Process Variables` 增加本地过滤输入

## Frontend Design

### Workflow History Quick Scopes

两个 workflow history 面板统一支持：

- `All`
- `Running`
- `Ended`
- `Approved`
- `Rejected`

行为约束：

- scope 和 text filter 叠加生效
- scope chip 直接显示命中数量
- 无匹配时显示明确 empty state，而不是退回原始列表

### Task Workbench Variable Filter

`TasksPage` 的 `Process Variables` 现在支持：

- 按 `name`
- `type`
- `scope`
- `value`

做本地过滤。

这让 task context 下的 variable governance 更适合真实排障场景，尤其在流程变量较多时不需要切去 process browser 再查找。

## Files

- `ecm-frontend/src/pages/TasksPage.tsx`
- `ecm-frontend/src/pages/WorkflowProcessesPage.tsx`

## Result

Athena 的 workflow workbench 进一步超出基础对标：不仅能看历史和变量，还能在任务页和流程页做更快的 triage 与治理。
