# Preview/WOPI Verification Report (2025-12-23)

## Scope
- Frontend PDF preview reliability
- WOPI menu gating for office documents

## Changes
- Verified `ecm-frontend/public/pdf.worker.min.mjs` (pdfjs-dist 5.4.296) is available for client PDF rendering.
- Limited WOPI "Edit/View Online" menus to office document types only.
- Document preview menu now respects user write roles (Edit vs View).

## Test Command
```
cd ecm-frontend
npx playwright test e2e/pdf-preview.spec.ts
```

## Result
- Status: PASS
- Tests: 3 passed
- Duration: ~13s

## Notes
- Playwright warning about NO_COLOR/ FORCE_COLOR is benign.

## MCP UI Verification
- Rebuilt/restarted frontend container: `docker compose up -d --build ecm-frontend`.
- PDF context menu: shows View/Download without Edit Online.
- Office context menu (mcp-wopi-test.docx): shows Edit Online.
- PDF View action opens preview dialog and renders content.
