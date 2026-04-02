# Phase 187 - Ops Recovery History Compare CSV Export (Verification)

## Date
2026-03-07

## Scope
- Verify compare CSV export API is admin-protected and returns expected payload/header.
- Verify frontend compare export action works in diagnostics page.
- Verify mocked E2E propagates current history filters into compare export request.

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
- `/api/v1/ops/recovery/history/summary/compare/export` is admin-only.
- Export response is downloadable CSV with `X-Ops-Recovery-Compare-Count`.
- UI button `Export Compare CSV` triggers download flow and success toast.
- E2E confirms compare export request carries `days/mode/actor/eventType` filters.
