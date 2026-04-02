# Phase302 Alfresco Async Batch Download and Collaboration Surfaces（开发设计）

## 1. 目标
- 对标 Alfresco download resource，将批量下载从同步直传升级为可轮询、可取消、可回放下载结果的 async task 模式。
- 把评论能力从“后端 API 已有”推进到主入口界面，在文档预览中直接可见。
- 把审批发起人选择从静态 mock 选项升级为 people directory 驱动，为后续 workflow assignee / mention / people picker 复用铺路。

## 2. 对标来源
- 参考 `reference-projects/alfresco-community-repo/remote-api/src/main/java/org/alfresco/rest/api/downloads/DownloadsEntityResource.java`
- 参考 `reference-projects/alfresco-community-repo/remote-api/src/main/java/org/alfresco/rest/api/Downloads.java`
- 参考 `reference-projects/alfresco-community-repo/remote-api/src/main/java/org/alfresco/rest/api/impl/DownloadsImpl.java`

Athena 本轮不照搬 Alfresco 的完整模型，而是先落地最小可用的 task 化下载资源：
- `POST /api/v1/nodes/download/batch-async`
- `GET /api/v1/nodes/download/batch-async`
- `GET /api/v1/nodes/download/batch-async/{taskId}`
- `POST /api/v1/nodes/download/batch-async/{taskId}/cancel`
- `GET /api/v1/nodes/download/batch-async/{taskId}/download`

## 3. 后端实现

### 3.1 BatchDownloadController
- 文件：`ecm-core/src/main/java/com/ecm/core/controller/BatchDownloadController.java`
- 新增 controller-local async task registry：
  - `Map<String, BatchDownloadAsyncTask> asyncTasks`
  - `Deque<String> asyncTaskOrder`
- 新增能力：
  - 接收批量下载请求并返回 `202 Accepted`
  - 维护任务状态：`QUEUED / RUNNING / CANCEL_REQUESTED / CANCELLED / COMPLETED / FAILED`
  - 在完成态暴露 `downloadUrl`
  - 对已完成临时 ZIP 做自动清理
- 保留既有同步端点 `GET /batch`，作为前端 fallback 路径。

### 3.2 BatchDownloadService
- 文件：`ecm-core/src/main/java/com/ecm/core/service/BatchDownloadService.java`
- 新增：
  - `inspectNodes(nodeIds)`：预估 `totalFiles / totalBytes`
  - `writeNodesAsZip(nodeIds, zipOut, progressListener)`：在写 ZIP 时回传进度并响应取消请求
  - `BatchDownloadProgressListener`
  - `BatchDownloadManifest`
  - `BatchDownloadArchiveSummary`
- 细节：
  - 仍沿用现有权限检查和 folder/document 递归
  - 对内容读取异常保持“单节点容错，不中断整包”
  - 在 async runner 中用临时文件持久化 ZIP，完成后开放下载

### 3.3 后端测试
- 文件：`ecm-core/src/test/java/com/ecm/core/controller/BatchDownloadControllerTest.java`
- 覆盖：
  - start -> poll -> list -> artifact download 的完整生命周期
  - 空 `nodeIds` 直接 `400`

## 4. 前端实现

### 4.1 nodeService 扩展
- 文件：`ecm-frontend/src/services/nodeService.ts`
- 新增类型：
  - `BatchDownloadAsyncTask`
  - `BatchDownloadAsyncTaskListResponse`
- 新增方法：
  - `startBatchDownloadAsync`
  - `listBatchDownloadAsyncTasks`
  - `getBatchDownloadAsyncTask`
  - `cancelBatchDownloadAsyncTask`
  - `downloadBatchDownloadAsyncTask`

### 4.2 FileBrowser 批量下载任务中心
- 文件：`ecm-frontend/src/pages/FileBrowser.tsx`
- 变更：
  - “Download selected” 优先触发 async batch download
  - 启动失败时回退到原同步 ZIP 下载
  - 新增 `Batch Download Tasks` 面板
  - 面板支持：
    - refresh
    - active task polling
    - progress 展示（files / bytes）
    - cancel
    - completed artifact download

### 4.3 预览页评论面板
- 文件：`ecm-frontend/src/components/preview/DocumentPreview.tsx`
- 变更：
  - 顶部工具栏新增 `Comments` toggle
  - 在预览底部挂载 `CommentSection`
  - 评论线程与预览在一个 dialog 中协同显示

### 4.4 审批发起人改为 people directory
- 文件：`ecm-frontend/src/components/dialogs/StartWorkflowDialog.tsx`
- 文件：`ecm-frontend/src/services/peopleService.ts`
- 变更：
  - dialog 打开时调用 `/people` 搜索接口装载可选审批人
  - 替换原静态 approver options
  - 后续可继续复用到 workflow reassignment / mention / favorites 等入口

## 5. 结果
- Athena 现在已有可用的 Alfresco-style async batch download 资源，而不是只有同步 ZIP 导出。
- 评论能力已经进入核心用户路径，文档预览里可直接查看与讨论。
- workflow 启动表单已开始接入 people directory，协作入口不再依赖写死用户名。
