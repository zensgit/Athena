# Phase 170 - Ops Policy Center + Search Faceted Path (Verification)

## Date
2026-03-06

## Scope
- Verify new ops policy controller endpoints and permissions.
- Verify unified ops recovery + preview diagnostics UI service migration compiles.
- Verify faceted search path integration for frontend search service.

## Commands and results

1. Backend controller tests
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

3. Frontend lint (changed files)
```bash
cd ecm-frontend
npx eslint src/pages/PreviewDiagnosticsPage.tsx \
  src/services/opsRecoveryService.ts \
  src/services/opsPolicyService.ts \
  src/services/nodeService.ts \
  src/store/slices/nodeSlice.ts
```
- Result: PASS

4. Frontend build
```bash
cd ecm-frontend
npm run -s build
```
- Result: PASS

## Verified behaviors
- `/api/v1/ops/policies/**` endpoints are admin-only.
- Admin can read policy state, update policy profile, and rollback policy version.
- Preview diagnostics reason-group actions and dead-letter replay are routed via unified ops recovery service contract.
- Search advanced path consumes `/search/faceted` response and uses `totalHits` with facets payload passthrough.
