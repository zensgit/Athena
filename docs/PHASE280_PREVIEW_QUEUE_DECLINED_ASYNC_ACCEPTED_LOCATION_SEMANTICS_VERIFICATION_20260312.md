# Phase 280 - Preview Queue Declined Async Accepted+Location Semantics (Verification)

## Date
- 2026-03-12

## Verification Scope
- Backend:
  - queue-declined async create/retry endpoints return `202` and remain functionally correct.
  - bulk retry and selected retry endpoints return `202` for both non-requeue and requeue dry-run task centers.
- Frontend:
  - mocked async contracts accept `202 + Location` without UI flow regression.

## Commands
- Backend:
  - `cd ecm-core && mvn -q -Dtest='PreviewDiagnosticsControllerSecurityTest#diagnosticsQueueDeclinedAsyncExportTaskCenterForAdmin+diagnosticsQueueDeclinedRequeueDryRunAsyncExportTaskCenterForAdmin+diagnosticsQueueDeclinedAsyncListSupportsPaging+diagnosticsQueueDeclinedRequeueDryRunAsyncListSupportsPaging+diagnosticsQueueDeclinedAsyncInvalidStatusFilterReturnsBadRequest+diagnosticsQueueDeclinedRequeueDryRunAsyncInvalidStatusFilterReturnsBadRequest+diagnosticsQueueDeclinedAsyncRetryTerminalRejectsActiveStatusFilter+diagnosticsQueueDeclinedRequeueDryRunAsyncRetryTerminalRejectsActiveStatusFilter' test`
- Frontend:
  - `cd ecm-frontend && npm run lint -- src/services/previewDiagnosticsService.ts src/pages/PreviewDiagnosticsPage.tsx e2e/admin-preview-diagnostics.mock.spec.ts`
  - `cd ecm-frontend && npm run build`
  - `cd ecm-frontend && (npx serve -s build -l 5500 >/tmp/athena-serve.log 2>&1 & echo $! >/tmp/athena-serve.pid) && for i in $(seq 1 30); do curl -sf http://localhost:5500 >/dev/null && break; sleep 1; done && ECM_UI_URL=http://localhost:5500 npx playwright test e2e/admin-preview-diagnostics.mock.spec.ts && kill $(cat /tmp/athena-serve.pid)`

## Results
- Backend targeted tests: Passed.
- Frontend lint: Passed.
- Frontend build: Passed.
- Playwright mocked e2e: Passed (`1 passed`).

## Conclusion
- Phase280 accepted+location async semantics are validated for queue-declined async create/retry governance (non-requeue + requeue dry-run) with no observed regression in mocked UI flow.
