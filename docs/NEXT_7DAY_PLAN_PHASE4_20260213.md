# Next 7-Day Plan - Phase 4 (Preview Hardening + Reliability) - 2026-02-13

This plan focuses on preview generation reliability, retry correctness, and UI/operator ergonomics for failure handling.

## Guiding Principles

- Prefer "safe by default": avoid infinite or wasteful retries for permanent failures.
- Keep the UX consistent: Search/Advanced Search/Preview dialog should agree on status semantics.
- Verify every slice with automation (backend tests + Playwright E2E gate subset).

## Day 1 (Done): Retry Classification Hardening

Goal:

- Stop auto-retrying permanent preview failures (for example malformed PDFs).
- Treat CAD preview disabled/unconfigured as UNSUPPORTED (not FAILED).

Deliverables:

- Implementation + tests
- Design/verification doc:
  - `docs/PHASE4_D1_PREVIEW_RETRY_CLASSIFICATION_20260213.md`

Verification:

- `cd ecm-core && mvn -q test`
- `cd ecm-frontend && ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 npx playwright test e2e/ocr-queue-ui.spec.ts e2e/pdf-preview.spec.ts e2e/search-preview-status.spec.ts --project=chromium`

## Day 2: MIME Type Normalization for `application/octet-stream`

Goal:

- Reduce false UNSUPPORTED previews when uploads are mislabeled as `application/octet-stream`.

Scope:

- Add server-side MIME normalization on upload (extension and/or magic bytes for common formats):
  - PDF (`%PDF-`)
  - PNG/JPEG
  - Plain text (UTF-8 heuristic) - optional

Acceptance:

- When a PDF is uploaded with `application/octet-stream` but `.pdf` name or PDF magic bytes, it is stored as `application/pdf` and preview succeeds.
- No regression for truly unknown binaries.

Verification:

- Unit tests for MIME sniffing.
- Playwright: upload mislabeled PDF and verify preview READY.

## Day 3: Preview Failure Taxonomy + UX Messaging

Goal:

- Make failure category actionable:
  - TEMPORARY: retry makes sense
  - PERMANENT: retry unlikely, guide to replace/check file
  - UNSUPPORTED: no retry actions, guide to download/convert

Scope:

- Add/extend a small, curated set of "permanent" PDF parse error hints.
- UI copy updates for PERMANENT failures.

Acceptance:

- Search/Advanced Search show consistent chips and consistent action availability.

## Day 4: Bulk Actions Guardrails

Goal:

- Bulk retry should only target TEMPORARY failures, not PERMANENT.

Scope:

- Backend: optional endpoint or server-side filter for "retry eligible".
- Frontend: bulk "Retry failed previews" only affects TEMPORARY items.

Acceptance:

- Clicking bulk retry does not re-queue PERMANENT failures.

## Day 5: Observability + Diagnostics

Goal:

- Make preview queue and failure behavior observable.

Scope:

- Metrics: counters by category and mime type.
- Logs: structured reason + category.
- Optional: admin endpoint to sample recent failures with categories.

## Day 6: Automation Coverage Expansion

Goal:

- Lock in the new semantics.

Scope:

- Playwright:
  - mis-labeled octet-stream PDF becomes previewable
  - permanent failure does not show queued retry count
  - CAD disabled shows UNSUPPORTED
- Backend:
  - classifier tests for new error hints

## Day 7: Regression Gate + Release Documentation

Goal:

- Run weekly subset gate and produce a short release note / delivery note.

Verification:

- `cd ecm-frontend && npx playwright test --workers=1 e2e/ui-smoke.spec.ts e2e/search-view.spec.ts e2e/search-preview-status.spec.ts e2e/pdf-preview.spec.ts e2e/ocr-queue-ui.spec.ts --project=chromium`

