# Phase 339 - Alfresco Workflow Task Workbench Variable Governance

## Goal

把 process variable 治理能力从独立 process browser 扩到任务工作台，让审批/运维用户在 task context 下直接治理 runtime 变量。

## Scope

- 复用既有 workflow process variable write API
- 在 `TasksPage` 的 `Process Variables` 面板增加：
  - `Set variable`
  - `Edit`
  - `Delete`
- 写入后自动刷新当前 task/process detail

## Frontend Design

`TasksPage` 现在支持：

- 基于当前选中 task 对应的 `processInstanceId` 打开 variable editor
- JSON 值自动解析，普通文本按字符串存储
- 仅对可治理场景显示按钮：
  - admin
  - process starter
  - active process

这样避免把历史流程误导成可写数据。

## Files

- `ecm-frontend/src/pages/TasksPage.tsx`

## Result

Athena 的 workflow task workbench 不再只是查看流程上下文，而是具备了直接的 runtime variable governance 能力，减少了“先切到 process browser 再编辑”的往返。
