# Phase 369AY: Tenant Root Workspace Bootstrap

## Goal

Turn `Tenant.rootNodeId` from a passive control-plane field into a managed workspace bootstrap:

- create tenant -> auto-provision tenant root workspace
- tenant admin UI -> show and open workspace
- add guardrails around disable/delete flows that would strand tenant workspace state

## Backend

- `TenantService`
  - injects `FolderService`
  - auto-creates a root workspace folder on tenant creation
  - bootstraps a missing root workspace on tenant update for legacy tenants
  - prevents changing `rootNodeId` through admin mutation payloads
  - rejects disabling the current request tenant
  - rejects deleting tenants that still have a provisioned root workspace

## Frontend

- `TenantAdminPage`
  - removes manual `rootNodeId` authoring from the dialog
  - explains automatic workspace provisioning
  - shows root workspace state on each tenant card
  - adds `Open Workspace` action when `rootNodeId` exists

## Notes

- This phase does not implement tenant data-plane isolation.
- This phase does not deprovision tenant workspaces on delete; it blocks deletion instead.
