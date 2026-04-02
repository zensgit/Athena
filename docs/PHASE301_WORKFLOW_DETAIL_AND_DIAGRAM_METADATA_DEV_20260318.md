# Phase301 Workflow Detail and Diagram Metadata（开发设计）

## 1. 目标
- 在现有 `definitions / tasks / document history` 基础上，补齐 workflow 详情能力：
  - task detail
  - process definition detail
  - BPMN XML/model metadata
  - diagram 可用性 metadata
- 将任务页升级为可视化查看 workflow detail/history 的入口，而不是仅展示任务列表。

## 2. 后端实现

### 2.1 Workflow Controller
- 文件：`ecm-core/src/main/java/com/ecm/core/controller/WorkflowController.java`
- 新增接口：
  - `GET /api/v1/workflows/tasks/{taskId}`
  - `GET /api/v1/workflows/definitions/{definitionId}`
  - `GET /api/v1/workflows/definitions/{definitionId}/model`
- 保留现有接口：
  - `GET /definitions`
  - `GET /tasks/my`
  - `POST /tasks/{taskId}/complete`
  - `GET /document/{documentId}/instances`
  - `GET /document/{documentId}/history`

### 2.2 Workflow Service
- 文件：`ecm-core/src/main/java/com/ecm/core/service/WorkflowService.java`
- 新增：
  - `getTaskDetail(taskId)`
  - `getDefinitionDetail(definitionId)`
  - `getDefinitionModel(definitionId)`
- 细节：
  - task detail 复用 `TaskService` + `RuntimeService` + `RepositoryService`
  - definition detail 返回 resource names / diagram availability / BPMN XML availability
  - model endpoint 从 `repositoryService.getProcessModel(definitionId)` 读取 BPMN XML

### 2.3 后端测试
- 文件：`ecm-core/src/test/java/com/ecm/core/controller/WorkflowControllerTest.java`
- 新增覆盖：
  - workflow definition detail + model metadata
  - workflow task detail
  - my tasks 列表映射
  - document history 返回映射
  - start approval 正常返回

## 3. 前端实现

### 3.1 workflow service 扩展
- 文件：`ecm-frontend/src/services/workflowService.ts`
- 新增类型：
  - `WorkflowTaskDetail`
  - `WorkflowDefinitionDetail`
  - `WorkflowDefinitionModel`
  - `WorkflowHistoryItem`
- 新增方法：
  - `getTaskDetail`
  - `getDefinitionDetail`
  - `getDefinitionModel`
  - `getDocumentHistory` 类型化返回

### 3.2 Tasks Page 增强
- 文件：`ecm-frontend/src/pages/TasksPage.tsx`
- 调整：
  - 左侧任务列表保持不变
  - 右侧新增 task detail card
  - 右侧新增 workflow definition card
  - 右侧新增 workflow history card
  - 支持展示 BPMN XML 预览

## 4. 结果
- 任务页从“待办列表”升级为“待办列表 + 工作流详情 + 历史 + 模型元数据”。
- 后端保留最小实现，不强依赖 diagram PNG 资源；通过 metadata + XML 已足够支撑后续 diagram 可视化切片。
