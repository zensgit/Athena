# Phase 333 - Alfresco Workflow Generic Process Start

## Goal

补齐 Alfresco 风格的通用流程启动能力，不再只支持文档审批专用入口。

## Scope

- 新增 `POST /api/v1/workflows/processes`
- 支持 `processDefinitionId` 或 `processDefinitionKey`
- 支持 `businessKey`、`variables`、`items`
- 在 `WorkflowProcessesPage` 提供可直接操作的启动入口

## Backend Design

`WorkflowController` 新增通用流程启动资源，`WorkflowService` 新增 `startProcess(...)`：

- 校验定义来源，要求 `processDefinitionId` 和 `processDefinitionKey` 二选一
- 解析并校验传入的 repository item UUID
- 自动补齐 `initiator`、`startFormSubmittedBy`、`startFormSubmittedAt`
- 有附件时补齐 `attachedItemIds`、`attachedItemNames`
- 单 item 场景下自动补齐 `documentId` / `nodeId` / `documentName`
- 未显式传 `businessKey` 且只有一个 item 时，默认回落为该 item id

## Frontend Design

`WorkflowProcessesPage` 新增 `Start Process` 对话框：

- 读取现有 workflow definitions
- 读取 definition start form model 作为字段提示
- 允许输入 `businessKey`
- 允许输入 `variables JSON`
- 允许批量输入 item UUID
- 启动成功后回到 `ACTIVE` 列表并刷新 browser

## Files

- `ecm-core/src/main/java/com/ecm/core/controller/WorkflowController.java`
- `ecm-core/src/main/java/com/ecm/core/service/WorkflowService.java`
- `ecm-core/src/test/java/com/ecm/core/controller/WorkflowControllerTest.java`
- `ecm-frontend/src/services/workflowService.ts`
- `ecm-frontend/src/pages/WorkflowProcessesPage.tsx`

## Result

Athena 现在具备了比“文档审批专用启动”更接近 Alfresco `POST /processes` 的平台化起点，并且已有前端入口可直接消费该能力。
