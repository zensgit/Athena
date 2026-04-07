# Phase 369BA: Tenant Search/Discovery Scope

## Goal

Close the most obvious tenant data-plane leak after workspace-root scoping:

- full-text search respects the current tenant workspace boundary
- faceted/discovery search respects the current tenant workspace boundary

## Approach

- reuse `TenantContext.rootNodeId` introduced in `369AZ`
- resolve the current tenant root workspace path from `NodeRepository`
- add path-scope filters to:
  - `FullTextSearchService`
  - `FacetedSearchService`
- reject folder-scoped search requests that point outside the active tenant workspace

## Non-Goals

- no global `tenant_id` schema rollout
- no legacy `SearchIndexService` rewrite in this phase
- no activity/notification tenant scoping yet
- no workflow/rule tenant scoping yet
- no frontend search UI changes
