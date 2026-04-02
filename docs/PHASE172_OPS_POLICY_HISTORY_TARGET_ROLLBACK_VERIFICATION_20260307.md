# Phase 172 - Ops Policy History + Targeted Rollback (Verification)

## Date
2026-03-07

## Scope
- Verify policy history API/controller security.
- Verify preview diagnostics UI compiles with history + target rollback controls.
- Verify mocked E2E flow for history rendering, dry-run, and targeted rollback.

## Commands and results

1. Frontend lint (changed files)
```bash
cd ecm-frontend
npx eslint src/pages/PreviewDiagnosticsPage.tsx src/services/opsPolicyService.ts
```
- Result: PASS

2. Frontend build
```bash
cd ecm-frontend
npm run -s build
```
- Result: PASS

3. Mocked Playwright E2E
```bash
cd ecm-frontend
# start local dev server, then:
ECM_UI_URL=http://localhost:3000 npm run -s e2e -- \
  e2e/admin-preview-diagnostics.mock.spec.ts \
  --project=chromium --reporter=list
```
- Result: PASS (`1 passed`)

4. Backend targeted tests
```bash
cd ecm-core
mvn -q -Dtest=OpsPolicyServiceTest,OpsPolicyControllerSecurityTest,OpsRecoveryControllerSecurityTest,PreviewDiagnosticsControllerSecurityTest test
```
- Result: PASS

5. Backend compile
```bash
cd ecm-core
mvn -q -DskipTests compile
```
- Result: PASS

## Verified outcomes
- Admin can retrieve policy history entries via `/api/v1/ops/policies/{domain}/history`.
- Non-admin access to history endpoint is rejected.
- Preview diagnostics page shows:
  - policy history table;
  - rollback target selector;
  - targeted rollback action integrated with existing policy version chip.
- Mocked E2E confirms dry-run + targeted rollback interaction remains stable.

