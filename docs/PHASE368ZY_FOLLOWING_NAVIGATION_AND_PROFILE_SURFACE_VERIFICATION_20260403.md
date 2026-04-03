# Phase 368ZY: Following Navigation And Profile Surface Verification

## Commands

```bash
cd ecm-frontend && ./node_modules/.bin/eslint src/pages/PeopleDirectoryPage.tsx src/pages/SitesPage.tsx src/components/layout/MainLayout.tsx src/components/layout/MainLayout.menu.test.tsx
cd ecm-frontend && CI=true npm test -- --watch=false --runInBand src/components/layout/MainLayout.menu.test.tsx
cd ecm-frontend && npm run -s build
git diff --check -- ecm-frontend/src/pages/PeopleDirectoryPage.tsx ecm-frontend/src/pages/SitesPage.tsx ecm-frontend/src/components/layout/MainLayout.tsx ecm-frontend/src/components/layout/MainLayout.menu.test.tsx docs/PHASE368ZY_FOLLOWING_NAVIGATION_AND_PROFILE_SURFACE_DEV_20260403.md docs/PHASE368ZY_FOLLOWING_NAVIGATION_AND_PROFILE_SURFACE_VERIFICATION_20260403.md
```

## Result

- `eslint` passed with no errors.
- Targeted `MainLayout.menu` test passed.
- Frontend production build passed.
- Build still reports two pre-existing warnings outside this phase:
  - `ecm-frontend/src/components/share/ShareLinkManager.tsx`
  - `ecm-frontend/src/pages/AdminDashboard.tsx`
- `git diff --check` passed for the files touched in this phase.
