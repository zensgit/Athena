# Phase 215 - Preview Rendition Resource Inline Actions UI - Development

## Date
2026-03-08

## Goal
- 在 `Rendition Resources` 表格上提供资源级 `Retry/Force` 直达操作，减少运维从诊断到恢复的切换成本。
- 复用现有队列批处理 API，保持后端接口收敛。

## Implemented

### 1) Inline queue actions
- Updated `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`
  - Added resource-level action state `renditionResourceActionId`.
  - Added `handleQueueRenditionResource(resource, force)`:
    - Calls `previewDiagnosticsService.queueFailuresBatch([documentId], force)`.
    - Surfaces success/warn/error toast by queue outcome.
    - Auto refreshes diagnostics data after action.

### 2) Rendition resources table UX
- Added `Actions` column for each resource row:
  - `Retry` button (`Retry rendition resource {documentId}`)
  - `Force` button (`Force rebuild rendition resource {documentId}`)
- Guardrails:
  - missing `documentId` disables actions
  - `UNSUPPORTED` disables non-force retry
  - action-in-flight disables concurrent row actions

### 3) Mocked E2E coverage
- Updated `ecm-frontend/e2e/admin-preview-diagnostics.mock.spec.ts`
  - Added stable rendition resource IDs for assertions.
  - Asserts inline retry button triggers queue call and success toast.
  - Verifies queued document id is captured in queue mock call list.

## Impact
- Preview diagnostics now supports resource-level remediation in-place, not only list-level/batch-level operations.
- Improves operator handling speed for pinpointed rendition failures.
