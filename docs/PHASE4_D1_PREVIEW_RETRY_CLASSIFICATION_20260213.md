# Phase 4 Day 1 - Preview Retry Classification Hardening (2026-02-13)

## Problem

Preview generation failures were broadly classified as "TEMPORARY" whenever the failure reason started with:

- `Error generating preview: ...`
- `CAD preview failed: ...`

This caused two practical issues:

1. **Auto-retry was too aggressive**: permanent errors (for example, malformed PDFs) were treated as retryable and re-queued until attempts were exhausted.
2. **Unsupported CAD configuration was misclassified**: when CAD preview is disabled or not configured, the system should treat it as **unsupported** (not "failed"), and the UI should not show retry actions.

## Design / Approach

1. **Classifier semantics**
   - `UNSUPPORTED`: mime types that cannot be previewed (for example `application/octet-stream`) or known "unsupported" reasons.
   - `TEMPORARY`: only when the failure reason includes transient hints (timeouts, connection resets/refusals, common 5xx codes).
   - `PERMANENT`: default for non-unsupported, non-transient failures.

2. **Queue retry policy**
   - Preview queue retries should be driven by the classifier result:
     - retry only when `PreviewFailureClassifier` returns `TEMPORARY`
     - do not retry for `UNSUPPORTED` or `PERMANENT`

3. **Search facet alignment**
   - `PreviewStatusFilterHelper` keeps a phrase list used to treat legacy/FAILED entries as UNSUPPORTED for facet correctness.
   - This list must stay aligned with `PreviewFailureClassifier.isUnsupportedReason(...)`.

## Implementation Notes

Backend changes:

- `ecm-core/src/main/java/com/ecm/core/preview/PreviewFailureClassifier.java`
  - Removed broad "startsWith(error generating preview)" from TEMPORARY classification.
  - TEMPORARY is now based on transient hints only.
  - Added CAD config failures as UNSUPPORTED reasons:
    - `CAD preview disabled`
    - `CAD preview service not configured`
- `ecm-core/src/main/java/com/ecm/core/preview/PreviewQueueService.java`
  - Queue retry decision now uses `PreviewFailureClassifier.classify(...)`.
- `ecm-core/src/main/java/com/ecm/core/search/PreviewStatusFilterHelper.java`
  - Added the same CAD phrases to `UNSUPPORTED_REASON_PHRASES`.
- Tests:
  - `ecm-core/src/test/java/com/ecm/core/preview/PreviewFailureClassifierTest.java`

## Verification

Backend:

```bash
cd ecm-core
mvn -q test
```

Frontend (E2E gate subset):

```bash
cd ecm-frontend
ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 \
  npx playwright test \
    e2e/ocr-queue-ui.spec.ts \
    e2e/pdf-preview.spec.ts \
    e2e/search-preview-status.spec.ts \
    --project=chromium
```

Expected outcome:

- Malformed/parse errors that do not include transient hints are categorized as `PERMANENT` and will not auto-retry.
- CAD preview disabled / not configured is treated as `UNSUPPORTED`.
- CI/local E2E expectations remain stable (no change to the preview-status filter semantics).

