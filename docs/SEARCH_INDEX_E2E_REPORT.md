# Search Index Status + E2E Verification Report

## Scope
- New search index status endpoint used by Playwright e2e tests.
- Search results view + PDF preview flows against UI on port 5500.

## Backend Changes
- Added search index presence check: `SearchIndexService.isDocumentIndexed`.
- Added API endpoint: `GET /api/v1/search/index/{documentId}/status`.

## Test Run
```bash
cd ecm-frontend
ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 \
  npx playwright test e2e/search-view.spec.ts e2e/pdf-preview.spec.ts
```

## Results
- 4 tests passed (2.2m):
  - Search results view opens preview for documents
  - PDF preview shows dialog and controls
  - PDF preview falls back to server render when client PDF fails
  - File browser view action opens preview

## Notes
- The index status endpoint returned `indexed=false` for some uploads during polling, so tests fell back to search polling before succeeding. This matches expected async indexing behavior.
