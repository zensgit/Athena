# Phase 369AV: Multi-Tenancy Control Plane Backbone

## Goal

Introduce the first real multi-tenancy control plane without attempting full data-plane isolation in one step.

## Delivered

- Added tenant registry domain:
  - [Tenant.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/entity/Tenant.java)
  - [TenantRepository.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/repository/TenantRepository.java)
  - [TenantService.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/TenantService.java)
- Added request-scoped tenant context:
  - [TenantContext.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/config/TenantContext.java)
  - [TenantFilter.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/config/TenantFilter.java)
  - [TenantFilterConfig.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/config/TenantFilterConfig.java)
- Added admin operator API:
  - [TenantAdminController.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/controller/TenantAdminController.java)
  - `GET/POST/PUT/DELETE /api/v1/admin/tenants`
  - `GET /api/v1/admin/tenants/current`
- Added Liquibase tenant registry table and seeded a system default tenant:
  - [059-create-tenants-table.xml](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/resources/db/changelog/changes/059-create-tenants-table.xml)
- Added frontend control-plane surface:
  - [tenantContext.ts](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/utils/tenantContext.ts)
  - [tenantService.ts](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/services/tenantService.ts)
  - [TenantAdminPage.tsx](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/pages/TenantAdminPage.tsx)
  - route `/admin/tenants`
  - admin menu entry `Tenants`
- API client now sends `X-Tenant-ID` using the browser-local active tenant selection.
- This phase intentionally establishes the control plane before broad `tenant_id` data-plane isolation.

## Scope Boundaries

- This phase does **not** add `tenant_id` columns to all existing business tables.
- This phase does **not** enforce tenant isolation in repositories/search/ACLs yet.
- It establishes the control plane and request context needed for later isolation phases.
