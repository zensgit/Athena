# PHASE368ZI Preview Diagnostics Local Override Queue Contract Convergence Verification

## Verification commands

```bash
cd ecm-frontend && ./node_modules/.bin/eslint src/pages/PreviewDiagnosticsPage.tsx src/utils/previewDiagnosticsPreviewQueueOverrideUtils.ts src/utils/previewDiagnosticsPreviewQueueOverrideUtils.test.ts src/utils/previewQueueOverrideUtils.ts src/utils/previewQueueOverrideUtils.test.ts
```

Result: passed with no lint errors.

```bash
cd ecm-frontend && CI=true npm test -- --watch=false --runInBand src/utils/previewDiagnosticsPreviewQueueOverrideUtils.test.ts src/utils/previewQueueOverrideUtils.test.ts
```

Result: passed.

```bash
cd ecm-frontend && npm run -s build
```

Result: passed with pre-existing warnings only:
- `src/components/share/ShareLinkManager.tsx` unused `BarChart`
- `src/pages/AdminDashboard.tsx` unused `FilterList`

```bash
git diff --check -- ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx ecm-frontend/src/utils/previewDiagnosticsPreviewQueueOverrideUtils.ts ecm-frontend/src/utils/previewDiagnosticsPreviewQueueOverrideUtils.test.ts docs/PHASE368ZI_PREVIEW_DIAGNOSTICS_LOCAL_OVERRIDE_QUEUE_CONTRACT_CONVERGENCE_DEV_20260401.md docs/PHASE368ZI_PREVIEW_DIAGNOSTICS_LOCAL_OVERRIDE_QUEUE_CONTRACT_CONVERGENCE_VERIFICATION_20260401.md
```

Result: passed.
