# Phase 255 - Preview Queue Health Diagnostics (Dev)

Date: 2026-03-10  
Scope: `ecm-core`, `ecm-frontend`

## 1. Goal

对标并强化 Stream B（队列治理）可观测能力，补齐预览队列健康诊断面：

1. 提供队列运行态摘要 API（scheduled/running/cancel-requested 等）。
2. 提供样本任务明细（governance key、attempt、nextAttemptAt、running、cancelRequested）。
3. 前端 Diagnostics 页面新增 Queue Health 卡片，支持运维快速定位拥塞与去重状态。

## 2. Backend Design & Implementation

### 2.1 Redis queue store diagnostics primitives

在 Redis 延迟队列存储中新增：

- `scheduledCount()`：读取调度 ZSET 数量。
- `peek(limit)`：读取最早 N 条调度条目（docId + attempts + nextAttemptAt）。

文件：

- `ecm-core/src/main/java/com/ecm/core/queue/RedisScheduledQueueStore.java`

### 2.2 PreviewQueueService diagnostics snapshot

新增 `diagnosticsSnapshot(limit)`，统一输出 memory/redis 两种后端的诊断快照：

- `backend`
- `queueEnabled`
- `scheduledCount`
- `governanceCount`
- `runningCount`
- `runningCountAccurate`
- `cancellationRequestedCount`
- `sampleLimit`
- `sampleTruncated`
- `items[]`

新增记录类型：

- `PreviewQueueDiagnosticsSnapshot`
- `PreviewQueueDiagnosticsItem`

Memory backend：

- 从 `queuedJobs / activeGovernanceByDocument / activeRunningByDocument / cancelRequestedByDocument` 直接统计。

Redis backend：

- 从 schedule ZSET + governance/cancel hash 统计；
- 对 governance key 扫描锁状态计算 `runningCount`，并通过 `runningCountAccurate` 标记扫描上限场景。

文件：

- `ecm-core/src/main/java/com/ecm/core/preview/PreviewQueueService.java`

### 2.3 Preview diagnostics API

新增接口：

- `GET /api/v1/preview/diagnostics/queue/summary?limit=20`

返回 DTO：

- `PreviewQueueDiagnosticsSummaryDto`
- `PreviewQueueDiagnosticsItemDto`

文件：

- `ecm-core/src/main/java/com/ecm/core/controller/PreviewDiagnosticsController.java`

## 3. Frontend Design & Implementation

### 3.1 Service contract

`previewDiagnosticsService` 新增：

- `getQueueDiagnosticsSummary(limit=20)`

并新增类型：

- `PreviewQueueDiagnosticsSummary`
- `PreviewQueueDiagnosticsItem`

文件：

- `ecm-frontend/src/services/previewDiagnosticsService.ts`

### 3.2 Preview Diagnostics UI

新增 `Preview Queue Health` 卡片：

- 关键指标 chip：backend/queue enabled/scheduled/running/cancel requested。
- 精度提示：`runningCountAccurate=false` 时显示 `Running estimated`。
- 样本任务表：documentId/attempts/nextAttemptAt/state/governanceKey。

文件：

- `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`

### 3.3 Mocked E2E updates

`admin-preview-diagnostics.mock.spec.ts` 新增：

- `/preview/diagnostics/queue/summary` 路由 mock。
- UI 断言：Queue Health 卡片展示、关键指标展示、请求参数断言（`limit=20`）。

文件：

- `ecm-frontend/e2e/admin-preview-diagnostics.mock.spec.ts`

## 4. Tests updated

后端：

- `PreviewDiagnosticsControllerSecurityTest`
  - 新增 queue summary 的 ADMIN/USER 权限与返回结构断言。
- `PreviewQueueServiceTest`
  - 新增 memory backend snapshot 统计正确性测试。

前端：

- mocked e2e 场景覆盖 queue summary API 消费与页面渲染。

