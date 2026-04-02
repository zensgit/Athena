# Phase 257 - Preview Queue Diagnostics Filtered Metadata (Dev)

Date: 2026-03-10  
Scope: `ecm-core`, `ecm-frontend`

## 1. Goal

在 Phase255/256 的 Queue Health 基础上，把“可看见”升级为“可筛选、可定位、可按筛选导出”：

1. 队列诊断支持状态过滤与关键字过滤（state/query）。
2. 样本项补齐文档元数据（name/path/mimeType/previewStatus）与标准化 queueState。
3. CSV 导出与当前过滤条件对齐，避免导出内容与 UI 观测不一致。

## 2. Backend Design & Implementation

## 2.1 Queue summary/filter contract

扩展接口：

- `GET /api/v1/preview/diagnostics/queue/summary?limit=20&state=...&query=...`
- `GET /api/v1/preview/diagnostics/queue/summary/export?limit=200&state=...&query=...`

其中：

- `state` 支持：`ALL | QUEUED | RUNNING | CANCEL_REQUESTED`
- `query` 支持 documentId / name / path / mimeType / previewStatus / queueState / governanceKey 模糊匹配

实现文件：

- `ecm-core/src/main/java/com/ecm/core/controller/PreviewDiagnosticsController.java`

核心实现点：

1. 新增 `mapQueueDiagnosticsItems(...)`，将 queue sample 与 `DocumentRepository` 元数据拼接。
2. 新增 `normalizeQueueDiagnosticsStateFilter(...)` 与 query 匹配工具方法。
3. 统一 queueState 推导：`RUNNING / QUEUED / CANCEL_REQUESTED`。

## 2.2 DTO schema extension

`PreviewQueueDiagnosticsSummaryDto` 新增：

- `stateFilter`
- `queryFilter`
- `totalSampledItems`
- `filteredSampledItems`

`PreviewQueueDiagnosticsItemDto` 新增：

- `name`
- `path`
- `mimeType`
- `previewStatus`
- `queueState`

## 2.3 CSV aligned export

`buildQueueDiagnosticsCsv(...)` 改为接收过滤后的 items，并输出过滤上下文列：

- `stateFilter`
- `queryFilter`
- `totalSampledItems`
- `filteredSampledItems`
- `queueState`
- `name/path/mimeType/previewStatus`

审计事件沿用 `PREVIEW_QUEUE_DIAGNOSTICS_EXPORTED`，并追加 state/query 上下文。

## 3. Frontend Design & Implementation

## 3.1 Service contract

`previewDiagnosticsService` 扩展：

- `getQueueDiagnosticsSummary(limit, state, query?)`
- `exportQueueDiagnosticsCsv(limit, state, query?)`

并同步类型：

- `PreviewQueueDiagnosticsStateFilter`
- `PreviewQueueDiagnosticsSummary` 新增 filter/sampled 字段
- `PreviewQueueDiagnosticsItem` 新增 metadata + `queueState`

文件：

- `ecm-frontend/src/services/previewDiagnosticsService.ts`

## 3.2 Queue Health UI

`PreviewDiagnosticsPage` 的 Queue Health 卡片新增：

1. 状态过滤下拉（All/Queued/Running/Cancel requested）
2. Query 输入框（name/path/mime/documentId）
3. `Apply filters` / `Clear`
4. `Refresh Queue` 按钮
5. 样本指标展示 `Sample filtered/total`

表格项展示升级为：

- 文档名称、路径、documentId
- MIME + Preview status chip
- queueState chip（从状态机角度统一显示）

导出按钮改为携带当前 state/query。

文件：

- `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`

## 3.3 Mocked E2E update

`admin-preview-diagnostics.mock.spec.ts` 扩展：

1. queue summary mock 支持 `state/query` 过滤。
2. queue export mock 与过滤结果对齐。
3. UI 交互覆盖状态筛选、query 应用、过滤后导出。
4. 断言 queue summary / export 请求参数包含 state/query。

## 4. Test updates

后端：

- `PreviewDiagnosticsControllerSecurityTest`
  - 覆盖 summary/export 的 state/query 过滤行为
  - 覆盖 metadata 字段与 queueState 回传

前端：

- mocked e2e 断言过滤与导出参数一致。
