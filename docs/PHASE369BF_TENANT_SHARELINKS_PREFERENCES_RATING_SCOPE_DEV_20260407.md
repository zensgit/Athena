# Phase 369BF: Tenant Share Links / Preferences / Rating Scope

> **Date**: 2026-04-07

## Goal

Extend tenant workspace scoping into share links, preference reads, and
ratings so scoped tenants cannot read or mutate node-adjacent surfaces outside
the current tenant workspace.

## Implementation

### ShareLinkService

- Updated [ShareLinkService.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/ShareLinkService.java)
  to require tenant-visible nodes before:
  - creating share links
  - listing share links for a node
  - resolving share links by token
  - accessing, deactivating, deleting, and updating links
- `getMyShareLinks()` and admin `listAllShareLinks()` now filter out links whose
  backing nodes are outside the current tenant workspace.
- Foreign-tenant nodes and tokens now resolve as not found rather than leaking
  link existence.

### PreferenceService

- Updated [PreferenceService.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/PreferenceService.java)
  so read/export/list flows sanitize structured preference payloads when a
  scoped tenant workspace is active.
- Nested maps/lists that reference foreign-tenant `siteId` or node-style ids
  such as `nodeId`, `folderId`, `rootFolderId`, `rootNodeId`, `targetFolderId`,
  or `workspaceId` are filtered out of read responses.
- Write semantics are unchanged in this phase; tenant filtering is enforced on
  returned data rather than by mutating stored JSON.

### RatingService

- Updated [RatingService.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/RatingService.java)
  so all rating reads and writes first require a tenant-visible `LIVE` node.
- This covers:
  - `rate(...)`
  - `removeRating(...)`
  - `getRatings(...)`
  - `getUserRating(...)`
  - `getSummary(...)`
- Foreign-tenant nodes now return not found semantics for likes and ratings.

## Test Surface

- [ShareLinkServiceTest.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/service/ShareLinkServiceTest.java)
- [ShareLinkGovernanceTest.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/service/ShareLinkGovernanceTest.java)
- [ShareLinkEnhancementTest.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/service/ShareLinkEnhancementTest.java)
- [PreferenceServiceTest.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/service/PreferenceServiceTest.java)
- [RatingServiceTest.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/service/RatingServiceTest.java)
- Existing controller regression retained:
  - [RatingControllerTest.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/controller/RatingControllerTest.java)

## Scope Boundaries

- No frontend behavior changes in this phase.
- No `tenant_id` columns added to share links, preferences, or ratings.
- Preference filtering is intentionally read-side only for now.
- Public share-link access still works only when the underlying node remains
  tenant-visible under the current scoped workspace semantics.
