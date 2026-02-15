# Phase 4 - Day 6: Automation Coverage Expansion (2026-02-13)

## Goal

Lock in the Phase 4 preview semantics with automation so regressions are caught early:

- Octet-stream mislabeled PDFs normalize correctly and preview renders.
- Permanent preview failures do not surface retry actions.
- CAD preview failures are classified consistently and the UI gates actions based on retryability.

## Changes

### Playwright E2E: Preview Status + CAD Coverage

Updated:

- `ecm-frontend/e2e/search-preview-status.spec.ts`

Additions / hardening:

- Permanent-failure search is now alpha-tokenized to avoid fuzzy matches with historical E2E artifacts.
- Added DWG upload helper that tolerates `400` from text extraction (Tika DWG parser) as long as a `documentId` is returned.
- Added CAD preview scenario:
  - Calls `GET /api/v1/documents/{id}/preview`
  - Asserts:
    - If CAD is disabled/unconfigured -> `UNSUPPORTED` and no retry actions
    - If CAD renderer is configured but unhealthy -> `FAILED` and action gating matches `TEMPORARY` vs `PERMANENT`

## Verification

```bash
cd ecm-frontend
npx playwright test \
  e2e/ocr-queue-ui.spec.ts \
  e2e/pdf-preview.spec.ts \
  e2e/search-preview-status.spec.ts \
  --project=chromium
```

Result: PASS (12 passed) (2026-02-13)

