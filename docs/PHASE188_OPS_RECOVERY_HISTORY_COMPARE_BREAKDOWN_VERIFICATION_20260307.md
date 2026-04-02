# Phase 188 - Ops Recovery History Compare Breakdown By Mode (Verification)

## Date
2026-03-07

## Scope
- Verify compare breakdown API is admin-protected and returns per-mode delta metrics.
- Verify frontend diagnostics panel renders compare breakdown chips.
- Verify mocked E2E propagates history filters to compare breakdown requests.

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

## Verified outcomes
- `/api/v1/ops/recovery/history/summary/compare/breakdown` is admin-only.
- Compare breakdown payload includes current/previous counts and computed deltas per event/mode.
- UI renders mode delta chips and keeps existing compare/trend/summary/history behavior stable.
- E2E confirms breakdown API receives active `days/mode/actor/eventType` filters.
