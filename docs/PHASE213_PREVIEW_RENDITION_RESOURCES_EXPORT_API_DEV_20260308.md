# Phase 213 - Preview Rendition Resources Export API - Development

## Date
2026-03-08

## Goal
- 为 `rendition resources` 增加 CSV 导出 API，补齐面向运维与审计的离线分析能力。
- 复用现有 diagnostics 窗口与采样机制，确保导出行为和面板数据一致。

## Implemented

### 1) Backend export endpoint
- Updated `ecm-core/src/main/java/com/ecm/core/controller/PreviewDiagnosticsController.java`
  - Added `GET /api/v1/preview/diagnostics/renditions/resources/export`
  - Params:
    - `days` default `7`
    - `limit` default `500`, max `2000`
  - Response:
    - content-type: `text/csv; charset=UTF-8`
    - `Content-Disposition` attachment filename
    - `X-Preview-Rendition-Resource-Count`

### 2) Export content model
- Added CSV builder for rendition resources with fields:
  - `documentId,name,path,mimeType,previewStatus,renditionStatus,previewFailureCategory,previewFailureReason,previewLastUpdated`
- Export row logic aligns with diagnostics list:
  - same mime normalization
  - same rendition status derivation
  - same failure reason/category inclusion gating

### 3) Audit trace
- Added audit event `PREVIEW_RENDITION_RESOURCES_EXPORTED` with days/limit/exported summary.

### 4) Security test coverage
- Updated `ecm-core/src/test/java/com/ecm/core/controller/PreviewDiagnosticsControllerSecurityTest.java`
  - USER forbidden assertion includes export endpoint
  - ADMIN CSV export success assertions (header/content + audit call)

## Impact
- Preview diagnostics now supports exportable rendition resource evidence, not only in-page viewing.
- Operators can perform offline triage and historical comparison with stable CSV schema.
