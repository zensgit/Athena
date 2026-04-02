# Phase 367ZZAH: Queue Diagnostics Effective Summary Contract Verification

## Verification

Backend:

```bash
cd ecm-core
mvn -q -Dtest='PreviewDiagnosticsControllerSecurityTest#diagnosticsQueueSummaryAllowsAdmin+diagnosticsQueueSummaryPrefersRenditionSummary' test
```

Frontend:

```bash
cd ecm-frontend
./node_modules/.bin/eslint src/pages/PreviewDiagnosticsPage.tsx src/services/previewDiagnosticsService.ts src/utils/previewQueueBatchUtils.ts src/utils/previewQueueBatchUtils.test.ts src/utils/previewQueueDiagnosticsUtils.ts src/utils/previewQueueDiagnosticsUtils.test.ts
CI=true npm test -- --watch=false --runInBand src/utils/previewQueueBatchUtils.test.ts src/utils/previewQueueDiagnosticsUtils.test.ts
npm run -s build
```

Diff hygiene:

```bash
git diff --check -- \
  ecm-core/src/main/java/com/ecm/core/controller/PreviewDiagnosticsController.java \
  ecm-core/src/test/java/com/ecm/core/controller/PreviewDiagnosticsControllerSecurityTest.java \
  ecm-frontend/src/services/previewDiagnosticsService.ts \
  ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx \
  docs/PHASE367ZZAH_QUEUE_DIAGNOSTICS_EFFECTIVE_SUMMARY_CONTRACT_DEV_20260329.md \
  docs/PHASE367ZZAH_QUEUE_DIAGNOSTICS_EFFECTIVE_SUMMARY_CONTRACT_VERIFICATION_20260329.md
```

## Expected Assertions

- queue diagnostics summary returns effective:
  - `previewStatus`
  - `previewFailureReason`
  - `previewFailureCategory`
  - `previewLastUpdated`
- queue diagnostics CSV export includes the new effective summary columns
- frontend queue-declined main table renders effective failure detail without type drift
