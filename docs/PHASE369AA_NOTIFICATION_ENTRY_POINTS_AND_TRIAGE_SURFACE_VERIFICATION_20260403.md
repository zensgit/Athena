# Phase 369AA: Notification Entry Points And Triage Surface Verification

## Commands

```bash
cd ecm-frontend && ./node_modules/.bin/eslint src/components/layout/MainLayout.tsx src/components/layout/MainLayout.menu.test.tsx src/pages/NotificationsPage.tsx src/pages/ActivityFeedPage.tsx src/pages/SitesPage.tsx src/services/notificationService.ts src/utils/notificationUtils.ts src/utils/notificationUtils.test.ts
cd ecm-frontend && CI=true npm test -- --watch=false --runInBand src/components/layout/MainLayout.menu.test.tsx src/utils/notificationUtils.test.ts
cd ecm-frontend && npm run -s build
git diff --check -- ecm-frontend/src/components/layout/MainLayout.tsx ecm-frontend/src/components/layout/MainLayout.menu.test.tsx ecm-frontend/src/pages/NotificationsPage.tsx ecm-frontend/src/pages/ActivityFeedPage.tsx ecm-frontend/src/pages/SitesPage.tsx ecm-frontend/src/utils/notificationUtils.ts ecm-frontend/src/utils/notificationUtils.test.ts docs/PHASE369AA_NOTIFICATION_ENTRY_POINTS_AND_TRIAGE_SURFACE_DEV_20260403.md docs/PHASE369AA_NOTIFICATION_ENTRY_POINTS_AND_TRIAGE_SURFACE_VERIFICATION_20260403.md
```

## Result

- `eslint` passed with no errors.
- Focused layout and notification utility tests passed.
- Frontend production build passed.
- Build still reports two pre-existing warnings outside this phase:
  - `ecm-frontend/src/components/share/ShareLinkManager.tsx`
  - `ecm-frontend/src/pages/AdminDashboard.tsx`
- `git diff --check` passed for the files touched in this phase.
