# Phase 369BG: Tenant Node-Adjacent Collaboration Scope Verification

> **Date**: 2026-04-08

## Verification

### Focused backend tenant scope tests

```bash
cd ecm-core
mvn -q -Dtest=FollowingServiceTest,FollowingControllerTest,FavoriteServiceTest,CommentServiceTenantScopeTest,TagServiceTenantScopeTest,CategoryServiceTenantScopeTest,PeopleControllerTest,PeopleControllerSecurityTest test
```

### Diff sanity

```bash
git diff --check
```

## What to Confirm

- Scoped tenants cannot favorite foreign-tenant nodes or resolve hidden
  favorites through `people` profile endpoints.
- Scoped tenants cannot follow foreign-tenant sites or nodes, and hidden
  subscriptions disappear from follow listings.
- Comment read/mutate flows return not-found semantics for foreign-tenant
  nodes/comments.
- Tag/category node lookups do not leak nodes outside the current tenant
  workspace.
- `PeopleController` no longer bypasses tenant-aware favorite/comment service
  logic through direct repository reads.

## Notes

- This phase verifies service-layer tenant scoping only.
- Global tag/category definitions remain shared.
- `USER` follow targets remain global; this phase only scopes site/node follows
  by tenant workspace visibility.
