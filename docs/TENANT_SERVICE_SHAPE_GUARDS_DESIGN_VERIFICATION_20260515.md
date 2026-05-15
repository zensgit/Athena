# Tenant Service Shape Guards: Design and Verification

Date: 2026-05-15

## Scope

This slice hardens `ecm-frontend/src/services/tenantService.ts` against
malformed runtime responses while preserving its public API, tenant-context
localStorage helpers, endpoint paths, and request payloads.

No backend code or route contract was changed.

## Backend Contract

Backend controller:

- `TenantAdminController`

Frontend relative paths remain unchanged:

- `GET /admin/tenants`
- `GET /admin/tenants/current`
- `GET /admin/tenants/{tenantDomain}`
- `GET /admin/tenants/{tenantDomain}/metrics`
- `POST /admin/tenants`
- `PUT /admin/tenants/{tenantDomain}`
- `DELETE /admin/tenants/{tenantDomain}`

Backend records checked:

- `TenantService.TenantDto`
- `TenantMetricsService.TenantMetrics`

## Design

Added `TENANT_UNEXPECTED_RESPONSE_MESSAGE` and runtime guards for tenant
readbacks:

- Tenant DTOs require string `id`, `tenantDomain`, `tenantName`,
  boolean `enabled`, boolean `systemDefault`, string `createdDate`, nullable
  `rootNodeId`, nullable `quotaBytes`, and nullable `lastModifiedDate`.
- Tenant metric DTOs require string identity fields, boolean `enabled`, numeric
  storage and count fields, and nullable quota/available-byte fields.
- Delete remains unguarded because the backend returns `204 No Content`.
- Local tenant-context helper methods remain unchanged and still delegate to
  `utils/tenantContext`.

All guarded API reads now use `unknown` and assert the runtime shape before
returning typed values.

## Verification

Targeted Jest:

```bash
cd ecm-frontend
npm test -- --runTestsByPath src/services/tenantService.test.ts --watchAll=false
```

Result: PASS. 1 test suite, 13 tests, 0 failures.

Covered cases:

- Active-tenant localStorage helper behavior.
- List/current/get/create/update/delete/metrics endpoint forwarding.
- Tenant-domain path encoding.
- Nullable tenant fields accepted.
- HTML fallback rejected.
- Malformed tenant DTO rejected.
- Malformed metrics DTO rejected.

## Commit

Pending integration commit at the time this document was written.

## Notes

This slice is intentionally frontend-only. It composes with the parallel
`dispositionScheduleService` guard slice before combined lint/build/CI
verification.
