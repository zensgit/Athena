# Phase 258 - Preview Queue Cancel-Active by Filter (Dev)

Date: 2026-03-10  
Scope: `ecm-core`, `ecm-frontend`

## 1. Goal

在 Phase257 的筛选诊断能力上补齐“执行”动作：  
支持按 Queue Health 当前过滤条件批量取消 active preview queue 任务，实现可观测与治理闭环。

## 2. Backend Design & Implementation

## 2.1 API

新增接口：

- `POST /api/v1/preview/diagnostics/queue/cancel-active?limit=200&state=...&query=...`

参数：

- `limit`：取消候选上限（默认 200，最大 1000）
- `state`：`ALL | QUEUED | RUNNING | CANCEL_REQUESTED`
- `query`：与 queue diagnostics 同步的关键字过滤

行为：

1. 基于 `previewQueueService.diagnosticsSnapshot(limit)` 取样。
2. 复用 Phase257 的 `mapQueueDiagnosticsItems(...)` 做 state/query 过滤。
3. 对候选项执行 `previewQueueService.cancel(documentId)`。
4. 返回聚合结果（requested/cancelled/skipped/failed）和逐项 outcome。

实现文件：

- `ecm-core/src/main/java/com/ecm/core/controller/PreviewDiagnosticsController.java`

## 2.2 DTO

新增：

- `PreviewQueueCancelActiveResponseDto`
- `PreviewQueueCancelActiveItemDto`

字段包含：

- 过滤上下文（`stateFilter/queryFilter/limit`）
- 聚合结果（`requested/cancelled/skipped/failed`）
- 逐项结果（`documentId/queueState/outcome/message`）

## 2.3 Audit

新增审计事件：

- `PREVIEW_QUEUE_CANCEL_ACTIVE`

记录：state/query/limit/requested/cancelled/skipped/failed。

## 2.4 Security test coverage

`PreviewDiagnosticsControllerSecurityTest` 增加：

1. USER 对 `POST /queue/cancel-active` 403。
2. ADMIN 按 filter 取消成功断言（返回结构 + 审计事件）。

## 3. Frontend Design & Implementation

## 3.1 Service

`previewDiagnosticsService` 新增：

- `cancelQueueDiagnosticsActive(limit, state, query?)`

类型新增：

- `PreviewQueueCancelActiveResult`
- `PreviewQueueCancelActiveItem`

文件：

- `ecm-frontend/src/services/previewDiagnosticsService.ts`

## 3.2 Queue Health UI

`PreviewDiagnosticsPage` Queue Health 卡片新增按钮：

- `Cancel Filtered`

行为：

1. 按当前 Queue filter 调用 cancel-active API。
2. toast 反馈完成/部分完成/失败/无匹配。
3. 动作后自动刷新 diagnostics。

文件：

- `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`

## 3.3 Mocked E2E

`admin-preview-diagnostics.mock.spec.ts` 增加：

1. `/preview/diagnostics/queue/cancel-active` route mock；
2. UI 点击 `Cancel Filtered` 的结果断言；
3. 请求参数断言（`limit/state/query`）。
