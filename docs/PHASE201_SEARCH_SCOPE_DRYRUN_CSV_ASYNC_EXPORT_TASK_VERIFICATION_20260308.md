# Phase 201 - Search-Scope Dry-Run CSV Async Export Task - Verification

## Date
2026-03-08

## Scope
- Verify backend async export task endpoints behavior and security.
- Verify frontend async polling/download flow.
- Verify mocked E2E scenario for advanced search preview batch panel.

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
- Admin can start async dry-run CSV export task and poll task status.
- Download endpoint returns CSV only when task reaches `COMPLETED`.
- Non-admin access to async endpoints is forbidden.
- Advanced Search UI export action no longer depends on one blocking sync response; it now uses task polling and staged progress text.
