# Phase 277 - Preview Queue Declined Async Export Retry Dedup Governance (Verification)

## Date
- 2026-03-12

## Verification Scope
- Backend:
  - single retry dedup hit returns existing active task metadata;
  - bulk/selected retry include `REUSED` outcome when dedup hit;
  - status guardrails keep original constraints.
- Frontend:
  - row retry shows reused toast on dedup hit.
- Mocked e2e:
  - validates retried then reused retry flow for queue declined async export tasks.

## Commands
- Backend:
  - `cd ecm-core && mvn -q -DskipTests compile`
  - `cd ecm-core && mvn -q -Dtest='PreviewDiagnosticsControllerSecurityTest#diagnosticsQueueDeclinedAsyncExportTaskCenterForAdmin+diagnosticsQueueDeclinedAsyncInvalidStatusFilterReturnsBadRequest+diagnosticsQueueDeclinedAsyncRetryTerminalRejectsActiveStatusFilter' test`
- Frontend:
  - `cd ecm-frontend && npm run lint -- src/pages/PreviewDiagnosticsPage.tsx e2e/admin-preview-diagnostics.mock.spec.ts`
  - `cd ecm-frontend && npm run build`
  - `cd ecm-frontend && npx serve -s build -l 5500`
  - `cd ecm-frontend && ECM_UI_URL=http://localhost:5500 npx playwright test e2e/admin-preview-diagnostics.mock.spec.ts`

## Results
- Backend compile: Passed.
- Backend targeted security tests: Passed.
  - `PreviewDiagnosticsControllerSecurityTest#diagnosticsQueueDeclinedAsyncExportTaskCenterForAdmin`
  - `PreviewDiagnosticsControllerSecurityTest#diagnosticsQueueDeclinedAsyncInvalidStatusFilterReturnsBadRequest`
  - `PreviewDiagnosticsControllerSecurityTest#diagnosticsQueueDeclinedAsyncRetryTerminalRejectsActiveStatusFilter`
- Frontend lint (`PreviewDiagnosticsPage.tsx` + mocked e2e spec): Passed.
- Frontend production build: Passed.
- Playwright mocked e2e: Passed.
  - `e2e/admin-preview-diagnostics.mock.spec.ts`
  - `1 passed (1.0m)`

## Notes
- During verification, one mock-stubbing conflict in `PreviewDiagnosticsControllerSecurityTest` was fixed by switching post-failure re-stubbing to `doAnswer/doReturn(...).when(...)`, and governance-audit assertions were aligned with current selected/bulk retry semantics.

## Conclusion
- Phase277 retry dedup governance for non-requeue `queue/declined/export-async` flow is verified end-to-end (backend API semantics, frontend UX feedback, mocked e2e regression).
