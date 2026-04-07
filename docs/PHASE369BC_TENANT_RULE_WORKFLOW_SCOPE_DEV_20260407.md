# Phase 369BC: Tenant Rule/Workflow Scope

> **Date**: 2026-04-07

## Goal

Extend tenant workspace scoping into the rule and workflow surfaces so a scoped
tenant cannot read or mutate rules, process instances, tasks, or workflow
document launches outside its current tenant workspace.

## Implementation

### Shared tenant visibility helper

- Expanded [TenantWorkspaceScopeService.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/TenantWorkspaceScopeService.java)
  with:
  - current tenant root node resolution
  - convenience `isNodeVisible(...)` / `isSiteVisible(...)` overloads
- Kept node/site path visibility as the single source of truth for service-layer
  tenant checks.

### RuleEngineService

- Updated [RuleEngineService.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/RuleEngineService.java)
  so scoped tenants:
  - default new rules without an explicit `scopeFolderId` to the current tenant
    root workspace
  - cannot read/update/delete/enable rules whose `scopeFolderId` falls outside
    the current tenant workspace
  - only see tenant-visible rules in:
    - `getAllRules(...)`
    - `getRulesByOwner(...)`
    - `searchRules(...)`
    - scoped folder reorder / dry-run
  - only see rule execution ledger entries whose rule and document stay inside
    the current tenant workspace

### WorkflowService

- Updated [WorkflowService.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/WorkflowService.java)
  so scoped tenants:
  - cannot start document approval for a node outside the current tenant
    workspace
  - cannot start generic processes whose attached items / business items resolve
    outside the current tenant workspace
  - only see workflow processes and tasks when their business key / process
    variables resolve to tenant-visible node IDs
  - get `ResourceNotFoundException` semantics for out-of-scope process/task
    detail and mutating operations

## Test Surface

- [RuleEngineServiceTenantScopeTest.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/service/RuleEngineServiceTenantScopeTest.java)
- [WorkflowServiceTenantScopeTest.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/service/WorkflowServiceTenantScopeTest.java)
- Existing rule regression updated:
  - [RuleEngineServiceFolderScopeTest.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/service/RuleEngineServiceFolderScopeTest.java)
  - [RuleEngineServiceValidationTest.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/service/RuleEngineServiceValidationTest.java)
  - [RuleEngineServiceExecutionLedgerTest.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/test/java/com/ecm/core/service/RuleEngineServiceExecutionLedgerTest.java)

## Scope Boundaries

- No frontend changes in this phase.
- No Flowable schema changes or tenant columns added to workflow tables.
- No query pushdown / tenant_id rollout across rule or workflow persistence.
- This phase is intentionally service-layer visibility enforcement over the
  existing rule/workflow data model.
