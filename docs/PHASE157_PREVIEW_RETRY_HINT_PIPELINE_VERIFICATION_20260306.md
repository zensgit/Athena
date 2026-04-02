# Phase157 Verification: Explicit Preview Retry-Hint Pipeline

## Date
2026-03-06

## Verification focus
- Ensure explicit retry hint is honored by queue logic.
- Ensure no regressions in preview diagnostics and UI integration paths.

## Commands
1. Queue logic + controller tests
- `cd ecm-core && mvn -q -Dtest=PreviewDiagnosticsControllerSecurityTest,PreviewQueueServiceTest test`

2. Frontend static checks
- `cd ecm-frontend && npm run -s lint -- src/pages/PreviewDiagnosticsPage.tsx src/services/previewDiagnosticsService.ts`

3. Frontend build and diagnostics mocked flow
- `cd ecm-frontend && npm run -s build`
- `python3 -m http.server 5500 --directory build`
- `cd ecm-frontend && npx playwright test e2e/admin-preview-diagnostics.mock.spec.ts --project=chromium`

## Results
- `PreviewQueueServiceTest`: PASS
  - includes new case `retriesWhenPreviewResultExplicitlyRequestsRetry`.
- `PreviewDiagnosticsControllerSecurityTest`: PASS
- Frontend lint/build: PASS
- Mocked diagnostics Playwright flow: PASS
