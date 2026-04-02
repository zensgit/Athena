# Phase 206 - Preview Rendition Resource Summary API + UI - Development

## Date
2026-03-08

## Goal
- 对标 Alfresco 预览治理可观测性，补齐“资源层状态分布 + 失败原因 TopN”汇总能力。
- 在 Athena Preview Diagnostics 页面增加可即时刷新的资源汇总卡片，支撑运维快速判断渲染健康度。

## Implemented

### 1) Backend: rendition summary endpoint
- Updated `ecm-core/src/main/java/com/ecm/core/controller/PreviewDiagnosticsController.java`
  - Added `GET /api/v1/preview/diagnostics/renditions/summary`
    - params: `days`, `sampleLimit`
    - returns:
      - `totalResources/sampledResources/sampleLimit/windowDays/windowStart/sampleTruncated`
      - `statusCounts`（`CREATED/NOT_CREATED/STALE/FAILED/UNSUPPORTED/PROCESSING`）
      - `topReasons`（Top 10）
  - Added helper methods:
    - `buildRenditionSummarySpecification(...)`
    - `deriveRenditionStatus(...)`
    - `shouldAggregateFailureReason(...)`

### 2) Backend security tests
- Updated `ecm-core/src/test/java/com/ecm/core/controller/PreviewDiagnosticsControllerSecurityTest.java`
  - 非 admin 访问 `/renditions/summary` 返回 `403`。
  - admin 可访问并校验响应结构和计数。

### 3) Frontend service + UI
- Updated `ecm-frontend/src/services/previewDiagnosticsService.ts`
  - Added `getRenditionSummary(days, sampleLimit)`.
- Updated `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`
  - Added “Rendition Resource Summary” panel:
    - sample coverage chip
    - sample truncated warning chip
    - status chips
    - top reasons table（UI 显示 Top 5）
    - refresh action（局部刷新）

### 4) E2E coverage
- Updated `ecm-frontend/e2e/admin-preview-diagnostics.mock.spec.ts`
  - Added route mock for `/preview/diagnostics/renditions/summary`.
  - Added assertions for summary card, status counts, top reasons limit, and refresh behavior.
  - Added file-level eslint disable for `testing-library/prefer-screen-queries`（该规则不适用于 Playwright e2e 文件）。

## Impact
- 预览异常排查从“单文档失败列表”扩展为“资源层健康总览 + 失败热点”，更接近 Alfresco 运维可观测能力。
- 与现有失败摘要/按原因批操作能力互补，形成更完整的 diagnostics 操作闭环。
