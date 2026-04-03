# Phase369AG Content Archive/Restore Backbone Verification

Date: 2026-04-03

## Backend

Command:

```bash
cd ecm-core && mvn -q -Dtest=DiscussionServiceTest,CalendarServiceTest,ContentArchiveServiceTest,ContentArchiveControllerTest test
```

Result:

- Passed

Coverage focus:

- discussion activity convergence still passes
- calendar delete activity convergence still passes
- archiveNode archives folder scope recursively
- restoreNode restores folder scope recursively
- restore permission guard
- archive controller contract for archive/restore/status/list

## Frontend

Command:

```bash
cd ecm-frontend && ./node_modules/.bin/eslint src/pages/ContentArchivePage.tsx src/services/contentArchiveService.ts src/App.tsx src/components/layout/MainLayout.tsx src/components/layout/MainLayout.menu.test.tsx src/utils/siteActivityUtils.ts src/utils/siteActivityUtils.test.ts
```

Result:

- Passed

Command:

```bash
cd ecm-frontend && CI=true npm test -- --watch=false --runInBand src/components/layout/MainLayout.menu.test.tsx src/utils/siteActivityUtils.test.ts src/utils/notificationUtils.test.ts
```

Result:

- Passed

Command:

```bash
cd ecm-frontend && npm run -s build
```

Result:

- Passed with 2 pre-existing warnings unrelated to this phase:
  - `src/components/share/ShareLinkManager.tsx` unused `BarChart`
  - `src/pages/AdminDashboard.tsx` unused `FilterList`

## Diff Hygiene

Command:

```bash
git diff --check
```

Result:

- Passed
