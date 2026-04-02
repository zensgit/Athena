# Phase 214 - Preview Rendition Resources Export UI - Development

## Date
2026-03-08

## Goal
- 在 Preview Diagnostics 的 rendition summary 面板提供一键导出入口，打通 UI 到导出 API 的操作闭环。
- 在 mocked E2E 中校验导出请求参数，保证 days/limit 透传正确。

## Implemented

### 1) Frontend service API
- Updated `ecm-frontend/src/services/previewDiagnosticsService.ts`
  - Added `exportRenditionResourcesCsv(days, limit)` calling:
    - `/preview/diagnostics/renditions/resources/export`
  - Uses `api.downloadFile(...)` and timestamped filename.

### 2) Preview diagnostics page action
- Updated `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`
  - Added `Export Resources CSV` button in rendition summary header.
  - Added `handleExportRenditionResourcesCsv()`:
    - success toast: `Rendition resources CSV exported`
    - failure toast: `Failed to export rendition resources CSV`

### 3) Mocked E2E alignment
- Updated `ecm-frontend/e2e/admin-preview-diagnostics.mock.spec.ts`
  - `renditions/resources` route now also handles `/renditions/resources/export`.
  - Captures export request params (`days`, `limit`) and returns CSV fixture.
  - Adds UI click/assert for export success toast and parameter assertions.

## Impact
- Operator can directly export rendition resources from the diagnostics panel without API tooling.
- E2E now validates not only rendering but also export interaction contract.
