# Phase 178 - Ops Recovery History Actor Filter (Verification)

## Date
2026-03-07

## Scope
- Verify backend actor-filter behavior for history list/export endpoints.
- Verify frontend actor filter integration and non-regression.
- Verify mocked E2E covers actor-filtered history and export calls.

## Commands and results

1. Backend targeted tests
```bash
cd ecm-core
mvn -q -Dtest=OpsRecoveryControllerSecurityTest,OpsPolicyControllerSecurityTest,PreviewDiagnosticsControllerSecurityTest test
```
- Result: PASS

2. Backend compile
```bash
cd ecm-core
mvn -q -DskipTests compile
```
- Result: PASS

3. Frontend lint (changed source files)
```bash
cd ecm-frontend
npm run -s lint -- src/pages/PreviewDiagnosticsPage.tsx src/services/opsRecoveryService.ts src/pages/AdvancedSearchPage.tsx
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
# start local dev server, then:
ECM_UI_URL=http://localhost:3000 npm run -s e2e -- \
  e2e/admin-preview-diagnostics.mock.spec.ts \
  --project=chromium --reporter=list
```
- Result: PASS (`1 passed`)

## Verified outcomes
- `history` and `history/export` both accept `actor` and return actor-scoped data.
- UI actor filter drives list and export with consistent query semantics.
- Existing diagnostics workflow remains stable while adding actor-scoped traceability.
