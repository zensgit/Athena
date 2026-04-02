# Phase 278 - Preview Queue Declined Retry Reused Structured Metrics (Dev)

## Date
- 2026-03-12

## Goal
- Promote retry dedup `reused` from message-only text into structured API metrics for both:
  - queue declined async export retry-terminal flow;
  - queue declined requeue dry-run async export retry-terminal flow.

## Alfresco Benchmark Mapping
- Reference:
  - `remote-api/.../impl/RenditionsImpl.java`
  - `repository/.../batch/BatchProcessor.java`
- Borrowed ideas:
  - long-running retry operations should expose machine-readable counters for operators and automation.
- Athena extension:
  - explicit `reused` response field and audit detail propagation for selected/bulk retry governance.

## Scope
- Backend:
  - `PreviewQueueDeclinedExportAsyncRetryTerminalResponseDto`
  - `PreviewQueueDeclinedRequeueDryRunExportAsyncRetryTerminalResponseDto`
  - bulk/selected retry audit details for both flows.
- Frontend:
  - retry summary toasts include `reused`.
- Mocked e2e:
  - mock payloads include `reused`;
  - assertions updated to match new summary.

## Implementation
- `ecm-core/src/main/java/com/ecm/core/controller/PreviewDiagnosticsController.java`
  - Added `reused` in response DTO records and construction paths.
  - Extended bulk/selected audit helper signatures with `reused`.
  - Audit details now include `reused=%d`.
- `ecm-core/src/test/java/com/ecm/core/controller/PreviewDiagnosticsControllerSecurityTest.java`
  - Added `$.reused` JSON assertions for bulk/selected retry responses.
  - Added/updated audit context assertions to include `reused=`.
- `ecm-frontend/src/services/previewDiagnosticsService.ts`
  - Added `reused: number` to both retry-terminal response types.
- `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`
  - Retry summary toasts now render:
    - `retried=..., reused=..., skipped=..., failed=...`
- `ecm-frontend/e2e/admin-preview-diagnostics.mock.spec.ts`
  - Added `reused` in mocked retry-terminal payloads.
  - Updated summary assertions for both non-requeue and requeue dry-run async centers.

## Expected Outcomes
- Structured retry dedup observability for UI, scripts, and audit consumers.
- Lower ambiguity in operator decisions (new task vs reused active task).
- Backward-compatible additive field change.
