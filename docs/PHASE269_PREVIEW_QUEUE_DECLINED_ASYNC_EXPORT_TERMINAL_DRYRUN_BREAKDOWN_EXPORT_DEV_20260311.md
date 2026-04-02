# Phase269 开发说明：Queue Declined Async Retry Dry-run 原因聚合与 CSV 导出（2026-03-11）

## 1. 目标
- 在既有 Phase267/268 的 `retry-terminal` dry-run 基础上，补齐可操作性缺口：
  - dry-run 返回**原因聚合**（reason breakdown），用于快速判断重试价值。
  - 新增 dry-run **CSV 导出端点**，支持离线分析与审计留痕。

## 2. 后端设计与实现

### 2.1 新增导出端点
- `GET /api/v1/preview/diagnostics/queue/declined/export-async/retry-terminal/dry-run/export`
- 参数：
  - `status`：`COMPLETED/CANCELLED/FAILED`（可选，不传默认 `FAILED|CANCELLED`）
  - `limit`：`1..200`（默认 `20`）
- 返回：
  - `text/csv; charset=UTF-8`
  - Header：`X-Preview-Queue-Declined-Retry-Dry-Run-Count`

### 2.2 dry-run 计算模型重构
- 新增内部计算模型：`QueueDeclinedExportAsyncRetryTerminalDryRunComputation`
- dry-run 与 dry-run/export 共用同一计算入口：
  - `computeQueueDeclinedExportAsyncRetryTerminalDryRun(status, limit)`
- 避免 API 与导出出现口径漂移。

### 2.3 dry-run 响应增强
- `PreviewQueueDeclinedExportAsyncRetryTerminalDryRunResponseDto` 新增：
  - `reasonBreakdown[]`
- `PreviewQueueDeclinedExportAsyncRetryTerminalDryRunItemDto` 新增：
  - `reasonCode`
- 新增聚合 DTO：
  - `PreviewQueueDeclinedExportAsyncRetryTerminalDryRunReasonCountDto(reasonCode, outcome, count)`

### 2.4 审计增强
- 既有事件增强：
  - `PREVIEW_QUEUE_DECLINED_ASYNC_EXPORT_RETRY_BULK_DRY_RUN` 明细中新增 `reasonBreakdown=...`
- 新增导出事件：
  - `PREVIEW_QUEUE_DECLINED_ASYNC_EXPORT_RETRY_BULK_DRY_RUN_EXPORTED`

## 3. 前端设计与实现

### 3.1 Service 层
- `previewDiagnosticsService.ts`：
  - dry-run item 类型新增 `reasonCode`
  - dry-run 响应类型新增 `reasonBreakdown`
  - 新增方法：
    - `exportDryRunRetryTerminalQueueDeclinedExportTasks(status?, limit?)`

### 3.2 页面交互
- `PreviewDiagnosticsPage.tsx`：
  - 在 Queue Declined async 操作栏新增按钮：
    - `Export Dry-run CSV`（`aria-label: Export queue declined terminal dry-run CSV`）
  - dry-run 候选面板新增：
    - reason breakdown chips（展示 Top5）
    - 候选表新增 `Reason` 列（显示 `reasonCode`）

## 4. 测试设计
- 后端：
  - `PreviewDiagnosticsControllerSecurityTest`
    - 非 admin 禁止访问新增导出端点
    - admin 场景验证 dry-run `reasonBreakdown` 字段
    - admin 场景验证 dry-run/export CSV 内容与审计事件
- 前端：
  - `admin-preview-diagnostics.mock.spec.ts`
    - 新增 dry-run/export mock 路由
    - 新增导出按钮点击与 toast 断言
    - 新增导出请求调用参数断言（`status/limit`）

## 5. 变更文件
- `ecm-core/src/main/java/com/ecm/core/controller/PreviewDiagnosticsController.java`
- `ecm-core/src/test/java/com/ecm/core/controller/PreviewDiagnosticsControllerSecurityTest.java`
- `ecm-frontend/src/services/previewDiagnosticsService.ts`
- `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`
- `ecm-frontend/e2e/admin-preview-diagnostics.mock.spec.ts`
