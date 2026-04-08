# Phase 369BF: Tenant Share Links / Preferences / Rating Scope Verification

> **Date**: 2026-04-07

## Verification

### Focused backend tenant scope tests

```bash
cd ecm-core
mvn -q -Dtest=ShareLinkServiceTest,ShareLinkGovernanceTest,ShareLinkEnhancementTest,PreferenceServiceTest,RatingServiceTest,RatingControllerTest test
```

### Diff sanity

```bash
git diff --check
```

## What to Confirm

- Scoped tenants cannot create or inspect share links for nodes outside the
  current tenant workspace.
- Share-link token access, admin listings, and creator listings do not leak
  foreign-tenant links.
- Preference reads filter structured site/node references that point outside
  the current tenant workspace.
- Rating and likes flows return not-found semantics for foreign-tenant nodes.

## Notes

- This phase verifies service-layer tenant visibility only.
- Preference writes are not rewritten or migrated; only read responses are
  sanitized under scoped tenant context.
