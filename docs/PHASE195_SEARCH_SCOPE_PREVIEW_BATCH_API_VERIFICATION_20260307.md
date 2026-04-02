# Phase 195 - Search-Scope Preview Batch Queue API (Verification)

## Date
2026-03-07

## Scope
- Verify backend admin API for search-scope failed preview batch queue.
- Verify frontend integration and mock E2E flow.

## Commands and results

1. Backend targeted tests
```bash
cd ecm-core
mvn -q -Dtest=SearchControllerTest,SearchControllerSecurityTest test
```
- Result: PASS

2. Backend compile
```bash
cd ecm-core
mvn -q -DskipTests compile
```
- Result: PASS

3. Frontend lint
```bash
cd ecm-frontend
npm run -s lint -- src/pages/AdvancedSearchPage.tsx src/services/nodeService.ts e2e/advanced-search-preview-batch-scope.mock.spec.ts
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
ECM_UI_URL=http://localhost:3000 npm run -s e2e -- \
  e2e/advanced-search-preview-batch-scope.mock.spec.ts \
  --project=chromium --reporter=list
```
- Result: PASS (`1 passed`)

## Verified outcomes
- `/api/v1/search/preview/queue-failed` is admin-protected.
- API supports all-matched retry queue flow with bounded caps and summary metrics.
- Advanced Search all-matched actions propagate expected payload to backend batch API.
- Current-page retry path remains functional.
