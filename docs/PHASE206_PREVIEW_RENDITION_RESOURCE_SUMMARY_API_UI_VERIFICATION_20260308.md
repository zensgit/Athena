# Phase 206 - Preview Rendition Resource Summary API + UI - Verification

## Date
2026-03-08

## Scope
- Verify backend rendition summary endpoint and security coverage.
- Verify frontend diagnostics page integration, lint/build stability.
- Verify mocked Playwright flow for preview diagnostics and advanced-search batch workflows.

## Commands and results

1. Backend targeted tests
```bash
cd ecm-core
mvn -q -Dtest=SearchControllerTest,SearchControllerSecurityTest,PreviewDiagnosticsControllerSecurityTest,BatchExecutorTest test
```
- Result: PASS

2. Backend compile
```bash
cd ecm-core
mvn -q -DskipTests compile
```
- Result: PASS

3. Frontend lint (targeted)
```bash
cd ecm-frontend
npm run -s lint -- \
  src/pages/AdvancedSearchPage.tsx \
  src/services/nodeService.ts \
  src/services/previewDiagnosticsService.ts \
  src/pages/PreviewDiagnosticsPage.tsx \
  e2e/advanced-search-preview-batch-scope.mock.spec.ts \
  e2e/admin-preview-diagnostics.mock.spec.ts
```
- Result: PASS

4. Frontend production build
```bash
cd ecm-frontend
npm run -s build
```
- Result: PASS

5. Mocked Playwright E2E
```bash
cd ecm-frontend
ECM_UI_URL=http://localhost:5601 npx playwright test \
  e2e/advanced-search-preview-batch-scope.mock.spec.ts \
  e2e/admin-preview-diagnostics.mock.spec.ts \
  --project=chromium
```
- Result: PASS (`3 passed`)

## Verified outcomes
- `/preview/diagnostics/renditions/summary` supports admin-only access and returns deterministic summary payload.
- Preview diagnostics UI now presents rendition resource summary with refresh, status chips, and top reasons.
- Existing advanced-search preview batch mocked flows remain green after integration.
