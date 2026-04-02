# Phase 362A Verification: Operator Task Ledger And Preflight

## Date
- 2026-03-21

## Commands
```bash
cd ecm-core && mvn -q -Dtest=BatchDownloadServiceTest,BatchDownloadControllerTest,AsyncTaskLifecycleServiceTest,AnalyticsControllerTest,AnalyticsControllerSecurityTest test
```

```bash
cd ecm-frontend && npx eslint src/pages/FileBrowser.tsx src/services/nodeService.ts
```

```bash
cd ecm-frontend && npm run -s build
```

```bash
git diff --check -- ecm-core/src/main/java/com/ecm/core/controller/BatchDownloadController.java \
  ecm-core/src/main/java/com/ecm/core/service/BatchDownloadService.java \
  ecm-core/src/test/java/com/ecm/core/controller/BatchDownloadControllerTest.java \
  ecm-core/src/test/java/com/ecm/core/service/BatchDownloadServiceTest.java \
  ecm-frontend/src/pages/FileBrowser.tsx \
  ecm-frontend/src/services/nodeService.ts \
  docs/PHASE362A_OPERATOR_TASK_LEDGER_AND_PREFLIGHT_DEV_20260321.md \
  docs/PHASE362A_OPERATOR_TASK_LEDGER_AND_PREFLIGHT_VERIFICATION_20260321.md
```

## Result
- Targeted backend Maven tests passed.
- ESLint passed for `FileBrowser.tsx` and `nodeService.ts`.
- Frontend production build passed.
- `git diff --check` passed for the Phase 362A slice.

## Coverage
- `BatchDownloadServiceTest`
  - verifies structured preflight aggregation across included, missing, deleted, forbidden, duplicate, and empty-folder inputs
- `BatchDownloadControllerTest`
  - verifies the new preflight endpoint response shape
  - verifies async start rejects non-executable preflight results
  - verifies the existing async lifecycle path still works with preflight-backed manifests
- `FileBrowser`
  - uses preflight before queueing async batch downloads
  - surfaces warnings for skipped nodes and blocks no-op queueing
