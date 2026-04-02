# Phase273 开发设计：Preview Queue Declined Requeue Dry-run Async Export Task Center Governance（2026-03-11）

## 1. 目标

在 Phase271（requeue dry-run reason breakdown + CSV）与 Phase272（preflight reason）基础上，补齐“可异步导出 + 可治理任务中心”能力，避免大筛选窗口下同步 CSV 导出阻塞接口线程：

1. 为 `queue declined requeue dry-run export` 提供异步任务通道。
2. 提供任务中心治理接口（list/summary/get/cancel/download/cleanup/cancel-active）。
3. 在 start 阶段做活跃任务去重（按规范化过滤快照复用）。
4. 记录完整审计链路，保证可回溯治理。

## 2. 后端设计

文件：`ecm-core/src/main/java/com/ecm/core/controller/PreviewDiagnosticsController.java`

### 2.1 新增 API

1. `POST /api/v1/preview/diagnostics/queue/declined/requeue/dry-run/export-async`
2. `GET /api/v1/preview/diagnostics/queue/declined/requeue/dry-run/export-async`
3. `GET /api/v1/preview/diagnostics/queue/declined/requeue/dry-run/export-async/summary`
4. `POST /api/v1/preview/diagnostics/queue/declined/requeue/dry-run/export-async/cleanup`
5. `POST /api/v1/preview/diagnostics/queue/declined/requeue/dry-run/export-async/cancel-active`
6. `GET /api/v1/preview/diagnostics/queue/declined/requeue/dry-run/export-async/{taskId}`
7. `POST /api/v1/preview/diagnostics/queue/declined/requeue/dry-run/export-async/{taskId}/cancel`
8. `GET /api/v1/preview/diagnostics/queue/declined/requeue/dry-run/export-async/{taskId}/download`

### 2.2 任务模型与状态机

1. 状态：`QUEUED/RUNNING/COMPLETED/CANCELLED/FAILED`。
2. 新增任务记录结构与 DTO：
   - request/create/status/list/summary/cleanup/cancel-active。
3. 复用已有 dry-run 计算与 CSV 生成链路：
   - `computeQueueDeclinedRequeueDryRun(...)`
   - `buildQueueDeclinedRequeueDryRunCsv(...)`

### 2.3 Start 去重治理

1. 对 `limit/category/forceRequired/query/windowHours/force` 做规范化快照。
2. 若存在同快照且活跃（`QUEUED/RUNNING`）任务，则直接返回复用任务：
   - `deduplicated=true`
   - `deduplicatedFromTaskId=<activeTaskId>`
3. 避免重复异步导出造成资源浪费与审计噪声。

### 2.4 审计治理

新增异步导出审计事件：

1. start / dedup-hit
2. cancel / cancel-active
3. cleanup
4. download
5. completed / failed / cancelled

审计上下文包含过滤条件与 taskId，支持运维复盘。

## 3. 前端设计

文件：

1. `ecm-frontend/src/services/previewDiagnosticsService.ts`
2. `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`

### 3.1 Service 能力

补齐 requeue dry-run async export 全套 API 客户端：

1. start/list/summary/get
2. cancel/download
3. cleanup/cancel-active

### 3.2 页面交互

在 Preview Diagnostics 增加 “Requeue Dry-run Async Export Task Center”：

1. 启动按钮：`Start Requeue Dry-run Async Export`
2. 汇总芯片：total/active/completed/failed
3. 任务表：taskId/status/createdAt/finishedAt
4. 行级动作：download/cancel
5. 治理动作：refresh/cancel-active/cleanup
6. start 去重命中时显示复用提示

## 4. Mocked E2E 设计

文件：`ecm-frontend/e2e/admin-preview-diagnostics.mock.spec.ts`

1. 增加以上 8 个 async API 的 route mock。
2. 覆盖 start-dedup、list/summary、row cancel、download、cancel-active、cleanup。
3. 断言请求参数对齐 `limit/category/forceRequired/query/windowHours/force`。

## 5. 对标价值（Alfresco+）

相较传统“同步导出 + 无任务治理”的模式，本阶段增强：

1. 大规模治理导出异步化（降低接口阻塞风险）。
2. 任务中心治理闭环（状态、取消、清理、下载可控）。
3. 去重启动策略减少重复任务，提升稳定性与可运维性。
