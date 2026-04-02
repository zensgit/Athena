# Phase 198 - Dry-Run Reason Breakdown Actionable Operations (Verification)

## Date
2026-03-07

## Scope
- Verify new reason-level dry-run action controls in Advanced Search.
- Verify existing all-matched and current-page preview queue flows remain valid.

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
- Dry-run reason breakdown row can directly trigger all-matched queue for selected reason.
- Selected reason is propagated to `/api/v1/search/preview/queue-failed` payload.
- Existing retry actions (current page + all matched) continue to pass in mocked flow.
