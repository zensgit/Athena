# Phase 200 - Dry-Run CSV Export (Benchmark-Aligned with Alfresco) - Verification

## Date
2026-03-07

## Scope
- Verify backend dry-run CSV export endpoint and security.
- Verify queue/dry-run reason breakdown response compatibility.
- Verify frontend export action and mocked E2E behavior.

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
- Admin can export dry-run search-scope failed preview CSV.
- Non-admin users are forbidden for export endpoint.
- Queue and dry-run payloads both carry reason breakdown consistently.
- Advanced Search can trigger dry-run CSV export and keep existing retry/rebuild flow intact.
