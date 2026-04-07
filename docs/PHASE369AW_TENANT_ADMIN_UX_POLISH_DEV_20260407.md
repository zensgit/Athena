# Phase 369AW: Tenant Admin UX Polish

> **Date**: 2026-04-07

## Goal

Polish the tenant control-plane UX delivered in Phase 369AV. Make the active
client tenant continuously visible across the workspace, give admins a one-click
shortcut into the tenant registry, harden the empty state, and surface a "reset
to default" affordance — all without touching the backend or any data-plane
isolation work.

## Delivered

### MainLayout — Active Tenant Badge

- [MainLayout.tsx](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/components/layout/MainLayout.tsx)
  - New `activeTenantDomain` state, seeded from `tenantService.getActiveTenantDomain()`.
  - New effect listens for `focus`, `storage`, and the custom
    `athena:tenant-changed` event so the badge stays in sync with localStorage
    even when the active tenant is changed in another tab or by
    `TenantAdminPage`.
  - Inserted a `<Chip>` between the Notifications icon and the Account menu in
    the AppBar Toolbar:
    - Always shows the active tenant domain for any logged-in user.
    - Tooltip explains what it represents and (for admins) that it is clickable.
    - Visual treatment: subtle translucent fill when on `default`, secondary
      color when a non-default tenant is active.
    - `aria-label` `Active tenant <domain>` for accessibility and tests.
    - `clickable` only when the user has `ROLE_ADMIN`; clicking navigates to
      `/admin/tenants`.

### TenantAdminPage — Polish & Empty State

- [TenantAdminPage.tsx](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/pages/TenantAdminPage.tsx)
  - New `notifyActiveTenantChanged()` helper dispatches the
    `athena:tenant-changed` CustomEvent so the badge refreshes immediately
    after `Use Tenant`, `Delete`, or `Reset to default`.
  - New `handleResetActiveTenant()` resets the client active tenant back to the
    default domain and re-loads the registry.
  - New "Reset to <default>" text button in the *Current Request Tenant* paper,
    rendered only when the client active tenant is non-default.
  - Empty state replaced with a richer `Stack`:
    - Heading: "No tenants defined".
    - Guidance copy explaining what a tenant is and why an admin would create
      one.
    - Primary CTA `Create Tenant` (re-uses `openCreateDialog`).
    - `aria-label="Create first tenant"` on the CTA so the empty state has a
      stable test selector.
  - Existing success toasts already covered create/update/delete/use/toggle —
    no duplicate logic added.

### Tests

- [MainLayout.tenant.test.tsx](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/components/layout/MainLayout.tenant.test.tsx) — 5 tests
  - Badge defaults to `default` when no active tenant is stored.
  - Badge reflects whatever value is in `localStorage[ecm_active_tenant]`.
  - Admins can click the badge and route to `/admin/tenants`.
  - Non-admins see the badge but clicking it is a no-op.
  - Badge updates live when `athena:tenant-changed` is dispatched.
- [tenantService.test.ts](/Users/huazhou/Downloads/Github/Athena/ecm-frontend/src/services/tenantService.test.ts) — 7 tests
  - Active tenant helpers: default fallback, persistence + casing
    normalization, clear behavior.
  - API wrappers: list, current, encoded update path, encoded delete path.

## Scope Boundaries

- **No backend changes.** This phase does not touch
  `TenantAdminController`, `TenantService`, `TenantFilter`, `TenantContext`, or
  any liquibase changeset.
- **No tenant_id data-plane isolation.** Repositories, search, ACL checks, and
  ops/governance code remain untouched.
- **Hot files untouched**: preview/rendition/search/ops-governance code and
  configuration not modified.
- Allowed surface: `MainLayout.tsx`, `TenantAdminPage.tsx`,
  `tenantService.ts`, plus the two new focused test files and these two docs.
