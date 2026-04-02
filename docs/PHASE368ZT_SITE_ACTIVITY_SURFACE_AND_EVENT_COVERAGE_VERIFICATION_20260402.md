# Phase368ZT Site Activity Surface And Event Coverage Verification

## Backend

Passed:

```bash
cd ecm-core && mvn -q -Dtest=SiteServiceTest,SiteMembershipServiceTest,SiteMemberRosterTest,ActivityEventListenerTest test
```

Coverage validated:

- site lifecycle activity emission from `SiteService`
- site membership activity emission from `SiteMembershipService`
- site-aware activity listener behavior

## Frontend

Passed:

```bash
cd ecm-frontend && ./node_modules/.bin/eslint src/pages/SitesPage.tsx src/pages/ActivityFeedPage.tsx src/services/activityService.ts src/App.tsx src/components/layout/MainLayout.tsx
cd ecm-frontend && npm run -s build
```

Coverage validated:

- `SitesPage` site detail activity panel compiles cleanly
- `ActivityFeedPage` query-param preselection compiles and lint-checks cleanly
- full frontend production build succeeds

## Diff hygiene

Passed:

```bash
git diff --check -- \
  ecm-core/src/main/java/com/ecm/core/service/ActivityEventListener.java \
  ecm-core/src/main/java/com/ecm/core/service/SiteService.java \
  ecm-core/src/main/java/com/ecm/core/service/SiteMembershipService.java \
  ecm-core/src/test/java/com/ecm/core/service/SiteServiceTest.java \
  ecm-core/src/test/java/com/ecm/core/service/SiteMembershipServiceTest.java \
  ecm-core/src/test/java/com/ecm/core/service/SiteMemberRosterTest.java \
  ecm-frontend/src/pages/SitesPage.tsx \
  ecm-frontend/src/pages/ActivityFeedPage.tsx \
  docs/PHASE368ZT_SITE_ACTIVITY_SURFACE_AND_EVENT_COVERAGE_DEV_20260402.md \
  docs/PHASE368ZT_SITE_ACTIVITY_SURFACE_AND_EVENT_COVERAGE_VERIFICATION_20260402.md
```

## Notes

- Frontend build still shows two pre-existing warnings outside this phase:
  - `src/components/share/ShareLinkManager.tsx`
  - `src/pages/AdminDashboard.tsx`
- No git commit was created in this phase.
