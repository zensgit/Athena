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

## Day 2 (Done): MIME Type Normalization for `application/octet-stream`

Goal:

- Reduce false UNSUPPORTED previews when uploads are mislabeled as `application/octet-stream`.

Scope:

- Add server-side MIME normalization on upload (magic bytes first, conservative extension fallback for a small allowlist):
  - PDF (`%PDF-`)
  - PNG/JPEG/GIF/WebP
  - Explicitly skip UTF-8 "text/plain" sniffing for now (avoid misclassifying arbitrary binaries and keep `.bin` E2E stable).

Acceptance:

- When a PDF is uploaded with `application/octet-stream` but PDF magic bytes (or `.pdf` name), it is stored as `application/pdf` and preview succeeds.
- No regression for truly unknown binaries.

Deliverables:

- Implementation + unit tests
- Design/verification doc:
  - `docs/PHASE4_D2_MIME_NORMALIZATION_20260213.md`

Verification:

- `cd ecm-core && mvn -q test`
- `bash scripts/get-token.sh admin admin`
- `ECM_API=http://localhost:7700 ECM_TOKEN_FILE=tmp/admin.access_token ECM_UPLOAD_FILE=tmp/mime-test.bin bash scripts/smoke.sh`
- `cd ecm-frontend && ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 npx playwright test e2e/ocr-queue-ui.spec.ts e2e/pdf-preview.spec.ts e2e/search-preview-status.spec.ts --project=chromium`

## Day 3: Preview Failure Taxonomy + UX Messaging

Goal:

- Make failure category actionable:
  - TEMPORARY: retry makes sense
  - PERMANENT: retry unlikely, guide to replace/check file
  - UNSUPPORTED: no retry actions, guide to download/convert

Scope:

- Backend: expand a curated set of PERMANENT parse/corruption hints (starting with PDF/OOXML parsing).
- Frontend: tighten copy and visibility rules so Search, Advanced Search, and Preview dialog stay consistent.

Implementation sketch:

- Backend:
  - `PreviewFailureClassifier` add patterns for PERMANENT parse failures
  - keep `PreviewStatusFilterHelper` aligned for UNSUPPORTED-only phrases
- Frontend:
  - preview dialog failure banner/message: sanitized reason + suggested next action

Acceptance:

- Search/Advanced Search show consistent chips and consistent action availability.
- Preview dialog shows guidance for PERMANENT failures and hides retry/force rebuild.

Verification:

- Backend tests: classifier categories for new reason hints
- Playwright: create a malformed PDF fixture, verify:
  - PERMANENT status
  - no retry/force rebuild actions
  - guidance copy visible

## Day 4: Bulk Actions Guardrails

Goal:

- Bulk retry should only target TEMPORARY failures, not PERMANENT.

Scope:

- Backend: add server-side "retry eligible" filtering (TEMPORARY only) to avoid stale-client mistakes.
- Frontend: bulk retry/force rebuild only appears when eligible items exist and shows accurate counts.

Acceptance:

- Clicking bulk retry does not re-queue PERMANENT failures.

Verification:

- Playwright: seed dataset with TEMPORARY + PERMANENT failures; ensure bulk retry queues only TEMPORARY.

## Day 5: Observability + Diagnostics

Goal:

- Make preview queue and failure behavior observable.

Scope:

- Metrics: counters by `category` and `mimeType` (and optionally `source`).
- Logs: structured/sanitized reason + category.
- Optional: admin endpoint to sample recent failures (rate-limited).

Acceptance:

- Operators can tell if failures are dominated by UNSUPPORTED vs PERMANENT vs TEMPORARY.

## Day 6: Automation Coverage Expansion

Goal:

- Lock in the new semantics.

Scope:

- Playwright:
  - mis-labeled octet-stream PDF becomes previewable
  - PERMANENT failure hides retry/force rebuild
  - CAD disabled shows UNSUPPORTED
- Backend:
  - classifier tests for new reason hints + category rules

## Day 7: Regression Gate + Release Documentation

Goal:

- Run weekly subset gate and produce a short release note / delivery note.

Verification:

- `cd ecm-frontend && npx playwright test --workers=1 e2e/ui-smoke.spec.ts e2e/search-view.spec.ts e2e/search-preview-status.spec.ts e2e/pdf-preview.spec.ts e2e/ocr-queue-ui.spec.ts --project=chromium`
