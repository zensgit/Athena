# Phase 254 - Preview Failure Ledger Filter Reset & CSV Export (Dev)

Date: 2026-03-10  
Scope: `ecm-core`, `ecm-frontend`

## 1. Goal

在 Phase253 的 failure-ledger 基础上，补齐按原因分组的治理与可审计导出能力：

1. 增加按 `reason/category/retryable` 过滤的 ledger 重置接口。
2. 增加 ledger CSV 导出接口（按窗口与上限）。
3. 在 Preview Diagnostics 页面补齐 reason 级 `Reset Ledger` 操作与 ledger 导出按钮。
4. 增加后端安全测试与前端 mocked E2E 覆盖。

## 2. Backend Design & Implementation

### 2.1 New APIs

新增接口：

- `POST /api/v1/preview/diagnostics/failures/ledger/reset-by-filter`
- `GET /api/v1/preview/diagnostics/failures/ledger/export`

文件：

- `ecm-core/src/main/java/com/ecm/core/controller/PreviewDiagnosticsController.java`

### 2.2 Filter reset governance

`reset-by-filter` 支持字段：

- `reason`
- `category`（`ANY` 或具体分类）
- `retryable`（可空）
- `maxDocuments`
- `days`

实现要点：

- 使用 `findPreviewFailureLedgerEntries` 分页扫描，带扫描上限 `FAILURE_LEDGER_RESET_FILTER_SCAN_LIMIT`。
- 增加 `FailureLedgerFilterScanResult`，返回真实 `scanned` 与 `truncated`，避免估算值偏差。
- 对匹配文档复用 `resetFailureLedgerInternal`，保证单条与批量行为一致。

### 2.3 CSV export

新增 ledger CSV 导出能力：

- 按 `days + limit` 查询 failure-ledger 样本。
- 输出字段包含文档基础信息、failure ledger 字段、hash 对齐状态。
- 返回头：`X-Preview-Failure-Ledger-Count` 与附件文件名。

### 2.4 Audit events

新增审计事件：

- `PREVIEW_FAILURE_LEDGER_RESET_BY_FILTER`
- `PREVIEW_FAILURE_LEDGER_EXPORTED`

用于追踪治理动作与导出行为，支撑运维审计闭环。

### 2.5 DTO additions

新增 DTO：

- `PreviewFailureLedgerResetByFilterRequestDto`
- `PreviewFailureLedgerResetByFilterResponseDto`

响应包含：

- 过滤上下文（reason/category/retryable/windowDays/maxDocuments）
- 覆盖统计（totalCandidates/scanned/matched/truncated）
- 执行统计（reset/skipped/failed）
- 逐条结果列表（`results`）

## 3. Frontend Design & Implementation

### 3.1 Service layer

`previewDiagnosticsService` 新增：

- `resetFailureLedgerByFilter(request)`
- `exportFailureLedgerCsv(days, limit)`

并补齐对应 TypeScript 类型：

- `PreviewFailureLedgerResetByFilterRequest`
- `PreviewFailureLedgerResetByFilterResult`

文件：

- `ecm-frontend/src/services/previewDiagnosticsService.ts`

### 3.2 Preview Diagnostics UI

`PreviewDiagnosticsPage` 新增：

- Top reason 行级操作：`Reset Ledger`
- Failure Ledger 面板导出按钮：`Export Ledger CSV`

并对 reason 行操作增加统一互斥状态：

- `reasonLedgerResetActionKey`

避免 reason 行上多类操作并发触发导致状态冲突。

文件：

- `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`

### 3.3 Mocked E2E updates

`admin-preview-diagnostics.mock.spec.ts` 增加：

- `/failures/ledger/reset-by-filter` mock 路由与请求断言
- `/failures/ledger/export` mock 路由与请求断言
- UI 流程：reason 级 `Reset Ledger` + ledger 导出成功提示校验

文件：

- `ecm-frontend/e2e/admin-preview-diagnostics.mock.spec.ts`

## 4. Test updates

后端：

- `PreviewDiagnosticsControllerSecurityTest`
  - 新接口 admin/user 权限覆盖
  - `reset-by-filter` 行为与审计断言
  - `ledger/export` CSV header/body 与审计断言

前端：

- mocked E2E 场景覆盖 reason 级 ledger 重置与导出操作。

