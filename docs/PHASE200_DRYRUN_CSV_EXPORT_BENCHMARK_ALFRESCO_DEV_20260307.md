# Phase 200 - Dry-Run CSV Export (Benchmark-Aligned with Alfresco) - Development

## Date
2026-03-07

## Goal
- 对标 Alfresco 在“导出能力”上的成熟实践，补齐 Athena 的预览批处理导出短板。
- 提供可复用的 dry-run 导出契约，为后续异步导出任务中心做前置能力沉淀。

## Implemented

### 1) Backend: dry-run CSV export API
- Updated `ecm-core/src/main/java/com/ecm/core/controller/SearchController.java`:
  - Added endpoint (admin):
    - `POST /api/v1/search/preview/queue-failed/dry-run/export`
  - Behavior:
    - Reuse search-scope matcher (`collectMatchedRetryableFailures`)
    - Export CSV including:
      - run metrics (`query/reasonFilter/maxDocuments/totalCandidates/scanned/matched/truncated`)
      - reason breakdown (`reason,count`)
      - matched items (`documentId,name,previewStatus,previewFailureReason,previewFailureCategory`)
  - Added CSV helpers for escaping and row writing.

### 2) Backend: queue/dry-run reason breakdown unification
- Refactored `SearchController`:
  - `buildReasonBreakdown(...)` shared by queue and dry-run endpoints
  - Queue response now also includes `reasonBreakdown`

### 3) Backend tests
- Updated `ecm-core/src/test/java/com/ecm/core/controller/SearchControllerTest.java`:
  - Queue endpoint asserts `reasonBreakdown`
  - Added CSV export endpoint test (content-disposition/content-type/content body)
- Updated `ecm-core/src/test/java/com/ecm/core/controller/SearchControllerSecurityTest.java`:
  - Added admin-only security tests for CSV export endpoint.

### 4) Frontend integration
- Updated `ecm-frontend/src/services/nodeService.ts`:
  - Added `exportDryRunFailedPreviewsCsvBySearch(...)`
  - Extended queue batch result type with `reasonBreakdown`.
- Updated `ecm-frontend/src/pages/AdvancedSearchPage.tsx`:
  - Added `Export dry-run CSV` button in dry-run summary panel.
  - Added `Batch reasons` chips for execution result feedback.
  - Added local export flow using Blob + object URL download.

### 5) Mock E2E alignment
- Updated `ecm-frontend/e2e/advanced-search-preview-batch-scope.mock.spec.ts`:
  - Added mocked route for dry-run export endpoint.
  - Added assertion for export request payload propagation.
  - Kept reason-level retry and all-matched flows assertions.
