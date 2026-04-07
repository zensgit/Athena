# Phase 369BC: Tenant Rule/Workflow Scope Verification

> **Date**: 2026-04-07

## Verification

### Focused backend tenant scope tests

```bash
cd ecm-core
mvn -q -Dtest=RuleEngineServiceTenantScopeTest,RuleEngineServiceFolderScopeTest,RuleEngineServiceValidationTest,RuleEngineServiceExecutionLedgerTest,WorkflowServiceTenantScopeTest test
```

### Diff sanity

```bash
git diff --check
```

## What to Confirm

- Rule creation in a scoped tenant defaults missing `scopeFolderId` to the
  tenant root workspace.
- Rule list/search/read operations hide rules outside the current tenant
  workspace.
- Rule execution ledger entries do not leak cross-tenant documents or rules.
- Workflow document approval rejects foreign-tenant nodes.
- Workflow process browser/detail only exposes processes whose business items
  resolve to tenant-visible node IDs.
- Workflow task/process mutations return not found semantics for foreign-tenant
  processes and tasks.

## Notes

- This phase verifies service-layer tenant scoping only.
- It does not attempt Flowable engine multi-tenancy, schema isolation, or
  `tenant_id` persistence rollout.
