# Phase274 开发设计：Preview Queue Declined Requeue Dry-run Async Export Status Filter UI Governance（2026-03-12）

## 1. 目标

在 Phase273 已完成 requeue dry-run async export task center 的基础上，补齐 UI 侧状态筛选治理能力，确保“看见什么、统计什么、清理什么”一致：

1. 支持按状态筛选 requeue dry-run async tasks（`ALL/QUEUED/RUNNING/COMPLETED/CANCELLED/FAILED`）。
2. `list/summary` 与当前筛选状态联动。
3. `cleanup` 使用当前筛选状态执行定向清理。
4. `cancel-active` 仅接受活跃状态（`QUEUED/RUNNING`），其他筛选回退为 `ALL`，与后端契约一致。

## 2. 前端实现

文件：`ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`

### 2.1 状态模型扩展

1. 引入 requeue dry-run async task 状态筛选类型：
   - `PreviewQueueDeclinedRequeueDryRunExportTaskStatusFilter`
   - `PreviewQueueDeclinedRequeueDryRunExportTaskActiveStatusFilter`
2. 新增状态筛选常量：
   - `QUEUE_DECLINED_REQUEUE_DRY_RUN_EXPORT_TASK_STATUS_FILTER_OPTIONS`
3. 新增页面状态：
   - `queueDeclinedRequeueDryRunExportTaskStatusFilter`

### 2.2 数据加载联动

`loadQueueDeclinedRequeueDryRunExportTasks(...)` 改造为：

1. `listQueueDeclinedRequeueDryRunExportTasks(limit, statusFilter)`
2. `getQueueDeclinedRequeueDryRunExportTaskSummary(statusFilter)`

并把 `statusFilter` 作为 callback 依赖，切换筛选后自动刷新列表与汇总。

### 2.3 治理动作联动

1. `cleanup`：当筛选非 `ALL` 时透传当前状态到后端。
2. `cancel-active`：仅当筛选为 `QUEUED/RUNNING` 时透传；否则传 `undefined`（后端按活跃全集处理）。

### 2.4 UI 交互

在 “Requeue Dry-run Async Export Tasks” 区块新增状态下拉：

1. aria: `Queue declined requeue dry-run async task filter status`
2. 切换后立即生效于 list/summary 与治理动作。

## 3. Mocked E2E 改造

文件：`ecm-frontend/e2e/admin-preview-diagnostics.mock.spec.ts`

1. 在 requeue dry-run async task center 流程中新增状态筛选交互：
   - 切换到 `Completed` 后验证仅显示 completed 任务。
   - 执行 cleanup 后切到 `Cancelled`，验证 cancelled 任务仍可见。
2. 增强调用断言：
   - list/summary 覆盖 `ALL/COMPLETED/CANCELLED`
   - cleanup 覆盖 `status=COMPLETED`
   - cancel-active 维持 `status=ALL`（非活跃筛选不透传）

## 4. 对标价值（Alfresco+）

相较“只支持全量 task 列表”的治理面板，本阶段增加状态维度运维闭环：

1. 状态级别可观测（list/summary 一致）。
2. 状态级别可操作（cleanup 定向）。
3. 与后端活跃状态契约对齐，避免无效 cancel-active 请求。
