# Phase 202 - Search-Scope Dry-Run CSV Async Task Cancel + List - Verification

## Date
2026-03-08

## Scope
- Verify backend async task list/cancel behavior and security.
- Verify frontend cancel export UX.
- Verify mocked E2E for both completed export path and cancelled export path.

## Commands and results

1. Backend targeted tests
```bash
cd ecm-core
mvn -q -Dtest=SearchControllerTest,SearchControllerSecurityTest test
```
- Result: PASS

2. Frontend lint
```bash
cd ecm-frontend
npm run -s lint -- src/pages/AdvancedSearchPage.tsx src/services/nodeService.ts e2e/advanced-search-preview-batch-scope.mock.spec.ts
```
- Result: PASS

3. Frontend production build
```bash
cd ecm-frontend
npm run -s build
```
- Result: PASS

4. Mocked Playwright E2E (2 scenarios)
```bash
cd ecm-frontend
ECM_UI_URL=http://localhost:3000 npm run -s e2e -- \
  e2e/advanced-search-preview-batch-scope.mock.spec.ts \
  --project=chromium --reporter=list
```
- Result: PASS (`2 passed`)

## Verified outcomes
- Admin can list recent async CSV export tasks.
- Admin can cancel queued/running export task; cancelled task becomes terminal and cannot be downloaded.
- Non-admin access to list/cancel endpoints is forbidden.
- Advanced Search export panel supports in-flight cancel without forcing user to wait for timeout.
