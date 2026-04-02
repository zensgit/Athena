# Phase 337 - Alfresco Workflow Process Variable Write Surfaces

## Goal

在 Athena workflow process browser 上补齐可治理的 process variable 写入口，超越只读变量列表。

## Scope

- 新增 `PUT /api/v1/workflows/processes/{processId}/variables/{variableName}`
- 新增 `DELETE /api/v1/workflows/processes/{processId}/variables/{variableName}`
- 在 `WorkflowProcessesPage` 提供变量新增 / 编辑 / 删除 UI

## Backend Design

`WorkflowService` 新增：

- `upsertProcessVariable(...)`
- `deleteProcessVariable(...)`

约束：

- 仅允许 active runtime process 修改变量
- admin 始终可写
- process starter 也可写
- completed process 会被拒绝，避免误导为可变历史数据

## Frontend Design

`WorkflowProcessesPage` 的 `Process Variables` 面板现在支持：

- `Set variable`
- `Edit`
- `Delete`
- JSON 值自动解析，普通文本按字符串保存

变量写入后会刷新当前 process detail 和 process browser 列表。

## Files

- `ecm-core/src/main/java/com/ecm/core/controller/WorkflowController.java`
- `ecm-core/src/main/java/com/ecm/core/service/WorkflowService.java`
- `ecm-core/src/test/java/com/ecm/core/controller/WorkflowControllerTest.java`
- `ecm-frontend/src/services/workflowService.ts`
- `ecm-frontend/src/pages/WorkflowProcessesPage.tsx`

## Result

Athena 不再只是展示 workflow variables，而是具备了更偏运维/平台化的变量治理能力。
