# Phase 367ZZAJ: Preview Diagnostics Queue Cancel-Active Effective Summary Contract Verification

## Verification

Backend:

```bash
cd ecm-core
mvn -q -Dtest='PreviewDiagnosticsControllerSecurityTest#diagnosticsQueueCancelActiveAllowsAdmin' test
```

Frontend:

```bash
cd ecm-frontend
./node_modules/.bin/eslint src/pages/PreviewDiagnosticsPage.tsx src/services/previewDiagnosticsService.ts src/utils/previewQueueDiagnosticsUtils.ts src/utils/previewQueueDiagnosticsUtils.test.ts
CI=true npm test -- --watch=false --runInBand src/utils/previewQueueDiagnosticsUtils.test.ts
```

Diff hygiene:

```bash
git diff --check -- \
  ecm-core/src/main/java/com/ecm/core/controller/PreviewDiagnosticsController.java \
  ecm-core/src/test/java/com/ecm/core/controller/PreviewDiagnosticsControllerSecurityTest.java \
  ecm-frontend/src/services/previewDiagnosticsService.ts \
  ecm-frontend/src/utils/previewQueueDiagnosticsUtils.ts \
  ecm-frontend/src/utils/previewQueueDiagnosticsUtils.test.ts \
  docs/PHASE367ZZAJ_PREVIEW_DIAGNOSTICS_QUEUE_CANCEL_ACTIVE_EFFECTIVE_SUMMARY_CONTRACT_DEV_20260329.md \
  docs/PHASE367ZZAJ_PREVIEW_DIAGNOSTICS_QUEUE_CANCEL_ACTIVE_EFFECTIVE_SUMMARY_CONTRACT_VERIFICATION_20260329.md
```

## Expected Assertions

- cancel-active response items include:
  - `previewStatus`
  - `previewFailureReason`
  - `previewFailureCategory`
  - `previewLastUpdated`
- local queue diagnostics override projection preserves those fields when present
