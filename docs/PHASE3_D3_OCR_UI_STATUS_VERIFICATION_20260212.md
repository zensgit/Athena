# Phase 3 Day 3: OCR UI Status + Queue Actions (Verification) — 2026-02-12

## Scope

Verify the Day 3 UI surfacing:
- OCR status chip appears in the preview dialog.
- Queue OCR / Force OCR actions are available and invoke the correct endpoint.
- UI remains stable regardless of OCR being enabled (disabled state still shows a clear chip).

## Preconditions

- Docker stack is running with:
  - Frontend at `http://localhost:5500`
  - Backend at `http://localhost:7700`

If you need to rebuild/restart the stack (recommended after frontend/backend changes):

```bash
bash scripts/restart-ecm.sh
```

## Automated Verification (Playwright)

Run the targeted Playwright scenario:

```bash
cd ecm-frontend
ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 npx playwright test e2e/ocr-queue-ui.spec.ts
```

Expected:
- `1 passed`

What the test asserts:
- Opens preview for a freshly uploaded PDF.
- “More actions” menu contains:
  - `Queue OCR`
  - `Force OCR Rebuild`
- Clicking the actions triggers `POST /api/v1/documents/{id}/ocr/queue`
- OCR chip becomes visible (e.g. `OCR: Processing` or `OCR: Disabled`).

## Manual Smoke (Optional)

1. Open a document preview from Search Results.
2. Confirm the top bar shows an OCR chip when OCR metadata exists, or after clicking `Queue OCR`.
3. Open “More actions” and confirm:
   - `Queue OCR`
   - `Force OCR Rebuild`

If OCR is enabled (`ECM_OCR_ENABLED=true`), you should see:
- an “OCR extraction is in progress” banner (while processing)
- then `OCR: Ready` when done

