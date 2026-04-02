# Phase 192 - Ops Recovery History Actor Compare CSV Export (Verification)

## Date
2026-03-07

## Scope
- Verify actor-compare export endpoint is admin-protected.
- Verify actor-compare export output includes metadata + actor rows.
- Verify frontend export button flow and mocked E2E propagation.

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
- `/api/v1/ops/recovery/history/summary/compare/actors/export` is admin-only.
- CSV export includes actor compare metadata (`sortBy`, `requestedLimit`, `totalItems`, `limited`) and actor delta rows.
- UI button `Export Actor Compare CSV` executes successfully and shows success toast.
- E2E confirms actor compare export request contains active filters and selected `limit/sort`.
