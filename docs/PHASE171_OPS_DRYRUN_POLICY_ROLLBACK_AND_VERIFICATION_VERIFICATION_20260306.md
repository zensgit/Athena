# Phase 171 - Ops Dry-Run + Policy Rollback UX and Verification Hardening (Verification)

## Date
2026-03-06

## Scope
- Verify frontend Preview Diagnostics dry-run + rollback additions.
- Verify mocked E2E route migration to `ops/*`.
- Verify backend policy service test coverage.

## Commands and results

1. Frontend lint (changed UI file)
```bash
cd ecm-frontend
npx eslint src/pages/PreviewDiagnosticsPage.tsx
```
- Result: PASS

2. Frontend production build
```bash
cd ecm-frontend
npm run -s build
```
- Result: PASS

3. Mocked Playwright E2E (preview diagnostics)
```bash
cd ecm-frontend
# start UI dev server, then:
ECM_UI_URL=http://localhost:3000 npm run -s e2e -- \
  e2e/admin-preview-diagnostics.mock.spec.ts \
  --project=chromium --reporter=list
```
- Result: PASS (`1 passed`)

4. Backend policy + ops controller security and service tests
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
- Preview diagnostics top-reason row supports dry-run impact estimation without enqueue side effects.
- Failure policy panel supports one-click rollback via unified policy center endpoint.
- Mocked diagnostics E2E no longer depends on legacy `preview/diagnostics` policy/recovery routes.
- Mocked diagnostics E2E asserts both:
  - dry-run action toast (`matched/queued/skipped/failed`);
  - policy rollback action toast.
- `OpsPolicyService` behavior for bootstrap/update/rollback is covered by deterministic unit tests.
