# Phase 367F: Document Preview Lock Info Consumption Verification

## Frontend

- `cd ecm-frontend && ./node_modules/.bin/eslint src/components/preview/DocumentPreview.tsx src/utils/lockInfoUtils.ts src/utils/lockInfoUtils.test.ts src/types/index.ts src/services/nodeService.ts`
- `cd ecm-frontend && CI=true npm test -- --watch=false --runInBand src/utils/lockInfoUtils.test.ts`
- `cd ecm-frontend && npm run -s build`

## Diff hygiene

- `git diff --check -- ecm-frontend/src/components/preview/DocumentPreview.tsx ecm-frontend/src/utils/lockInfoUtils.ts ecm-frontend/src/utils/lockInfoUtils.test.ts ecm-frontend/src/types/index.ts ecm-frontend/src/services/nodeService.ts docs/PHASE367F_DOCUMENT_PREVIEW_LOCK_INFO_CONSUMPTION_DEV_20260326.md docs/PHASE367F_DOCUMENT_PREVIEW_LOCK_INFO_CONSUMPTION_VERIFICATION_20260326.md`

## Scope verified

- Preview loads lock-info alongside node details.
- Toolbar chip reflects owner/foreign/expired lock states.
- Preview alert reflects actionable lock detail.
- Shared lock-info formatting helpers are covered by focused unit tests.

## Notes

- This slice consumes existing lock-info only; it does not yet add lock actions or a broader lock-management panel.
- The broader “surpass benchmark in all functional, operational, and detail surfaces” goal remains in progress.
