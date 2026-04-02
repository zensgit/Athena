# Phase 362C: Batch Download Preflight Decision Diagnostics Verification

## Backend

- `cd ecm-core && mvn -q -Dtest=BatchDownloadServiceTest,BatchDownloadControllerTest test`

## Frontend

- `cd ecm-frontend && ./node_modules/.bin/eslint src/services/nodeService.ts src/pages/FileBrowser.tsx`
- `cd ecm-frontend && npm run -s build`

## Diff hygiene

- `git diff --check -- ecm-core/src/main/java/com/ecm/core/service/BatchDownloadService.java ecm-core/src/main/java/com/ecm/core/controller/BatchDownloadController.java ecm-core/src/test/java/com/ecm/core/service/BatchDownloadServiceTest.java ecm-core/src/test/java/com/ecm/core/controller/BatchDownloadControllerTest.java ecm-frontend/src/services/nodeService.ts ecm-frontend/src/pages/FileBrowser.tsx`

## Scope verified

- Batch-download preflight now reports `decision` and `primaryReason` in addition to `executable`.
- Service-level tests verify `PARTIAL` and `BLOCKED` classification.
- Controller tests verify the new response fields are serialized.
- Frontend typing and `FileBrowser` preflight toast logic compile against the extended response.

## Notes

- This slice does not yet add a separate archive-applicability endpoint or direct download-node resource model.
- The broader “surpass benchmark in all functional, operational, and detail surfaces” goal remains in progress.
