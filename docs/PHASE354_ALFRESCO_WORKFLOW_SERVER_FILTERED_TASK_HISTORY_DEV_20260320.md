# Phase 354 - Alfresco Workflow Server Filtered Task History

## Goal

把 workflow 的 `Process Task History` 从“纯前端二次筛选”推进成“后端粗筛 + 前端 quick scope 细筛”的工作台，减少长流程实例下的历史列表噪音。

## Scope

- backend `processes/{processId}/task-history` 增加更稳定的筛选入口
  - `query`
  - `assignee`
  - `taskDefinitionKey`
- `TasksPage` 的 `Process Task History` 接入上述服务端过滤
- `WorkflowProcessesPage` 的 `Process Task History` 接入上述服务端过滤
- 修复 `TasksPage` 中 process variable 写入后 detail 不一定重新加载的问题

## Backend Design

`WorkflowController` 和 `WorkflowService` 现在让 `task-history` relation 支持可选 request params，并在历史任务映射前完成筛选。

匹配原则：

- `query`
  - 模糊匹配 `name / assignee / owner / taskDefinitionKey / deleteReason`
- `assignee`
  - 精确匹配 assignee
- `taskDefinitionKey`
  - 精确匹配 task definition key

这样保持 contract 简洁，同时能覆盖最常见的 triage 维度。

## Frontend Design

### Task Workbench

`TasksPage` 的 `Process Task History` 新增三段过滤输入：

- `Filter history`
- `Assignee`
- `Definition key`

交互模式：

- 输入 250ms debounce
- 服务端过滤负责缩小数据集
- 既有 quick scopes 继续对已加载结果做本地细筛
- empty-state 文案按 “server filters + quick scope” 联合状态展示

同时把 detail load effect 改为监听 `detailRefreshToken`，确保 process variable save/delete 后当前 task/process detail 会重新拉取。

### Process Browser

`WorkflowProcessesPage` 采用同样的筛选模型，保证 process browser 与 task workbench 的 task-history triage 行为一致。

## Files

- `ecm-core/src/main/java/com/ecm/core/controller/WorkflowController.java`
- `ecm-core/src/main/java/com/ecm/core/service/WorkflowService.java`
- `ecm-core/src/test/java/com/ecm/core/controller/WorkflowControllerTest.java`
- `ecm-frontend/src/services/workflowService.ts`
- `ecm-frontend/src/pages/TasksPage.tsx`
- `ecm-frontend/src/pages/WorkflowProcessesPage.tsx`

## Result

Athena 的 workflow task/process workbench 现在不仅能看历史，还能先由后端缩小 task-history 数据集，再由前端 quick scopes 做轻量 triage；同时 task workbench 的 variable governance 刷新链路也补齐了。
