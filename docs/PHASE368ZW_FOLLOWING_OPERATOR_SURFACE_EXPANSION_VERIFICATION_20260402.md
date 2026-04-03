# Phase368ZW Following Operator Surface Expansion Verification

## Checkpoint commit

Created before this phase:

```bash
git commit -m "feat(collab): checkpoint sites activity following milestone"
```

Resulting commit:

```text
2644528
```

## Focused frontend lint

Passed:

```bash
cd ecm-frontend && ./node_modules/.bin/eslint \
  src/pages/PeopleDirectoryPage.tsx \
  src/components/browser/FileList.tsx \
  src/pages/ActivityFeedPage.tsx \
  src/services/followingService.ts \
  src/utils/siteActivityUtils.ts \
  src/utils/siteActivityUtils.test.ts
```

## Focused frontend test

Passed:

```bash
cd ecm-frontend && CI=true npm test -- --watch=false --runInBand src/utils/siteActivityUtils.test.ts
```

## Frontend build

Passed:

```bash
cd ecm-frontend && npm run -s build
```

Notes:

- Build still reports two pre-existing warnings outside this phase:
  - `src/components/share/ShareLinkManager.tsx`
  - `src/pages/AdminDashboard.tsx`

## Diff hygiene

Passed:

```bash
git diff --check -- \
  ecm-frontend/src/pages/PeopleDirectoryPage.tsx \
  ecm-frontend/src/components/browser/FileList.tsx \
  ecm-frontend/src/pages/ActivityFeedPage.tsx \
  ecm-frontend/src/services/followingService.ts \
  ecm-frontend/src/utils/siteActivityUtils.ts \
  ecm-frontend/src/utils/siteActivityUtils.test.ts \
  docs/PHASE368ZW_FOLLOWING_OPERATOR_SURFACE_EXPANSION_DEV_20260402.md \
  docs/PHASE368ZW_FOLLOWING_OPERATOR_SURFACE_EXPANSION_VERIFICATION_20260402.md
```

## Coverage validated

- selected-profile user follow/unfollow action
- file-list node follow/unfollow action in list, grid, and context-menu surfaces
- activity feed target-kind filtering
- query-param continuity for `target`
