# Phase 369BD: Tenant Sites/Collaboration Scope Verification

> **Date**: 2026-04-07

## Verification

### Focused backend tenant scope tests

```bash
cd ecm-core
mvn -q -Dtest=SiteServiceTest,SiteMembershipServiceTest,SiteMemberRosterTest,DiscussionServiceTest,BlogServiceTest,CalendarServiceTest test
```

### Diff sanity

```bash
git diff --check
```

## What to Confirm

- Scoped tenants only see sites whose root folder is inside the current tenant
  workspace.
- Membership requests and site roster operations return not-found semantics for
  foreign-tenant sites.
- `getRequestsForUser(...)` and `getUserSites(...)` do not leak cross-tenant
  site references.
- Discussion topics/replies, blog posts, and calendar events resolve to
  not-found semantics when their owning site is outside the current tenant
  workspace.
- Existing non-tenant site and collaboration behavior remains intact in the
  default tenant.

## Notes

- This phase verifies service-layer tenant scoping only.
- It does not add `tenant_id` persistence to site, discussion, blog, or
  calendar tables.
