# Phase 278 - Preview Queue Declined Retry Reused Structured Metrics (Verification)

## Date
- 2026-03-12

## Verification Scope
- Backend:
  - retry-terminal responses expose structured `reused` counters.
  - bulk/selected retry audit details include `reused=`.
- Frontend:
  - retry summary toasts include `reused`.
- Mocked e2e:
  - regression flow passes with updated summary and mock payload semantics.

## Commands
- Backend:
  - `cd ecm-core && mvn -q -Dtest='PreviewDiagnosticsControllerSecurityTest#diagnosticsQueueDeclinedAsyncExportTaskCenterForAdmin+diagnosticsQueueDeclinedAsyncInvalidStatusFilterReturnsBadRequest+diagnosticsQueueDeclinedAsyncRetryTerminalRejectsActiveStatusFilter+diagnosticsQueueDeclinedRequeueDryRunAsyncExportTaskCenterForAdmin+diagnosticsQueueDeclinedRequeueDryRunAsyncInvalidStatusFilterReturnsBadRequest+diagnosticsQueueDeclinedRequeueDryRunAsyncRetryTerminalRejectsActiveStatusFilter' test`
- Frontend:
  - `cd ecm-frontend && npm run lint -- src/pages/PreviewDiagnosticsPage.tsx e2e/admin-preview-diagnostics.mock.spec.ts src/services/previewDiagnosticsService.ts`
  - `cd ecm-frontend && npm run build`
  - `cd ecm-frontend && npx serve -s build -l 5500`
  - `cd ecm-frontend && ECM_UI_URL=http://localhost:5500 npx playwright test e2e/admin-preview-diagnostics.mock.spec.ts`

## Results
- Backend targeted security tests: Passed.
- Frontend lint: Passed.
- Frontend build: Passed.
- Playwright mocked e2e: Passed (`1 passed`).

## Conclusion
- Phase278 reused-structured-metrics governance is validated end-to-end for queue-declined async retry flows (non-requeue + requeue dry-run async).
