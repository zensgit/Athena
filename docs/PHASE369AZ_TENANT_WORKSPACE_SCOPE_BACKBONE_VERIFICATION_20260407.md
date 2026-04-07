# Phase 369AZ: Tenant Workspace Scope Backbone Verification

## Backend

Executed:

```bash
cd ecm-core && mvn -q -Dtest=TenantServiceTest,TenantFilterTest,FolderServiceTenantWorkspaceScopeTest,NodeServiceTenantWorkspaceScopeTest,FolderServiceContentsAclTest,NodeServiceChildrenAclTest test
```

Verified:

- tenant filter carries root workspace context
- non-default tenant root listing collapses to the tenant workspace root
- folder creation without explicit parent lands under tenant root workspace
- document creation without explicit parent lands under tenant root workspace
- direct reads to nodes outside the current tenant workspace are hidden as not found
- existing ACL-focused folder/node tests remain green

## Frontend

Executed:

```bash
cd ecm-frontend && npm run -s build
```

Verified:

- no frontend code changes required for this phase
- existing tenant admin / browse routing remains buildable

## Diff Hygiene

Executed:

```bash
git diff --check
```
