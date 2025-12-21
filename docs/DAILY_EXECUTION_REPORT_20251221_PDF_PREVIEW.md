# Daily Execution Report - PDF Preview Fix (2025-12-21)

## Goal
Resolve "Failed to load PDF" in the preview dialog and re-validate the PDF preview E2E test.

## Findings
- PDF content API was returning valid PDF (200) when using `unified-portal` token.
- The PDF.js worker bundled by CRA was emitted with absolute ESM imports like `/app/node_modules/@babel/runtime/...`, which 404 in the browser.
- Console showed `Warning: Setting up fake worker` plus repeated 404s, causing PDF.js to fail and surface "Failed to load PDF".

## Changes
- Serve a static, self-contained worker from `public/pdf.worker.min.mjs` (copied from `pdfjs-dist`).
- Point `pdfjs.GlobalWorkerOptions.workerSrc` to `/pdf.worker.min.mjs` to bypass webpack/Babel transforms.
- Add an Nginx location for `.mjs` so the worker is served with the correct MIME type and SPA fallback is bypassed.

## Files Touched
- `ecm-frontend/src/components/preview/PdfPreview.tsx`
- `ecm-frontend/public/pdf.worker.min.mjs`
- `ecm-frontend/nginx.conf`
- `ecm-frontend/e2e/pdf-preview.spec.ts`

## Validation
- Worker URL responds 200 with `Content-Type: application/javascript`.
- PDF content endpoint returns a valid PDF payload (verified by magic header and pdfjs parsing).
- Playwright E2E:
  - `npx playwright test e2e/pdf-preview.spec.ts --reporter=line --timeout=240000` ✅

## Notes
- Using `client_id=ecm-api` for password grants yields tokens without realm roles; permission checks fail (403). Use `unified-portal` for admin API tests.
