# P1 PR-8 Site Membership Persistence Design

## Date
- 2026-04-14

## Status
- Implemented

## Objective
- Move site membership requests out of `User.preferences.siteMembershipRequests` into first-class persistence.
- Centralize site-request and roster mutations in `SiteMembershipService`.
- Replace admin-only controller gates with site-role-aware moderation and roster management.

## Scope
- DB-first request lifecycle for:
  - create
  - update
  - approve
  - reject
  - withdraw
  - list by site
  - list by user
  - moderation queue listing
- One-release compatibility reader for legacy JSONB request payloads.
- Site `MANAGER` parity with `ROLE_ADMIN` for request moderation and member roster changes.
- Controller delegation cleanup for `PeopleController` and `SiteController`.

## Implemented Design

### Persistence Model
- `SiteMembershipRequest` is the authoritative request store.
- `074-create-site-membership-requests.xml` is included from `db.changelog-master.xml`.
- Persistence is controlled by:
  - `ecm.site.membership.persistence.enabled`
  - `ecm.site.membership.legacy-reader.enabled`

### Service Consolidation
- `SiteMembershipService` now owns all request state transitions.
- `createRequestForUser(...)`:
  - validates requester writability
  - validates user existence
  - validates site visibility
  - rejects duplicate requests across both persistent rows and legacy JSONB compatibility data
- `updateRequestForUser(...)` and `withdrawForUser(...)`:
  - load persistent rows first
  - materialize a legacy request row on demand during the compatibility window
- `approve(...)`:
  - updates request decision metadata
  - upserts a `SiteMember` row using the requested role
- `reject(...)`:
  - stores decision metadata without creating membership

### Compatibility Window
- Read paths are DB-first.
- Legacy JSONB requests are merged only when `legacy-reader.enabled=true`.
- Mutation paths materialize a matching legacy request into `site_membership_requests` before update, reject, or withdraw.
- `PeopleController` no longer mutates `User.preferences` for site-request state.

### Authorization Model
- Request moderation and roster management now accept either:
  - `ROLE_ADMIN`
  - `SiteMemberRole.MANAGER` for the target site
- Site-role checks are implemented in `SiteMembershipService` for this increment to avoid a broader `SecurityService` constructor churn.
- `SiteController` membership endpoints no longer enforce admin-only method security.

### API Surface Changes
- `PeopleController` site membership endpoints now delegate to `SiteMembershipService`.
- `PersonSiteMembershipRequestDto` maps from `SiteMembershipService.MembershipRequestDto`.
- `SiteController` continues to expose site membership APIs, but authorization is resolved inside the service layer.

## Key Files
- `ecm-core/src/main/java/com/ecm/core/service/SiteMembershipService.java`
- `ecm-core/src/main/java/com/ecm/core/controller/PeopleController.java`
- `ecm-core/src/main/java/com/ecm/core/controller/SiteController.java`
- `ecm-core/src/main/java/com/ecm/core/entity/SiteMembershipRequest.java`
- `ecm-core/src/main/java/com/ecm/core/repository/SiteMembershipRequestRepository.java`
- `ecm-core/src/main/resources/application.yml`
- `ecm-core/src/main/resources/db/changelog/changes/074-create-site-membership-requests.xml`

## Non-Goals
- No invitation token flow.
- No email notification integration.
- No global ACL/authority rewrite outside site membership moderation paths.

## Exit Conditions
- site request writes no longer depend on direct JSONB preference mutation
- request reads no longer require full user scans in the steady state
- site managers can moderate requests and manage members without admin-only controller gates
- compatibility reader remains available for one transition release
