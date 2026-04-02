# Phase367H FileList Lock Action Gating Verification

## Verified Behavior

- unlocked nodes still show the same context menu actions
- nodes locked by another user now disable:
  - `Annotate (PDF)`
  - `Edit Online`
  - `Move`
  - `Delete`
- disabled actions explain why through lock-owner-aware tooltip copy
- nodes locked by the current user are not blocked by the new helper
- `locked=true` without `lockedBy` still guards write actions conservatively

## Commands

```bash
cd ecm-frontend && ./node_modules/.bin/eslint src/components/browser/FileList.tsx src/utils/fileLockBadgeUtils.ts src/utils/fileLockBadgeUtils.test.ts
cd ecm-frontend && CI=true npm test -- --watch=false --runInBand src/utils/fileLockBadgeUtils.test.ts
cd ecm-frontend && npm run -s build
git diff --check -- ecm-frontend/src/components/browser/FileList.tsx ecm-frontend/src/utils/fileLockBadgeUtils.ts ecm-frontend/src/utils/fileLockBadgeUtils.test.ts docs/PHASE367H_FILELIST_LOCK_ACTION_GATING_DEV_20260326.md docs/PHASE367H_FILELIST_LOCK_ACTION_GATING_VERIFICATION_20260326.md
```

## Result

- ESLint passed
- Jest passed
- production build passed
- targeted `git diff --check` passed
