# Phase303 Alfresco Workflow Process Relations and Diagram Binary（开发设计）

## 1. 目标
- 对标 Alfresco workflow API 的 `processes/{id}`、`processes/{id}/tasks`、`process-definitions/{id}/image`。
- 把 Athena workflow 从“task/detail metadata”推进到“process-centric detail + active tasks + diagram preview”。
- 在前端任务页直接展示 process summary、current process tasks 和 diagram，而不再只停留在 XML 文本和 availability chip。

## 2. 对标来源
- `reference-projects/alfresco-community-repo/remote-api/src/main/java/org/alfresco/rest/workflow/api/processes/ProcessesRestEntityResource.java`
- `reference-projects/alfresco-community-repo/remote-api/src/main/java/org/alfresco/rest/workflow/api/processes/ProcessTasksRelation.java`
- `reference-projects/alfresco-community-repo/remote-api/src/main/java/org/alfresco/rest/workflow/api/processdefinitions/ProcessDefinitionsRestEntityResource.java`

Athena 本轮先落最小闭环：
- `GET /api/v1/workflows/processes/{processId}`
- `GET /api/v1/workflows/processes/{processId}/tasks`
- `GET /api/v1/workflows/definitions/{definitionId}/diagram`

## 3. 后端实现

### 3.1 WorkflowController
- 文件：`ecm-core/src/main/java/com/ecm/core/controller/WorkflowController.java`
- 新增接口：
  - `GET /definitions/{definitionId}/diagram`
  - `GET /processes/{processId}`
  - `GET /processes/{processId}/tasks`
- 新增 DTO：
  - `ProcessDetailResponse`
  - `ProcessTaskResponse`
- diagram binary 通过 `Content-Disposition: inline` 返回，按资源名自动推断媒体类型。

### 3.2 WorkflowService
- 文件：`ecm-core/src/main/java/com/ecm/core/service/WorkflowService.java`
- 新增：
  - `getProcessDetail(processInstanceId)`
  - `getProcessTaskDetails(processInstanceId)`
  - `getDefinitionDiagram(definitionId)`
- 细节：
  - process detail 同时结合 `RuntimeService` 和 `HistoryService`
  - 运行中流程优先读取 runtime variables，结束流程回退到 historic variables
  - process detail 返回 `definition/name/version/businessKey/startedBy/start/end/suspended/ended/variables`
  - diagram 从 deployment resource 直接流出，不要求额外静态 URL

### 3.3 后端测试
- 文件：`ecm-core/src/test/java/com/ecm/core/controller/WorkflowControllerTest.java`
- 新增覆盖：
  - definition diagram binary 返回
  - process detail 返回
  - process tasks relation 返回

## 4. 前端实现

### 4.1 workflowService 扩展
- 文件：`ecm-frontend/src/services/workflowService.ts`
- 新增类型：
  - `WorkflowProcessDetail`
  - `WorkflowProcessTask`
- 新增方法：
  - `getProcessDetail`
  - `getProcessTasks`
  - `getDefinitionDiagram`

### 4.2 TasksPage 升级
- 文件：`ecm-frontend/src/pages/TasksPage.tsx`
- 新增区块：
  - `Process Summary`
  - `Current Process Tasks`
  - `Diagram Preview`
- 行为：
  - 选择任务后并行加载 process detail / process tasks / definition detail / model / history
  - diagram 通过 blob URL 方式加载，失败时回退到 XML 预览和提示信息
  - 当前选中的 task 会在 process tasks 中高亮

## 5. 结果
- Athena workflow 视图已经开始接近 Alfresco 的 process/detail 关系式 API，而不再只是任务元数据。
- 前端已经具备 diagram binary 预览能力，后续若补流程实例级 image 或 task form model，可在同一页继续扩展。
