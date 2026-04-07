# Phase 369BE: Tenant Bulk Import/Archive Scope Verification

> **Date**: 2026-04-07

## Verification

### Focused backend tenant scope tests

```bash
cd ecm-core
mvn -q -Dtest=BulkImportServiceTest,ContentArchiveServiceTest,ArchivePolicyServiceTest,BulkImportControllerTest,ContentArchiveControllerTest test
```

### Diff sanity

```bash
git diff --check
```

## What to Confirm

- Scoped tenants cannot start bulk imports into folders outside the current
  tenant workspace.
- Scoped tenants only see import jobs whose target folders belong to the
  current tenant workspace.
- Archive archive/restore/status operations return not-found semantics for
  foreign-tenant nodes.
- Archived node listings do not leak paths from other tenants.
- Archive policy read/list/execute flows only operate on tenant-visible folders.

## Notes

- This phase verifies service-layer tenant scoping only.
- It does not add `tenant_id` persistence to import jobs, nodes, or archive
  policy tables.
