# Phase 367ZZD: Document Preview Effective Failure Consumption Verification

## Verified
- Zero-source preview-read responses now return `UNSUPPORTED` instead of generic `FAILED`.
- Zero-source responses keep `retryNeeded=false`.
- Stale-hash preview-read responses now carry `failureCategory=TEMPORARY`.
- `PreviewFailureClassifier` now classifies `SOURCE_EMPTY` as `UNSUPPORTED` and `STALE_HASH_MISMATCH` as `TEMPORARY`.
- `DocumentPreview` consumes preview-endpoint `failureCategory` as part of its effective status resolution path.

## Commands
```bash
cd ecm-core && mvn -q -Dtest=PreviewFailureClassifierTest,DocumentControllerPreviewRepairTest,NodeControllerPreviewSemanticsTest,PreviewStatusFilterHelperTest,NodeDocumentPreviewProjectionTest,SearchControllerTest test
cd ecm-frontend && ./node_modules/.bin/eslint src/components/preview/DocumentPreview.tsx
cd ecm-frontend && npm run -s build
```

## Notes
- This slice intentionally does not alter preview diagnostics or ops recovery raw status surfaces.
- The goal is operator-facing convergence for ordinary preview consumption, not removal of low-level diagnostic detail.
