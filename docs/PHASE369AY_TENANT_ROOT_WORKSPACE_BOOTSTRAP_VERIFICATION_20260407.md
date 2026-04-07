# Phase 369AY: Tenant Root Workspace Bootstrap Verification

## Backend

Executed:

```bash
cd ecm-core && mvn -q -Dtest=TenantServiceTest,TenantAdminControllerTest,TenantFilterTest test
```

Verified:

- tenant creation provisions a root workspace
- legacy tenant update bootstraps a missing root workspace
- current request tenant cannot be disabled
- tenant with provisioned workspace cannot be deleted
- tenant admin controller contract remains green
- tenant filter contract remains green

## Frontend

Executed:

```bash
cd ecm-frontend && ./node_modules/.bin/eslint src/pages/TenantAdminPage.tsx src/services/tenantService.ts
cd ecm-frontend && CI=true npm test -- --watch=false --runInBand src/services/tenantService.test.ts src/components/layout/MainLayout.tenant.test.tsx
cd ecm-frontend && npm run -s build
```

Verified:

- tenant admin page builds without manual `rootNodeId` editing
- tenant cards show workspace state and expose `Open Workspace` when available
- existing tenant active-badge tests remain green
- existing tenant service tests remain green

## Diff Hygiene

Executed:

```bash
git diff --check
```
