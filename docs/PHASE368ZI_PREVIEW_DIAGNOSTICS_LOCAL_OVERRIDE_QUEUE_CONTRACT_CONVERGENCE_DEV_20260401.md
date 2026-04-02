# PHASE368ZI Preview Diagnostics Local Override Queue Contract Convergence

## Goal
Keep the single-item preview queue override path in `PreviewDiagnosticsPage` aligned with the shared queue mutation contract.

## Problem
`PreviewQueueLocalOverride` previously stored only:
- `previewStatus`
- `previewFailureReason`
- `previewFailureCategory`
- `previewLastUpdated`

That meant the local optimistic override for `queuePreview(...)` could drop:
- `queueState`
- `attempts`
- `nextAttemptAt`
- `message`

## Design
- Reuse the existing shared helper `buildPreviewQueueOverride(...)` from `previewQueueOverrideUtils`.
- Replace the local four-field inline override assignment in `handleQueuePreview(...)` with the shared helper output.
- Add a small utility helper to apply local preview queue overrides to failure samples so the page can keep the full queue mutation contract in one place.
- Keep the page scope limited to `PreviewDiagnosticsPage.tsx` plus tiny utils/tests.

## Files
- `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`
- `ecm-frontend/src/utils/previewDiagnosticsPreviewQueueOverrideUtils.ts`
- `ecm-frontend/src/utils/previewDiagnosticsPreviewQueueOverrideUtils.test.ts`

## Notes
- No backend files were modified.
- No other preview/search/recovery surfaces were changed.
