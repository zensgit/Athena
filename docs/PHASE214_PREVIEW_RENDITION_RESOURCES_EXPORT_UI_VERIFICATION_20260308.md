# Phase 214 - Preview Rendition Resources Export UI - Verification

## Date
2026-03-08

## Scope
- Verify frontend lint/build stability after export button and service integration.
- Verify mocked Playwright flow for rendition resources CSV export action.

## Commands and results

1. Frontend lint
```bash
cd ecm-frontend
npm run -s lint -- src/services/previewDiagnosticsService.ts src/pages/PreviewDiagnosticsPage.tsx e2e/admin-preview-diagnostics.mock.spec.ts
```
- Result: PASS

2. Frontend build
```bash
cd ecm-frontend
npm run -s build
```
- Result: PASS

3. Mocked Playwright E2E
```bash
cd ecm-frontend
ECM_UI_URL=http://localhost:5601 npx playwright test \
  e2e/admin-preview-diagnostics.mock.spec.ts \
  e2e/advanced-search-preview-batch-scope.mock.spec.ts \
  --project=chromium
```
- Result: PASS (`3 passed`)

## Verified outcomes
- Rendition summary panel export button triggers CSV download path and success toast.
- Export request carries expected `days` and `limit` parameters under mocked assertions.
