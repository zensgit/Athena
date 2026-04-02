# Phase 336 - Alfresco Workflow Process Task History Relations

## Goal

补齐 workflow process 下的 completed task relation，让流程页和任务页都能直接查看流程级任务历史，而不只看 activity timeline。

## Scope

- 新增 `GET /api/v1/workflows/processes/{processId}/task-history`
- 返回流程下已结束的 historic tasks
- 在 `WorkflowProcessesPage` 与 `TasksPage` 增加 `Process Task History` 面板

## Backend Design

`WorkflowService` 新增 `getProcessTaskHistory(...)`：

- 校验 process instance 在 runtime 或 history 中存在
- 从 `HistoricTaskInstanceQuery` 读取该流程的历史任务
- 仅保留已结束任务
- 按 `endTime` / `startTime` 排序，输出稳定资源

`WorkflowController` 新增 relation 资源，响应字段包括：

- `id`
- `name`
- `assignee`
- `owner`
- `description`
- `taskDefinitionKey`
- `startTime`
- `endTime`
- `durationInMillis`
- `deleteReason`

## Frontend Design

`WorkflowProcessesPage` 与 `TasksPage` 现在都会在流程详情区显示：

- 历史任务名称
- assignee / owner
- start / end 时间
- duration
- delete reason

这让 Athena 对流程 traceability 的展示深度更接近 Alfresco workflow browser。

## Files

- `ecm-core/src/main/java/com/ecm/core/controller/WorkflowController.java`
- `ecm-core/src/main/java/com/ecm/core/service/WorkflowService.java`
- `ecm-core/src/test/java/com/ecm/core/controller/WorkflowControllerTest.java`
- `ecm-frontend/src/services/workflowService.ts`
- `ecm-frontend/src/pages/WorkflowProcessesPage.tsx`
- `ecm-frontend/src/pages/TasksPage.tsx`

## Result

Athena 现在不仅能看 active tasks 和 activities，还能看流程级 completed task history，流程排障与审计闭环更完整。
