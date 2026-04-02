# Phase 183 - Ops Recovery History Summary CSV Export (Verification)

## Date
2026-03-07

## Scope
- Verify backend summary CSV export endpoint and security.
- Verify frontend summary-export wiring and event type option.
- Verify mocked E2E coverage for summary export flow.

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
ECM_UI_URL=http://localhost:3000 npm run -s e2e -- \
  e2e/admin-preview-diagnostics.mock.spec.ts \
  --project=chromium --reporter=list
```
- Result: PASS (`1 passed`)
- Note: first run failed due local server not started (`ERR_CONNECTION_REFUSED`); rerun after starting frontend dev server passed.

## Verified outcomes
- `/api/v1/ops/recovery/history/summary/export` is admin-protected and returns CSV payload.
- Summary export applies same filters as summary/list (`days/mode/actor/eventType`).
- UI supports one-click summary export and surfaces success feedback.
- E2E confirms request propagation and non-regression in diagnostics panel flow.
