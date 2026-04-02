# Phase 279 - Preview Queue Declined Async Task Center Standard Paging (Verification)

## Date
- 2026-03-12

## Verification Scope
- Backend:
  - non-requeue and requeue-dry-run async list endpoints support `skipCount/maxItems`.
  - list responses include `paging` metadata.
- Frontend:
  - task center page state and controls compile/lint cleanly.
- Mocked e2e:
  - queue-declined task-center flow passes with paging-aware API mocks.

## Commands
- Backend:
  - `cd ecm-core && mvn -q -Dtest='PreviewDiagnosticsControllerSecurityTest#diagnosticsQueueDeclinedAsyncListSupportsPaging+diagnosticsQueueDeclinedRequeueDryRunAsyncListSupportsPaging+diagnosticsQueueDeclinedAsyncExportTaskCenterForAdmin+diagnosticsQueueDeclinedRequeueDryRunExportTaskCenterForAdmin+diagnosticsQueueDeclinedRequeueDryRunAsyncInvalidStatusFilterReturnsBadRequest+diagnosticsQueueDeclinedRequeueDryRunAsyncRetryTerminalRejectsActiveStatusFilter' test`
- Frontend:
  - `cd ecm-frontend && npm run lint -- src/pages/PreviewDiagnosticsPage.tsx src/services/previewDiagnosticsService.ts e2e/admin-preview-diagnostics.mock.spec.ts`
  - `cd ecm-frontend && npm run build`
  - `cd ecm-frontend && (npx serve -s build -l 5500 & wait-for-ready) && ECM_UI_URL=http://localhost:5500 npx playwright test e2e/admin-preview-diagnostics.mock.spec.ts`

## Results
- Backend targeted tests: Passed.
- Frontend lint: Passed.
- Frontend build: Passed.
- Playwright mocked e2e: Passed (`1 passed`).

## Notes
- First Playwright attempt failed with `ERR_CONNECTION_REFUSED` before static server readiness.
- Re-run with explicit readiness wait (`curl` polling) passed.

## Conclusion
- Phase279 standard paging is validated end-to-end for queue-declined async task centers (non-requeue + requeue dry-run) with backward-compatible `limit` support.
