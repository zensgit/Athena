# Phase270 开发说明：Queue Declined Async Export Start 去重复用治理（2026-03-11）

## 1. 目标
- 对 `POST /api/v1/preview/diagnostics/queue/declined/export-async` 增加活跃任务去重治理：
  - 若存在同过滤快照（`limit/category/forceRequired/query/windowHours`）且状态为 `QUEUED/RUNNING` 的任务，则复用该任务，不再创建新任务。
- 目标是降低重复导出任务、减少噪声与资源浪费，提升操作稳定性。

## 2. 后端实现

### 2.1 Start 去重逻辑
- 在 start 入口新增活跃任务查找：
  - `findActiveQueueDeclinedExportAsyncTask(...)`
  - `matchesQueueDeclinedExportAsyncTaskRequest(...)`
- 匹配条件：
  - `limit`、`category`、`forceRequired`、`query`、`windowHours` 规范化后全量一致。
- 命中后行为：
  - 直接返回已有任务（不新建、不异步提交）。

### 2.2 返回模型扩展
- `PreviewQueueDeclinedExportAsyncCreateResponseDto` 新增字段：
  - `deduplicated`
  - `deduplicatedFromTaskId`
  - `message`
- start 普通创建返回：
  - `deduplicated=false`
- start 命中去重返回：
  - `deduplicated=true`
  - `deduplicatedFromTaskId=<reusedTaskId>`

### 2.3 审计增强
- 新增事件：
  - `PREVIEW_QUEUE_DECLINED_ASYNC_EXPORT_START_DEDUP_HIT`
- 审计内容包含：
  - 过滤上下文
  - `reusedTaskId`

## 3. 前端实现

### 3.1 类型扩展
- `PreviewQueueDeclinedExportTask` 增加：
  - `deduplicated?: boolean`
  - `deduplicatedFromTaskId?: string`

### 3.2 交互增强
- `Start Async Export` 返回去重命中时：
  - toast 从 “started” 改为 “reused”
  - 文案：`Queue declined async export task reused: <taskId>`

## 4. Mocked E2E 改造
- start mock 增加“同过滤活跃任务去重”分支。
- 场景中加入“连续两次 Start（第二次命中复用）”断言。
- 追加去重调用记录断言：
  - `queueDeclinedExportAsyncStartDedupCalls`

## 5. 变更文件
- `ecm-core/src/main/java/com/ecm/core/controller/PreviewDiagnosticsController.java`
- `ecm-core/src/test/java/com/ecm/core/controller/PreviewDiagnosticsControllerSecurityTest.java`
- `ecm-frontend/src/services/previewDiagnosticsService.ts`
- `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`
- `ecm-frontend/e2e/admin-preview-diagnostics.mock.spec.ts`
