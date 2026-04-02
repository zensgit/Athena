# Phase 256 - Preview Queue Diagnostics CSV Export (Dev)

Date: 2026-03-10  
Scope: `ecm-core`, `ecm-frontend`

## 1. Goal

在 Phase255 队列健康可视化基础上补齐“可留档”能力：

1. 提供队列健康摘要 CSV 导出接口。
2. 在 Preview Diagnostics Queue Health 卡片增加导出入口。
3. 增加审计事件，形成治理动作留痕。

## 2. Backend Design & Implementation

### 2.1 API

新增接口：

- `GET /api/v1/preview/diagnostics/queue/summary/export?limit=200`

行为：

- 基于 `previewQueueService.diagnosticsSnapshot(limit)` 输出 CSV。
- 返回头：`X-Preview-Queue-Item-Count`。
- 文件名：`preview_queue_diagnostics_<timestamp>.csv`。

文件：

- `ecm-core/src/main/java/com/ecm/core/controller/PreviewDiagnosticsController.java`

### 2.2 CSV schema

导出字段：

- `backend`
- `queueEnabled`
- `scheduledCount`
- `governanceCount`
- `runningCount`
- `runningCountAccurate`
- `cancellationRequestedCount`
- `sampleLimit`
- `sampleTruncated`
- `documentId`
- `attempts`
- `nextAttemptAt`
- `running`
- `cancelRequested`
- `governanceKey`

### 2.3 Audit

新增审计事件：

- `PREVIEW_QUEUE_DIAGNOSTICS_EXPORTED`

记录导出 limit、导出样本数、backend。

## 3. Frontend Design & Implementation

### 3.1 Service

`previewDiagnosticsService` 新增：

- `exportQueueDiagnosticsCsv(limit=200)`

文件：

- `ecm-frontend/src/services/previewDiagnosticsService.ts`

### 3.2 UI

Queue Health 卡片新增按钮：

- `Export Queue CSV`

成功提示：

- `Queue diagnostics CSV exported`

文件：

- `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`

### 3.3 Mocked E2E

`admin-preview-diagnostics.mock.spec.ts` 新增：

- `/preview/diagnostics/queue/summary/export` mock；
- 导出按钮点击与提示断言；
- 请求参数断言（`limit=200`）。

## 4. Test updates

后端：

- `PreviewDiagnosticsControllerSecurityTest`
  - 增加 queue summary export 的 admin/user 覆盖与审计断言。

前端：

- mocked e2e 增加 queue diagnostics export 路径覆盖。

