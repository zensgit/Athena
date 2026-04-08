# Phase 369BH: Tenant Content Mutation Scope Verification

> **Date**: 2026-04-08

## Verification

### Focused backend tenant scope tests

```bash
cd ecm-core
mvn -q -Dtest=BatchDownloadServiceTest,LockServiceTest,TrashServiceTest,CheckOutCheckInServiceTest,DocumentRelationAssociationTest test
```

### Diff sanity

```bash
git diff --check
```

## What to Confirm

- Batch download preflight and ZIP streaming no longer include foreign-tenant
  nodes.
- Lock APIs collapse foreign-tenant nodes to not-found semantics.
- Trash list/mutate flows only operate on tenant-visible nodes.
- Checkout/checkin flows cannot target foreign-tenant documents or destination
  folders.
- Document relation create/read/delete paths no longer leak or mutate hidden
  cross-tenant documents, and now enforce basic source/target permissions.

## Notes

- This phase verifies service-layer tenant scoping and ACL hardening only.
- It does not add `tenant_id` persistence to nodes, trash, working copies, or
  document relations.
