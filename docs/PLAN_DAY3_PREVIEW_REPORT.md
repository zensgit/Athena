# Day 3 Preview Pipeline Report

Date: 2025-12-23 (local)

## Scope
- Validate preview generation for PDF and Office documents.
- Validate thumbnail generation for PDF.
- Validate WOPI integration health and editor URL generation.

## Inputs
- API base: `http://localhost:7700/api/v1`
- Keycloak: `http://localhost:8180/realms/ecm`
- PDF search query: `e2e-preview` (fallback `J0924032`)
- DOCX search query: `mcp-wopi-test.docx`

## Results
- PDF search: `200`
  - Document id: `f4f24bb4-dbbc-40ca-bf20-4bcdc42ea9c3`
- PDF preview: `200`
  - supported: true
  - pageCount: 1
  - mimeType: `application/pdf`
- PDF thumbnail: `200`
- DOCX search: `200`
  - Document id: `f27ce160-558d-4ef9-95a7-edf4eb713876`
- DOCX preview: `200`
  - supported: true
  - pageCount: 1
  - mimeType: `application/vnd.openxmlformats-officedocument.wordprocessingml.document`
- WOPI health: `200`
  - discovery: reachable
  - capabilities: reachable
  - product: Collabora Online Development Edition
- WOPI URL generation: `200`
  - URL returned with access_token + TTL

## UI Verification (MCP)
- PDF preview (search results):
  - `J0924032-02上罐体组件v2-模型.pdf` loads in the viewer.
  - `e2e-preview-1766301291888.pdf` loads in the viewer (previously failing sample).
  - `e2e-preview-fallback-1766364405124.pdf` renders (“Dummy PDF file”).
  - Screenshots: `tmp/pdf-preview-mcp.png`, `tmp/pdf-preview-mcp-after-rebuild.png`, `tmp/pdf-preview-mcp-e2e-preview.png`

## Update
- Added PDF preview fallback handling to flag empty PDFs and trigger server preview.
- Reset PDF failure state on retry.
- Frontend build: `npm run build` ✓

## Artifacts
- `tmp/day3-preview-20251223_082158-summary.txt`
- `tmp/day3-preview-20251223_082158-pdf-search.json`
- `tmp/day3-preview-20251223_082158-pdf-preview.json`
- `tmp/day3-preview-20251223_082158-pdf-thumb.bin`
- `tmp/day3-preview-20251223_082158-docx-search.json`
- `tmp/day3-preview-20251223_082158-docx-preview.json`
- `tmp/day3-preview-20251223_082158-wopi-health.json`
- `tmp/day3-preview-20251223_082158-wopi-url.json`

## Notes
- Preview succeeded for PDF and DOCX via the API.
- If PDF previews fail in UI, check for empty content and/or WOPI configuration mismatches.
