# Phase 334 - Alfresco Workflow Task Transitions And Delegation

## Goal

把 Athena 的任务操作从分散的 `claim/unclaim/assign/complete` API 提升到更接近 Alfresco 的统一 state transition 模式，并补上 `delegated/resolved` 生命周期。

## Scope

- 新增 `PUT /api/v1/workflows/tasks/{taskId}`
- 支持 `completed / claimed / unclaimed / assigned / delegated / resolved`
- 保留旧的 task action endpoint 兼容现有调用方
- 在 `TasksPage` 增加 delegate / resolve 的真实交互入口

## Backend Design

`WorkflowService` 新增：

- `updateTask(...)` 统一路由 task transition
- `delegateTask(...)` 做 owner/assignee 权限和目标用户校验
- `resolveTask(...)` 校验 delegated pending 状态并回写 `resolvedBy`、`resolvedAt`

同时补齐了 runtime task 的 `delegationState`：

- task inbox summary 返回 `delegationState`
- task detail 返回 `delegationState`
- process task list 返回 `owner` + `delegationState`
- delegated task 的展示状态从普通 `ASSIGNED` 提升为 `DELEGATED`

## Frontend Design

`TasksPage` 现在：

- claim / release / approve / reject 改走统一 transition API
- 新增 `Delegate Task`
- delegated pending 任务新增 `Resolve Task`
- 列表、detail、process task list 都显示 owner / delegation 信息
- 原有旧 endpoint 仍保留，避免外部调用方被破坏

## Files

- `ecm-core/src/main/java/com/ecm/core/controller/WorkflowController.java`
- `ecm-core/src/main/java/com/ecm/core/service/WorkflowService.java`
- `ecm-core/src/test/java/com/ecm/core/controller/WorkflowControllerTest.java`
- `ecm-frontend/src/services/workflowService.ts`
- `ecm-frontend/src/pages/TasksPage.tsx`

## Result

Athena 现在具备统一 task state transition 入口，并且支持 delegated / resolved 生命周期，这一块已经明显超出此前“只支持审批完成”的窄流程实现。
