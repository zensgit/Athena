# Phase 369BI: Tenant Edge/Protocol Scope

> **Date**: 2026-04-08

## Goal

Close the remaining tenant edge leaks in:

- people preference read endpoints that were still bypassing
  [PreferenceService.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/PreferenceService.java)
- CMIS query execution that still walked the global node result set before ACL
  filtering

This phase keeps the existing control-plane and service-layer tenant strategy.
It does not introduce schema-level `tenant_id` persistence.

## Implementation

### PeopleController preference reads

- Updated [PeopleController.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/controller/PeopleController.java)
  so both:
  - `GET /api/v1/people/{username}/preferences`
  - `GET /api/v1/people/{username}/preferences/{preferenceName}`
  now delegate to
  [PreferenceService.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/PreferenceService.java)
  for tenant-aware sanitization.
- This removes the last direct preference-map read on the single-preference
  endpoint and also aligns the unfiltered preference read with the same tenant
  filtering logic already used by filtered/export flows.

### CMIS query scoping

- Updated [CmisQueryService.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/cmis/CmisQueryService.java)
  to apply
  [TenantWorkspaceScopeService.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/TenantWorkspaceScopeService.java)
  before ACL filtering.
- Scoped tenants now drop foreign-workspace query matches even if ACL would
  otherwise allow the node.
- This keeps `cmisselector=query` consistent with the tenant-scoped behavior
  already established in browse, search, activity, and mutation paths.

## Test Surface

- [PeopleControllerTest.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/controller/PeopleControllerTest.java)
- [CmisBrowserServiceTest.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/cmis/CmisBrowserServiceTest.java)
- [CmisBrowserControllerTest.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/controller/CmisBrowserControllerTest.java)

## Scope Boundaries

- No frontend changes in this phase.
- No changes to other People preference mutation routes.
- No new CMIS selectors or AtomPub endpoints.
- No schema-level multi-tenancy rollout.
