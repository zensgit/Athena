# Phase 197 - Search-Scope Dry-Run Reason Breakdown (Verification)

## Date
2026-03-07

## Scope
- Verify backend dry-run response includes reason breakdown fields.
- Verify frontend type/UI changes compile and pass lint/build.
- Verify mocked Advanced Search flow remains stable.

## Commands and results

1. Backend targeted tests
```bash
cd ecm-core
mvn -q -Dtest=SearchControllerTest,SearchControllerSecurityTest test
```
- Result: PASS

2. Backend compile
```bash
cd ecm-core
mvn -q -DskipTests compile
```
- Result: PASS

3. Frontend lint
```bash
cd ecm-frontend
npm run -s lint -- src/pages/AdvancedSearchPage.tsx src/services/nodeService.ts e2e/advanced-search-preview-batch-scope.mock.spec.ts
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
  e2e/advanced-search-preview-batch-scope.mock.spec.ts \
  --project=chromium --reporter=list
```
- Result: PASS (`1 passed`)

## Verified outcomes
- Dry-run response now carries `reasonBreakdown`.
- Dry-run reason breakdown is asserted by backend controller test.
- Advanced Search dry-run panel can render reason chips without regression.
- Existing retry/rebuild all-matched actions remain functional in mock flow.
