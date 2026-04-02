# Phase 281 - Preview Async Contract Annotation and Poll Backoff (Verification)

## Date
- 2026-03-12

## Verification Scope
- Backend:
  - queue-declined async endpoints remain test-pass after OpenAPI contract annotation enrichment.
- Frontend:
  - Preview Diagnostics page compiles/lints with adaptive polling backoff logic.
  - mocked async task-center flow still passes under updated polling behavior.

## Commands
- Backend:
  - `cd ecm-core && mvn -q -Dtest='PreviewDiagnosticsControllerSecurityTest#diagnosticsQueueDeclinedAsyncExportTaskCenterForAdmin+diagnosticsQueueDeclinedRequeueDryRunAsyncExportTaskCenterForAdmin' test`
- Frontend:
  - `cd ecm-frontend && npm run lint -- src/pages/PreviewDiagnosticsPage.tsx e2e/admin-preview-diagnostics.mock.spec.ts`
  - `cd ecm-frontend && npm run build`
  - `cd ecm-frontend && (npx serve -s build -l 5500 >/tmp/athena-serve.log 2>&1 & echo $! >/tmp/athena-serve.pid) && for i in $(seq 1 30); do curl -sf http://localhost:5500 >/dev/null && break; sleep 1; done && ECM_UI_URL=http://localhost:5500 npx playwright test e2e/admin-preview-diagnostics.mock.spec.ts && kill $(cat /tmp/athena-serve.pid)`

## Results
- Backend targeted tests: Passed.
- Frontend lint: Passed.
- Frontend build: Passed.
- Playwright mocked e2e: Passed (`1 passed`).

## Conclusion
- Phase281 is validated: OpenAPI async contract annotations and UI adaptive polling backoff are both integrated without observed regression.
