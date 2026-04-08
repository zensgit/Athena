# Phase 369BI: Tenant Edge/Protocol Scope Verification

> **Date**: 2026-04-08

## Verification

### Focused backend tests

```bash
cd ecm-core
mvn -q -Dtest=PeopleControllerTest,PeopleControllerSecurityTest,CmisBrowserServiceTest,CmisBrowserControllerTest test
```

### Diff sanity

```bash
git diff --check
```

## What to Confirm

- Unfiltered person preference reads no longer bypass tenant filtering.
- Single preference reads return tenant-sanitized values and collapse hidden
  entries to not-found semantics.
- CMIS query results no longer include foreign-tenant nodes, even when ACLs
  would otherwise allow the match.
- Existing CMIS browser query controller coverage still passes after the new
  tenant filter.

## Notes

- This phase closes edge/protocol leaks only.
- It does not add new tenant persistence columns or change CMIS API shape.
