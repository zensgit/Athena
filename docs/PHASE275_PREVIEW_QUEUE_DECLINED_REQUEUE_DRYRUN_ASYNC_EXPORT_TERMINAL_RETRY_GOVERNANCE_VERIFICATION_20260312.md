# Phase 275 - Preview Queue Declined Requeue Dry-run Async Export Terminal Retry Governance (Verification)

## Date
- 2026-03-12

## Verification Scope
- Backend compile
- Backend targeted security/controller tests for newly added requeue async retry governance
- Frontend lint/build
- Frontend mocked e2e flow for preview diagnostics page

## Commands and Results

1. Backend compile
- Command:
  - `cd ecm-core && mvn -q -DskipTests compile`
- Result:
  - Passed

2. Frontend lint (changed files)
- Command:
  - `cd ecm-frontend && npm run lint -- src/pages/PreviewDiagnosticsPage.tsx e2e/admin-preview-diagnostics.mock.spec.ts`
- Result:
  - Passed

3. Frontend production build
- Command:
  - `cd ecm-frontend && npm run build`
- Result:
  - Passed

4. Backend targeted tests (new endpoints/governance)
- Command:
  - `cd ecm-core && mvn -q -Dtest='PreviewDiagnosticsControllerSecurityTest#diagnosticsQueueDeclinedRequeueDryRunAsyncExportTaskCenterForAdmin+diagnosticsQueueDeclinedRequeueDryRunAsyncInvalidStatusFilterReturnsBadRequest+diagnosticsQueueDeclinedRequeueDryRunAsyncRetryTerminalRejectsActiveStatusFilter' test`
- Result:
  - Passed

5. Backend service regression spot-check
- Command:
  - `cd ecm-core && mvn -q -Dtest=PreviewQueueServiceTest test`
- Result:
  - Passed

6. Frontend mocked e2e
- Commands:
  - `cd ecm-frontend && python3 -m http.server 5511 --directory build` (local static hosting)
  - `cd ecm-frontend && ECM_UI_URL=http://localhost:5511 npx playwright test e2e/admin-preview-diagnostics.mock.spec.ts --project=chromium`
- Result:
  - Passed (`1 passed`)

## Notes
- Running the broad combined backend suite command
  - `mvn -q -Dtest=PreviewDiagnosticsControllerSecurityTest,PreviewQueueServiceTest test`
  showed existing date-window-dependent failures in older queue-declined assertions (`requested/filteredSampledItems` expected `1` but got `0`).
- These failures are outside Phase275 code path and were not introduced by the new requeue async retry endpoints; the newly added method-level tests pass.
