# Phase367P Verification: FileList Check-In Dialog

## Verified

- `FileList` now exposes `Check In` for checked-out documents.
- Browse operators can:
  - release checkout without uploading a new version
  - upload a new file during check-in
  - mark the uploaded version as major
  - keep the document checked out when a new file is uploaded
- Non-owner non-admin users get an explicit disabled reason instead of a failing action.
- Folder refresh after successful check-in keeps browse badges and action gating aligned with server state.

## Commands

```bash
cd ecm-frontend && ./node_modules/.bin/eslint src/components/browser/FileList.tsx src/services/nodeService.ts src/utils/fileCheckoutBadgeUtils.ts src/utils/fileCheckoutBadgeUtils.test.ts
cd ecm-frontend && CI=true npm test -- --watch=false --runInBand src/utils/fileCheckoutBadgeUtils.test.ts
cd ecm-frontend && npm run -s build
git diff --check -- ecm-frontend/src/components/browser/FileList.tsx ecm-frontend/src/services/nodeService.ts ecm-frontend/src/utils/fileCheckoutBadgeUtils.ts ecm-frontend/src/utils/fileCheckoutBadgeUtils.test.ts docs/PHASE367P_FILELIST_CHECKIN_DIALOG_DEV_20260327.md docs/PHASE367P_FILELIST_CHECKIN_DIALOG_VERIFICATION_20260327.md
```

## Notes

- This phase does not yet add browse-layer inline `Check In` controls outside the context menu.
- This phase does not yet add search-result `Check In` upload flow.
- This phase still does not implement working-copy/source relationships.
