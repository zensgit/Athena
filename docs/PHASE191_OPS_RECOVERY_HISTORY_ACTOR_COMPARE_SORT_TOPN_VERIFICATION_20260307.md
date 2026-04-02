# Phase 191 - Ops Recovery History Actor Compare Sort + TopN (Verification)

## Date
2026-03-07

## Scope
- Verify actor compare endpoint is admin-protected.
- Verify actor compare supports sort + TopN semantics.
- Verify frontend actor compare controls and E2E request propagation.

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
- `/api/v1/ops/recovery/history/summary/compare/actors` is admin-only.
- Actor compare returns current/previous/delta metrics with sort/TopN metadata.
- UI actor compare sort/TopN controls trigger data refresh with expected query params.
- E2E confirms both default and changed `limit/sort` values reach actor compare API.
