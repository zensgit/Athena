# Phase 369AZ: Tenant Workspace Scope Backbone

## Goal

Add a first tenant data-plane cut without touching global `tenant_id` schema isolation:

- non-default tenant requests see only their tenant root workspace at the root layer
- folder/document creation without an explicit parent lands under the tenant root workspace
- direct node/folder reads outside the current tenant workspace are hidden as not found

## Approach

- extend `TenantContext` to carry `rootNodeId`
- have `TenantFilter` populate `tenantDomain + rootNodeId`
- use `TenantContext` inside `FolderService` and `NodeService` for:
  - implicit parent resolution
  - root folder scoping
  - path-based workspace boundary checks

## Non-Goals

- no global `tenant_id` column rollout
- no search-wide tenant filtering
- no workflow/rule/activity/notification tenant isolation yet
- no workspace deprovisioning logic
