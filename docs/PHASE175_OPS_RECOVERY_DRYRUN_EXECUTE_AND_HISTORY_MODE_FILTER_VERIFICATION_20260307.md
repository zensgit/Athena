# Phase 175 - Ops Recovery Dry-run Execute + History Mode Filter (Verification)

## Date
2026-03-07

## Scope
- Verify backend history mode filter behavior and security tests.
- Verify frontend compile/lint with dry-run execute path and history mode selector.
- Verify mocked preview diagnostics E2E for new action path + timeline filtering.

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

3. Frontend lint (changed src files)
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
- Recovery history supports mode filtering and returns mode-aligned events.
- Dry-run panel can execute recovery directly with current criteria.
- Preview diagnostics mock regression remains green with added execute + mode-filter interactions.
