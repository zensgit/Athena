# Phase 369AV: Multi-Tenancy Control Plane Backbone Verification

## Verification

Run:

```bash
cd ecm-core && mvn -q -Dtest=TenantServiceTest,TenantAdminControllerTest,TenantFilterTest test
cd ecm-frontend && ./node_modules/.bin/eslint src/pages/TenantAdminPage.tsx src/services/tenantService.ts src/utils/tenantContext.ts src/services/api.ts src/App.tsx src/components/layout/MainLayout.tsx src/components/layout/MainLayout.menu.test.tsx
cd ecm-frontend && CI=true npm test -- --watch=false --runInBand src/components/layout/MainLayout.menu.test.tsx
cd ecm-frontend && npm run -s build
git diff --check
```

## Expected Outcome

- Admin tenant registry endpoints return stable CRUD/current-tenant contracts.
- Request filter resolves the default tenant when no header is supplied and rejects unknown/disabled tenants.
- The default seeded tenant keeps the system operational without mandatory tenant bootstrapping work.
- Admin UI exposes tenant registry CRUD and active-tenant switching.
