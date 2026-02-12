# Phase 2 Day 3 (P0) - Preview Retry Per-Item Actions (Verification)

Date: 2026-02-12

## Summary

Verified that retryable preview failures expose **two per-item actions** in both search entry points:
- `Retry preview`
- `Force rebuild preview`

And that UNSUPPORTED previews do **not** expose retry/rebuild actions.

## Environment

- UI: `http://localhost:5500`
- API: `http://localhost:7700`
- Keycloak: `http://localhost:8180`

## Automated Verification (Playwright)

Command:

```bash
cd ecm-frontend
ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 \
  npx playwright test e2e/search-preview-status.spec.ts
```

Result:
- `5 passed`

What the spec asserts:
1. A document with `previewStatus=UNSUPPORTED` shows “Preview unsupported” and has **no**:
   - `Retry preview`
   - `Force rebuild preview`
2. A retryable failure (`previewStatus=FAILED` with non-UNSUPPORTED failureCategory) shows **both** actions in:
   - `/search-results` result card
   - `/search` result row
3. Clicking `Force rebuild preview` produces a toast containing one of:
   - `Preview queued`
   - `Preview already up to date`
   - `Failed to queue preview`

## Notes

- The test seeds a retryable failure by uploading an intentionally invalid PDF. This can return HTTP 400 with extraction errors while still creating a document id. The E2E helper accepts both 2xx and 400 as long as `documentId` is returned, then continues with explicit indexing for deterministic search visibility.

