# Phase 367G: FileList Lock Badge Verification

## Frontend

- `cd ecm-frontend && ./node_modules/.bin/eslint src/components/browser/FileList.tsx src/utils/fileLockBadgeUtils.ts src/utils/fileLockBadgeUtils.test.ts`
- `cd ecm-frontend && CI=true npm test -- --watch=false --runInBand src/utils/fileLockBadgeUtils.test.ts`
- `cd ecm-frontend && npm run -s build`

## Diff hygiene

- `git diff --check -- ecm-frontend/src/components/browser/FileList.tsx ecm-frontend/src/utils/fileLockBadgeUtils.ts ecm-frontend/src/utils/fileLockBadgeUtils.test.ts docs/PHASE367G_FILELIST_LOCK_BADGE_DEV_20260326.md docs/PHASE367G_FILELIST_LOCK_BADGE_VERIFICATION_20260326.md`

## Scope verified

- File list rows show a lock badge when `node.locked` is true.
- Grid/card rows show the same lock badge behavior.
- Tooltip reflects `lockedBy` when available.
- No new API calls are introduced for browse-level lock visibility.

## Notes

- This slice does not yet gate actions like edit/delete based on lock ownership.
- The broader “surpass benchmark in all functional, operational, and detail surfaces” goal remains in progress.
