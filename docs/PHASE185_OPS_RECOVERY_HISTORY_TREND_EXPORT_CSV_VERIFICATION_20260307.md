# Phase 185 - Ops Recovery History Trend CSV Export (Verification)

## Date
2026-03-07

## Scope
- Verify backend trend export endpoint security and CSV payload.
- Verify frontend trend export button wiring.
- Verify mocked E2E covers trend export request propagation.

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
- `/api/v1/ops/recovery/history/summary/trend/export` is admin-protected.
- Trend export CSV returns expected columns (`day,count`) and count header.
- UI can export trend CSV directly from diagnostics history panel.
- E2E confirms request filters (`days/mode/actor/eventType`) are propagated.
