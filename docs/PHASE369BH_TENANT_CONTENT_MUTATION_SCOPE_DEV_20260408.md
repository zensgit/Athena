# Phase 369BH: Tenant Content Mutation Scope

> **Date**: 2026-04-08

## Goal

Extend tenant workspace scoping into the remaining high-risk content mutation
paths so a scoped tenant cannot download, lock, trash, check out, or relate
content outside the current tenant workspace.

## Implementation

### BatchDownloadService

- Updated [BatchDownloadService.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/BatchDownloadService.java)
  so scoped tenants:
  - treat foreign-tenant nodes as missing during preflight
  - skip hidden nodes during ZIP streaming
  - do not recurse into foreign-tenant children when walking folders
- This closes the previous hole where a scoped tenant could still preflight or
  package a foreign-tenant node if ACLs happened to allow it.

### LockService

- Updated [LockService.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/LockService.java)
  so lock/unlock/status calls only resolve tenant-visible live nodes.
- Hidden nodes now collapse to not-found semantics instead of exposing lock
  state across tenant boundaries.

### TrashService

- Updated [TrashService.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/TrashService.java)
  so scoped tenants:
  - can only move/restore/permanently-delete tenant-visible nodes
  - only see tenant-visible trash items, stats, and near-purge results
- This keeps recycle-bin flows aligned with tenant workspace visibility instead
  of the old global deleted-node lists.

### CheckOutCheckInService

- Updated [CheckOutCheckInService.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/CheckOutCheckInService.java)
  so scoped tenants:
  - can only checkout/checkin/cancel checkout on tenant-visible documents
  - can only create working copies into tenant-visible destination folders
  - only see tenant-visible working copy/original query results

### DocumentRelationService

- Updated [DocumentRelationService.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/DocumentRelationService.java)
  to add both tenant visibility and missing ACL checks:
  - relation create/delete now requires writable source + readable target
  - relation list/read paths require readable requested document
  - returned relations are filtered so hidden or unreadable counterparts do not
    leak across tenant or ACL boundaries
- This phase intentionally keeps the existing relation schema and API shape; it
  just makes the service behavior safe.

## Test Surface

- [BatchDownloadServiceTest.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/service/BatchDownloadServiceTest.java)
- [LockServiceTest.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/service/LockServiceTest.java)
- [TrashServiceTest.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/service/TrashServiceTest.java)
- [CheckOutCheckInServiceTest.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/service/CheckOutCheckInServiceTest.java)
- [DocumentRelationAssociationTest.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/service/DocumentRelationAssociationTest.java)

## Scope Boundaries

- No frontend changes in this phase.
- No `tenant_id` columns added to node, working copy, relation, or trash data.
- No batch-download job model changes; this phase scopes preflight/streaming
  only.
- No schema redesign for document relations; this is service-layer tenant and
  ACL hardening over the existing relation model.
