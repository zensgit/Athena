# Phase 369BD: Tenant Sites/Collaboration Scope

> **Date**: 2026-04-07

## Goal

Extend tenant workspace scoping into the site and collaboration surfaces so a
scoped tenant cannot read or mutate sites, memberships, discussions, blog
posts, or calendar events outside its current tenant workspace.

## Implementation

### Site visibility as the boundary

- Reused [TenantWorkspaceScopeService.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/TenantWorkspaceScopeService.java)
  as the single tenant visibility boundary.
- This phase continues to scope by a site's `rootFolder.path` falling under the
  current tenant root workspace. No `tenant_id` columns were introduced.

### SiteService

- Updated [SiteService.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/SiteService.java)
  so scoped tenants:
  - only see tenant-visible sites in `listSites(...)`
  - get not-found semantics for foreign-tenant `getSite(...)`,
    `updateSite(...)`, and `deleteSite(...)`
  - cannot attach a site root folder outside the current tenant workspace

### SiteMembershipService

- Updated [SiteMembershipService.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/SiteMembershipService.java)
  so scoped tenants:
  - only query membership requests for tenant-visible sites
  - only see their own tenant-visible requests in `getRequestsForUser(...)`
  - cannot create / approve / reject / withdraw membership requests for
    foreign-tenant sites
  - only see roster memberships and `getUserSites(...)` entries for
    tenant-visible sites

### Discussion / Blog / Calendar services

- Updated [DiscussionService.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/DiscussionService.java),
  [BlogService.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/BlogService.java),
  and [CalendarService.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/CalendarService.java)
  to require a tenant-visible site before:
  - site-scoped list/create operations
  - object-by-id read/update/delete operations
  - reply/event/post actions that resolve through an existing topic/post/event
- Foreign-tenant collaboration content now resolves to not-found semantics via
  the owning site visibility check.

## Test Surface

- [SiteServiceTest.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/service/SiteServiceTest.java)
- [SiteMembershipServiceTest.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/service/SiteMembershipServiceTest.java)
- [SiteMemberRosterTest.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/service/SiteMemberRosterTest.java)
- [DiscussionServiceTest.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/service/DiscussionServiceTest.java)
- [BlogServiceTest.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/service/BlogServiceTest.java)
- [CalendarServiceTest.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/service/CalendarServiceTest.java)

## Scope Boundaries

- No frontend changes in this phase.
- No site schema changes or tenant columns added to collaboration tables.
- No controller route or DTO contract changes.
- This phase is intentionally service-layer tenant visibility enforcement over
  the existing site and collaboration model.
