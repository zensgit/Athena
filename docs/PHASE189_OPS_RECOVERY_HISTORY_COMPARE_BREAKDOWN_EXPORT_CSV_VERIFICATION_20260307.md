# Phase 189 - Ops Recovery History Compare Breakdown CSV Export (Verification)

## Date
2026-03-07

## Scope
- Verify compare-breakdown export endpoint is admin-protected.
- Verify export response headers/body include compare-breakdown rows.
- Verify frontend button flow + mocked E2E request propagation.

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
- `/api/v1/ops/recovery/history/summary/compare/breakdown/export` is admin-only.
- CSV export includes compare-breakdown schema + expected values with `X-Ops-Recovery-Compare-Breakdown-Count`.
- UI export button succeeds and displays success toast.
- E2E confirms compare-breakdown export carries active `days/mode/actor/eventType` filters.
