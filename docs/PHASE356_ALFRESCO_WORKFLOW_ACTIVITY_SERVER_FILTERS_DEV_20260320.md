# Phase 356 - Alfresco Workflow Activity Server Filters

## Goal

把 workflow 的 `Process Activity Timeline` 从“只在前端做本地文本过滤”继续推进到“后端粗筛 + 前端 quick scopes 细筛”的排障工作台，降低长流程实例下 activity 列表噪音。

## Scope

- backend `GET /api/v1/workflows/processes/{processId}/activities` 增加可选筛选参数
  - `query`
  - `assignee`
  - `activityType`
- `TasksPage` 的 `Process Activity Timeline` 改为把现有 activity 搜索框透传到后端
- `WorkflowProcessesPage` 的 `Process Activity Timeline` 改为把现有 activity 搜索框透传到后端
- 修复 `WorkflowService` 中已存在的 workflow record 构造参数漂移，恢复后端可编译状态

## Backend Design

`WorkflowController` 为 process activity relation 暴露可选 query params，并把过滤委托给 `WorkflowService`。

`WorkflowService` 的筛选原则：

- `assignee`
  - 精确匹配 activity assignee
- `activityType`
  - 精确匹配 activity type
- `query`
  - 模糊匹配以下字段：
    - `activityName`
    - `activityId`
    - `activityType`
    - `executionId`
    - `taskId`
    - `assignee`

这样保持 contract 简洁，同时为后续扩展更细粒度 triage 字段预留空间。

## Frontend Design

### TasksPage

- 复用现有 `Filter activities` 输入框
- 250ms debounce 后把 `activitySearchQuery` 传给 `workflowService.getProcessActivities`
- 既有 quick scopes 继续在已加载 activity 集合上做本地细筛
- Activity 区块提示文案更新为 “server filters + local scopes”

### WorkflowProcessesPage

- 与 task workbench 保持同样交互模型
- process browser 中的 activity timeline 现在和 task workbench 一样，先由服务端缩小 activity 数据集，再由 quick scopes 做本地 triage

## Files

- `ecm-core/src/main/java/com/ecm/core/controller/WorkflowController.java`
- `ecm-core/src/main/java/com/ecm/core/service/WorkflowService.java`
- `ecm-core/src/test/java/com/ecm/core/controller/WorkflowControllerTest.java`
- `ecm-frontend/src/services/workflowService.ts`
- `ecm-frontend/src/pages/TasksPage.tsx`
- `ecm-frontend/src/pages/WorkflowProcessesPage.tsx`

## Result

Athena 的 workflow activity timeline 现在不再依赖纯前端全文过滤，而是先通过服务端 query 收缩 activity 集合，再结合前端 quick scopes 做轻量 triage；同时顺手修复了 workflow service 中已存在的 record 构造漂移，恢复了该工作面的后端验证链路。
