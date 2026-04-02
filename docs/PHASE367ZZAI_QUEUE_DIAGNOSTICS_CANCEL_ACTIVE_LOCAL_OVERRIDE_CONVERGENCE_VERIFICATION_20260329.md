# Phase 367ZZAI: Queue Diagnostics Cancel-Active Local Override Convergence Verification

## Verification

Frontend:

```bash
cd ecm-frontend
./node_modules/.bin/eslint src/pages/PreviewDiagnosticsPage.tsx src/utils/previewQueueDiagnosticsUtils.ts src/utils/previewQueueDiagnosticsUtils.test.ts
CI=true npm test -- --watch=false --runInBand src/utils/previewQueueDiagnosticsUtils.test.ts
npm run -s build
```

Diff hygiene:

```bash
git diff --check -- \
  ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx \
  ecm-frontend/src/utils/previewQueueDiagnosticsUtils.ts \
  ecm-frontend/src/utils/previewQueueDiagnosticsUtils.test.ts \
  docs/PHASE367ZZAI_QUEUE_DIAGNOSTICS_CANCEL_ACTIVE_LOCAL_OVERRIDE_CONVERGENCE_DEV_20260329.md \
  docs/PHASE367ZZAI_QUEUE_DIAGNOSTICS_CANCEL_ACTIVE_LOCAL_OVERRIDE_CONVERGENCE_VERIFICATION_20260329.md
```

## Expected Assertions

- cancelling filtered queue rows immediately marks matching rows `CANCEL_REQUESTED`
- `runningCount` is reduced locally
- `cancellationRequestedCount` increases locally
- unrelated rows are preserved unchanged
