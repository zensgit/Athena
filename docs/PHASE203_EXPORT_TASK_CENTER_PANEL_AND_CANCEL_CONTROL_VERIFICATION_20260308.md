# Phase 203 - Export Task Center Panel + Cancel Control - Verification

## Date
2026-03-08

## Scope
- Verify backend async export task center endpoints (list/cancel) and security.
- Verify frontend task center panel behavior and cancel/download controls.
- Verify mocked E2E scenarios remain green.

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
- Result: PASS (`2 passed`)

## Verified outcomes
- Export async task center supports recent-task listing and user cancellation.
- Cancelled tasks are terminal and blocked from download.
- Frontend now provides operational visibility (task list + status chips + actions) in the dry-run area.
