# Phase 369BE: Tenant Bulk Import/Archive Scope

> **Date**: 2026-04-07

## Goal

Extend tenant workspace scoping into bulk import and archive surfaces so a
scoped tenant cannot start, inspect, or mutate import jobs, archived content,
or archive policies outside its current tenant workspace.

## Implementation

### Shared tenant path visibility

- Expanded [TenantWorkspaceScopeService.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/TenantWorkspaceScopeService.java)
  with public `isPathVisible(...)` helpers.
- This keeps archive visibility on the same tenant root path boundary used by
  workspace, search, activity, and site scoping.

### BulkImportService

- Updated [BulkImportService.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/BulkImportService.java)
  so scoped tenants:
  - cannot target folders outside the current tenant workspace
  - default `targetFolderId` to the current tenant root workspace when omitted
  - only see import jobs whose `targetFolderId` is tenant-visible
  - get not-found semantics for foreign-tenant jobs in `getJob(...)` and
    `cancelImport(...)`

### ContentArchiveService

- Updated [ContentArchiveService.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/ContentArchiveService.java)
  so scoped tenants:
  - cannot archive / restore / inspect archive status for nodes outside the
    current tenant workspace
  - only see archived nodes whose stored paths fall under the current tenant
    root workspace
- Archive visibility uses stored node paths so archived nodes remain tenant-
  scannable even though they are no longer `LIVE`.

### ArchivePolicyService

- Updated [ArchivePolicyService.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/ArchivePolicyService.java)
  so scoped tenants:
  - cannot read/update/delete/dry-run/execute policies for folders outside the
    current tenant workspace
  - only see visible policies in `listPolicies()`
  - only run scheduled policies for tenant-visible folders

## Test Surface

- [BulkImportServiceTest.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/service/BulkImportServiceTest.java)
- [ContentArchiveServiceTest.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/service/ContentArchiveServiceTest.java)
- [ArchivePolicyServiceTest.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/service/ArchivePolicyServiceTest.java)
- Existing controller regression retained:
  - [BulkImportControllerTest.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/controller/BulkImportControllerTest.java)
  - [ContentArchiveControllerTest.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/controller/ContentArchiveControllerTest.java)

## Scope Boundaries

- No frontend changes in this phase.
- No `tenant_id` columns added to import job, node, or archive policy tables.
- No archive cold-storage backend integration changes.
- This phase is intentionally service-layer tenant visibility enforcement over
  the existing import/archive model.
