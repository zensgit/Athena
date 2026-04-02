# Phase 340 - Alfresco Workflow History Filter Surfaces

## Goal

把 workflow process task history 从“纯列表”推进到“可快速检索的 triage 面板”，补齐更深一层的历史排查效率。

## Scope

- `WorkflowProcessesPage` 的 `Process Task History` 增加前端过滤框
- `TasksPage` 的 `Process Task History` 增加同样的过滤框
- 统一支持 debounce query 与 filtered empty state

## Frontend Design

过滤范围覆盖常用排查字段：

- task name
- assignee
- owner
- task definition key
- delete reason

交互原则：

- 输入 250ms debounce
- 无过滤词时显示完整 history
- 有过滤词但无命中时显示定制 empty state，而不是误判成“流程没有历史”

## Files

- `ecm-frontend/src/pages/WorkflowProcessesPage.tsx`
- `ecm-frontend/src/pages/TasksPage.tsx`

## Result

Athena 现在在 process browser 和 task workbench 两个 workflow 主界面里都具备更高效的历史筛查能力，比单纯对标实现更适合排障和运营值守。
