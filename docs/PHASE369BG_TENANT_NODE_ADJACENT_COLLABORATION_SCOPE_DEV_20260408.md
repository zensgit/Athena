# Phase 369BG: Tenant Node-Adjacent Collaboration Scope

> **Date**: 2026-04-08

## Goal

Extend tenant workspace scoping into node-adjacent collaboration surfaces so a
scoped tenant cannot comment on, tag, categorize, favorite, or follow nodes and
sites outside the current tenant workspace.

## Implementation

### FavoriteService

- Updated [FavoriteService.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/FavoriteService.java)
  so scoped tenants:
  - can only favorite tenant-visible live nodes
  - only see favorites whose backing nodes are tenant-visible
  - get not-found semantics when resolving or removing a foreign-tenant
    favorite
- Added `getFavoritesForUser(...)` so controller read paths can reuse the same
  tenant-filtered service behavior instead of bypassing it with repository
  queries.

### FollowingService

- Updated [FollowingService.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/FollowingService.java)
  so scoped tenants:
  - can only follow tenant-visible `SITE` and `NODE` targets
  - only see visible site/node subscriptions in current-user follow listings
  - do not treat hidden site/node targets as currently followed
- `USER` follows remain global in this phase; this keeps the change scoped to
  tenant workspace visibility for content-adjacent targets.

### CommentService

- Updated [CommentService.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/CommentService.java)
  so scoped tenants:
  - cannot read or mutate comments on foreign-tenant nodes
  - cannot resolve hidden comments by id
  - only see tenant-visible comments in user-comment and mention listings
- Node visibility now gates `loadActiveNode(...)`, and comment visibility is
  enforced through the owning node path.

### TagService and CategoryService

- Updated [TagService.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/TagService.java)
  and [CategoryService.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/CategoryService.java)
  so scoped tenants:
  - cannot tag/categorize foreign-tenant nodes
  - only see tenant-visible nodes in tag/category node lookups
- This phase does **not** tenant-isolate global tag or category definitions;
  it only scopes node-attached usage and lookup results.

### PeopleController read-path cleanup

- Updated [PeopleController.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/controller/PeopleController.java)
  to route favorites and recent comment activity through tenant-aware services
  instead of direct repository access.
- This prevents `people` profile reads from bypassing the new favorite/comment
  tenant visibility rules.

## Test Surface

- [FavoriteServiceTest.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/service/FavoriteServiceTest.java)
- [FollowingServiceTest.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/service/FollowingServiceTest.java)
- [CommentServiceTenantScopeTest.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/service/CommentServiceTenantScopeTest.java)
- [TagServiceTenantScopeTest.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/service/TagServiceTenantScopeTest.java)
- [CategoryServiceTenantScopeTest.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/service/CategoryServiceTenantScopeTest.java)
- Controller regression retained:
  - [PeopleControllerTest.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/controller/PeopleControllerTest.java)
  - [PeopleControllerSecurityTest.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/controller/PeopleControllerSecurityTest.java)
  - [FollowingControllerTest.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/controller/FollowingControllerTest.java)

## Scope Boundaries

- No frontend changes in this phase.
- No `tenant_id` columns added to comment, favorite, follow, tag, or category
  tables.
- No taxonomy-level tenant isolation for tag/category definitions.
- No user-follow isolation beyond site/node workspace visibility.
- This phase is intentionally service-layer tenant visibility enforcement over
  the existing collaboration-adjacent model.
