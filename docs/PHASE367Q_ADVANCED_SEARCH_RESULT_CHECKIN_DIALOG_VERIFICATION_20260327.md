# Phase367Q Verification: Advanced Search Result Check-In Dialog

## Verified

- Search result cards now expose `Check In` for checked-out documents.
- Non-owner non-admin callers get an explicit disabled reason instead of a failing mutation.
- Operators can:
  - release checkout without a new version file
  - upload a new version file during check-in
  - mark the uploaded version as major
  - keep the document checked out when a new file is uploaded
- Result refresh after check-in keeps checkout chips and result actions aligned with server state.

## Commands

```bash
cd ecm-frontend && ./node_modules/.bin/eslint src/pages/AdvancedSearchPage.tsx src/utils/advancedSearchActionUtils.ts src/utils/advancedSearchActionUtils.test.ts
cd ecm-frontend && CI=true npm test -- --watch=false --runInBand src/utils/advancedSearchActionUtils.test.ts src/utils/advancedSearchCheckoutUtils.test.ts src/utils/advancedSearchLockUtils.test.ts
cd ecm-frontend && npm run -s build
git diff --check -- ecm-frontend/src/pages/AdvancedSearchPage.tsx ecm-frontend/src/utils/advancedSearchActionUtils.ts ecm-frontend/src/utils/advancedSearchActionUtils.test.ts docs/PHASE367Q_ADVANCED_SEARCH_RESULT_CHECKIN_DIALOG_DEV_20260327.md docs/PHASE367Q_ADVANCED_SEARCH_RESULT_CHECKIN_DIALOG_VERIFICATION_20260327.md
```

## Notes

- This phase does not yet add a shared check-in dialog component between browse and search.
- This phase still does not implement working-copy/source relationships.
- This phase still does not add inline search-result `Check In` outside the result-card action row.
