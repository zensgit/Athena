# Phase 190 - Ops Recovery History Compare Breakdown Sort + TopN (Verification)

## Date
2026-03-07

## Scope
- Verify compare-breakdown APIs accept and apply `sort + limit`.
- Verify compare-breakdown export carries selected `sort + limit` metadata.
- Verify UI controls and mocked E2E request propagation for TopN/sort.

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
- Compare-breakdown endpoint supports sort/TopN controls and returns deterministic ranked subsets.
- Compare-breakdown export CSV includes sort/limit metadata and respects selected filters.
- UI controls update compare-breakdown fetch behavior.
- E2E confirms both default and user-updated `limit/sort` values are propagated to API/export requests.
