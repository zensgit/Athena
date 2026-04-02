# Phase156 Verification: Preview Reason-Scope Batch Queue

## Date
2026-03-06

## Verification commands
1. Backend security/controller tests
- `cd ecm-core && mvn -q -Dtest=PreviewDiagnosticsControllerSecurityTest,PreviewQueueServiceTest test`

2. Frontend lint
- `cd ecm-frontend && npm run -s lint -- src/pages/PreviewDiagnosticsPage.tsx src/services/previewDiagnosticsService.ts`

3. Frontend unit regression spot check
- `cd ecm-frontend && npm test -- --watchAll=false --runInBand src/utils/previewStatusUtils.test.ts`

4. Frontend build + mocked E2E
- `cd ecm-frontend && npm run -s build`
- `python3 -m http.server 5500 --directory build`
- `cd ecm-frontend && npx playwright test e2e/admin-preview-diagnostics.mock.spec.ts --project=chromium`

## Results
- Backend tests: PASS
- Frontend lint (`src` targets): PASS
- Frontend unit test: PASS
- Mocked Playwright spec: PASS

## Notes
- Running Playwright directly without a static server on `:5500` returns `ERR_CONNECTION_REFUSED`; serving `build/` resolves this.
