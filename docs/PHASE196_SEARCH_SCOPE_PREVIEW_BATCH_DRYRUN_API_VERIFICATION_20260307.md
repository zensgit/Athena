# Phase 196 - Search-Scope Preview Batch Dry-Run API (Verification)

## Date
2026-03-07

## Scope
- Verify backend dry-run endpoint for all-matched failed preview scope.
- Verify frontend dry-run integration in Advanced Search.
- Verify mock E2E payload and behavior for dry-run action.

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
- `/api/v1/search/preview/queue-failed/dry-run` is admin-protected.
- Dry-run endpoint returns capped all-matched summary and sampled failed items.
- Advanced Search can trigger dry-run for:
  - all matched scope
  - per-reason all matched scope
- Mock E2E confirms dry-run request payload propagation and verifies no direct single-document queue call for dry-run action.
