# Phase 211 - Preview Rendition Resources API - Development

## Date
2026-03-08

## Goal
- 将 preview 诊断里的 rendition 资源列表升级为一等资源模型（带窗口与采样元数据），而不是仅返回裸数组。
- 对齐后端契约，支撑后续按资源维度的运营诊断与批处理策略。

## Implemented

### 1) Backend resource diagnostics endpoint
- Updated `ecm-core/src/main/java/com/ecm/core/controller/PreviewDiagnosticsController.java`
  - Added/confirmed `GET /api/v1/preview/diagnostics/renditions/resources`
  - Request params:
    - `days` (default `7`)
    - `limit` (default `100`, max `500`)
  - Response payload:
    - Metadata: `totalResources`, `sampledResources`, `limit`, `windowDays`, `windowStart`, `sampleTruncated`
    - `items[]` with resource-level fields:
      - `documentId`, `name`, `path`, `mimeType`
      - `previewStatus`, `renditionStatus`
      - `previewFailureReason`, `previewFailureCategory`
      - `previewLastUpdated`

### 2) Shared diagnostics behavior alignment
- Reused rendition summary window/page helpers to keep:
  - window slicing逻辑一致
  - 排序与采样行为一致
  - limit clamp 行为一致

### 3) Security test coverage
- Updated `ecm-core/src/test/java/com/ecm/core/controller/PreviewDiagnosticsControllerSecurityTest.java`
  - USER forbidden set now includes `/renditions/resources`
  - ADMIN allow-case now includes `/renditions/resources` and verifies payload shape

## Impact
- Rendition diagnostics API 从“面板专用数组”升级为“可扩展资源诊断模型”。
- 为前端兼容层、后续批量动作、CSV 导出和审计扩展提供稳定基础。
