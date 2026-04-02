# Phase 276 - Preview Queue Declined Requeue Dry-run Async Export Retry Dedup Governance (Verification)

## Date
- 2026-03-12

## Verification Scope
- Backend:
  - Single retry dedup hit reuses active task id.
  - Bulk/selected retry can return `REUSED` outcome on dedup hit.
  - Existing status filter guardrails remain valid.
- Frontend:
  - Retry action displays dedup reuse toast when API returns `deduplicated=true`.
  - Mocked e2e validates dedup branch without regressing existing flow.

## Commands
- Backend compile/tests:
  - `cd ecm-core && mvn -q -DskipTests compile`
  - `cd ecm-core && mvn -q -Dtest='PreviewDiagnosticsControllerSecurityTest#diagnosticsQueueDeclinedRequeueDryRunAsyncExportTaskCenterForAdmin' test`
- Frontend checks:
  - `cd ecm-frontend && npm run lint -- src/pages/PreviewDiagnosticsPage.tsx e2e/admin-preview-diagnostics.mock.spec.ts`
  - `cd ecm-frontend && npm run build`
  - `cd ecm-frontend && ECM_UI_URL=http://localhost:5511 npx playwright test e2e/admin-preview-diagnostics.mock.spec.ts --project=chromium`

## Results
- PASS: `cd ecm-core && mvn -q -DskipTests compile`
- PASS: `cd ecm-core && mvn -q -Dtest='PreviewDiagnosticsControllerSecurityTest#diagnosticsQueueDeclinedRequeueDryRunAsyncExportTaskCenterForAdmin' test`
- PASS: `cd ecm-core && mvn -q -Dtest='PreviewDiagnosticsControllerSecurityTest#diagnosticsQueueDeclinedRequeueDryRunAsyncExportTaskCenterForAdmin+diagnosticsQueueDeclinedRequeueDryRunAsyncInvalidStatusFilterReturnsBadRequest+diagnosticsQueueDeclinedRequeueDryRunAsyncRetryTerminalRejectsActiveStatusFilter' test`
- PASS: `cd ecm-core && mvn -q -Dtest=PreviewQueueServiceTest test`
- PASS: `cd ecm-frontend && npm run lint -- src/pages/PreviewDiagnosticsPage.tsx e2e/admin-preview-diagnostics.mock.spec.ts`
- PASS: `cd ecm-frontend && npm run build`
- PASS: `cd ecm-frontend && (PORT=5511 node scripts/serve-build.js & ECM_UI_URL=http://127.0.0.1:5511 npx playwright test e2e/admin-preview-diagnostics.mock.spec.ts --project=chromium)`

## Conclusion
- Phase 276 dedup governance is verified for backend retry API behavior and frontend mocked task-center flow.
- No regression observed in targeted backend queue service tests and frontend build/lint/mocked e2e gate.
