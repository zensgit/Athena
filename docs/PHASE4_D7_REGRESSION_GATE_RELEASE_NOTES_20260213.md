# Phase 4 - Day 7: Regression Gate + Release Notes (2026-02-13)

## What Shipped (Phase 4 Summary)

Phase 4 focused on preview reliability, actionable failure semantics, and operator ergonomics.

- Day 1: Retry classification hardening (avoid wasteful retries for permanent failures; CAD disabled/unconfigured -> UNSUPPORTED)
  - `docs/PHASE4_D1_PREVIEW_RETRY_CLASSIFICATION_20260213.md`
- Day 2: MIME type normalization for `application/octet-stream` (content sniff + filename fallback)
  - `docs/PHASE4_D2_MIME_TYPE_NORMALIZATION_20260213.md`
- Day 3: Failure taxonomy + UX messaging consistency (Search / Advanced Search / Preview dialog)
  - `docs/PHASE4_D3_PREVIEW_FAILURE_TAXONOMY_UX_20260213.md`
- Day 4: Backend guardrails for preview queue + message field parity
  - `docs/PHASE4_D4_BACKEND_PREVIEW_QUEUE_GUARDRAILS_20260213.md`
- Day 5: Observability + admin diagnostics endpoint
  - `docs/PHASE4_D5_OBSERVABILITY_DIAGNOSTICS_20260213.md`
- Day 6: Automation coverage expansion (lock semantics with Playwright)
  - `docs/PHASE4_D6_AUTOMATION_COVERAGE_EXPANSION_20260213.md`

## Regression Gate

Run the weekly subset gate (serial, chromium):

```bash
cd ecm-frontend
npx playwright test --workers=1 \
  e2e/ui-smoke.spec.ts \
  e2e/search-view.spec.ts \
  e2e/search-preview-status.spec.ts \
  e2e/pdf-preview.spec.ts \
  e2e/ocr-queue-ui.spec.ts \
  --project=chromium
```

Result: PASS (24 passed) (2026-02-13)

## Notes

- The gate targets the Docker-served UI at `http://localhost:5500` (auto-detected by the E2E helpers when `ECM_UI_URL` is not set).
- ClamAV may report unhealthy/unavailable in local dev; the antivirus E2E case tolerates that startup behavior and still validates the rejection flow when available.

