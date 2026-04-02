# Phase 212 - Preview Rendition Resources Panel UI - Development

## Date
2026-03-08

## Goal
- 前端接入 rendition resources 一等资源接口，并处理历史数组契约与新对象契约并存。
- 保证 UI 在后端升级期间不空白、不回归。

## Implemented

### 1) Frontend service compatibility adapter
- Updated `ecm-frontend/src/services/previewDiagnosticsService.ts`
  - Added response compatibility handling for `/preview/diagnostics/renditions/resources`:
    - 支持 legacy `[]`
    - 支持 new object `{ ..., items: [] }`
  - Added normalization mapping:
    - `renditionStatus -> status`
    - `previewFailureReason -> reason`
    - `previewLastUpdated -> updatedAt`
    - 兼容旧字段 `status/reason/updatedAt`
  - Standardized return type to `PreviewRenditionResource[]` for page consumers.

### 2) Mocked E2E contract alignment
- Updated `ecm-frontend/e2e/admin-preview-diagnostics.mock.spec.ts`
  - Rendition resources mock now returns backend-aligned object payload with metadata + `items`.
  - Item fields updated to backend names (`renditionStatus`, `previewFailureReason`, `previewLastUpdated`, etc.).

### 3) UI behavior preservation
- Existing `PreviewDiagnosticsPage` resources table remains unchanged in rendering contract.
- 通过 service 适配层消化契约差异，避免页面组件扩散兼容逻辑。

## Impact
- 前端和后端 rendition resources 契约已对齐，同时保留历史兼容能力。
- Mocked 回归用例更贴近真实后端返回，降低“mock 绿、联调红”的风险。
