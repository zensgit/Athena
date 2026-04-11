# Development And Verification: Frontend Acceptance Closure

Date: 2026-04-11

## Scope

This pass closed the remaining review and acceptance gaps for:

- Tenant quota / metrics display
- Transfer replication job entry report UI
- CMIS Explorer smoke coverage

## Changes

### Tenant metrics hardening

Files:

- `ecm-frontend/src/pages/TenantAdminPage.tsx`
- `ecm-frontend/src/pages/TenantAdminPage.test.tsx`
- `ecm-core/src/main/java/com/ecm/core/service/TenantMetricsService.java`
- `ecm-core/src/test/java/com/ecm/core/service/TenantMetricsServiceTest.java`

What changed:

- Tenant metrics cache is now invalidated after manual refresh and tenant mutations.
- Tenant metrics failure state is now explicit instead of looking like a permanent "loading on view" state.
- Tenant metrics cards now expose a retry button after a failed fetch.
- `storageAvailableBytes` is now clamped to `0` when a tenant is already over quota.

Why:

- Prevent stale quota/storage UI after backend changes.
- Prevent misleading loading state after transient API failures.
- Avoid negative available-byte values reaching the frontend.

### Transfer job report hardening

Files:

- `ecm-frontend/src/pages/TransferReplicationPage.tsx`
- `ecm-frontend/src/pages/TransferReplicationPage.test.tsx`
- `ecm-core/src/test/java/com/ecm/core/controller/TransferReplicationControllerTest.java`

What changed:

- Transfer job report rendering now validates that `entryReport` contains the expected numeric summary fields and `entries` array before rendering summary chips.
- Empty or default backend JSON objects no longer produce `Total undefined` / `Succeeded undefined` / `Failed undefined` UI.
- Controller test coverage now pins the HTTP JSON contract for `entryReport` and `reportTruncated`.

Why:

- The entity model defaults `entryReport` to an empty map, so the page needed to be defensive.
- The frontend-facing JSON contract is now explicitly covered at controller level.

### CMIS Explorer closure

Files:

- `ecm-frontend/src/pages/CmisExplorerPage.tsx`
- `ecm-frontend/src/pages/CmisExplorerPage.test.tsx`
- `ecm-frontend/src/services/cmisService.ts`
- `ecm-core/src/test/java/com/ecm/core/controller/CmisBrowserControllerTest.java`

What changed:

- CMIS query row typing was tightened from `any[]` to `Record<string, unknown>[]`.
- CMIS Explorer now uses per-surface loading flags instead of one shared loading boolean.
- CMIS Explorer now resets and reloads data when `athena:tenant-changed` is emitted.
- Query result row keys now prefer `cmis:objectId` when available.
- Smoke tests were added for:
  - repository info render
  - type browser load
  - query execution and `hasMoreItems` hint
  - tenant-change reload
  - blank-query guard
- Controller test coverage now includes `typeChildren` and stronger `query` response assertions.

Why:

- The page previously had no dedicated smoke coverage.
- Tenant changes could leave CMIS repository/type data stale.
- Shared loading state made independent page sections harder to reason about and verify.

## Verification

Frontend lint:

```bash
cd ecm-frontend
./node_modules/.bin/eslint \
  src/pages/CmisExplorerPage.tsx \
  src/pages/CmisExplorerPage.test.tsx \
  src/pages/TenantAdminPage.tsx \
  src/pages/TenantAdminPage.test.tsx \
  src/pages/TransferReplicationPage.tsx \
  src/pages/TransferReplicationPage.test.tsx \
  src/services/cmisService.ts
```

Frontend tests:

```bash
cd ecm-frontend
CI=true npm test -- --watch=false --runInBand \
  src/pages/CmisExplorerPage.test.tsx \
  src/pages/TenantAdminPage.test.tsx \
  src/pages/TransferReplicationPage.test.tsx \
  src/services/tenantService.test.ts
```

Result:

- 4 suites passed
- 18 tests passed

Backend tests:

```bash
cd ecm-core
mvn -q -Dtest=TenantMetricsServiceTest,TenantAdminControllerTest,TransferReplicationControllerTest,CmisBrowserControllerTest test
```

Result:

- focused controller/service suite passed

Build and patch hygiene:

```bash
cd ecm-frontend
npm run -s build

git diff --check
```

Result:

- frontend build passed
- `git diff --check` passed

Known pre-existing warnings still present in build:

- `ecm-frontend/src/components/share/ShareLinkManager.tsx`
- `ecm-frontend/src/pages/AdminDashboard.tsx`

## Notes

- A full browser-based authenticated acceptance run against a live Athena stack was not executed in this pass because the local Athena API/UI stack was not active on the expected endpoints; this pass therefore closed acceptance through focused frontend smoke tests plus backend controller/service verification.

## Follow-up Full-Stack Smoke Harness

File:

- `ecm-frontend/e2e/frontend-acceptance-smoke.spec.ts`

What was added:

- A lightweight authenticated Playwright smoke harness for:
  - `/admin/tenants`
  - `/admin/transfer-replication`
  - `/admin/cmis-explorer`
- The spec uses the existing `loginWithCredentialsE2E(...)` bypass-first helper and `waitForApiReady(...)`.
- Validation performed in this pass:

```bash
cd ecm-frontend
npx playwright test e2e/frontend-acceptance-smoke.spec.ts --list
```

Result:

- Playwright discovered 3 smoke tests successfully.

Environment limitation encountered:

- A true live run of the new Playwright smoke was attempted but could not be completed on this machine because the local Athena stack could not be started.
- Docker image pulls for required services failed repeatedly with Docker Hub `EOF` errors (`postgres:15-alpine`, `nginx:alpine`, `python:3.11-slim`, and related images).
- This host also had no local cache for the required Athena dependency images, so `--pull never` was not a viable fallback.

Practical consequence:

- The repository now contains the runnable smoke spec, but the live browser-backed full-stack execution is still pending a machine with:
  - a working Docker registry connection, or
  - a prewarmed local image cache, or
  - an already-running Athena stack reachable at the expected UI/API endpoints.
