# Phase 369BA: Tenant Search/Discovery Scope Verification

## Backend

Executed:

```bash
cd ecm-core && mvn -q -Dtest=SearchAclFilteringTest,TenantFilterTest test
```

Verified:

- full-text search adds tenant workspace path filters
- faceted/discovery search adds tenant workspace path filters
- folder-scoped search requests outside the active tenant workspace collapse to empty results
- tenant filter context remains intact

## Diff Hygiene

Executed:

```bash
git diff --check
```
