# Search Results Preview Verification Report

## Summary
- Fixed backend preview MIME normalization to infer file types from file names when MIME is generic (e.g., `application/octet-stream`).
- Rebuilt the `ecm-core` container and verified PDF preview from Search Results for a valid PDF entry.

## Code Changes
- `ecm-core/src/main/java/com/ecm/core/preview/PreviewService.java`
  - Normalize MIME values (lowercase, strip parameters).
  - Treat generic/blank MIME values as unknown and infer from filename extension.
  - Added extension-to-MIME mapping for PDF, images, text, and common Office formats.

## Verification Steps (MCP)
1. Open `http://localhost:5500/search-results`.
2. Use Quick search by name with `J0924032-02`.
3. From results, open a PDF entry that reports a size of `377.63 KB` (type shown as `PDF`).
4. Verify the preview dialog opens and renders the PDF (page indicator shows `1 / 1`).

## Results
- ✅ Search Results `View` for a valid PDF entry now renders correctly.
- ⚠️ Entries that show size `-` can still fail to preview with `End-of-File` errors; these appear to be data/content issues rather than MIME detection.

## Notes
- The preview failure for empty content items is expected until those items are re-uploaded or fixed at the content layer.

## Update: Empty PDF Guard
- Backend now returns a clear message when PDF content is empty, avoiding PDFBox EOF errors.

### MCP Verification (empty content)
1. In Search Results, open a result where Size shows "-".
2. Click View.
3. Confirm message: "Preview not available for empty PDF content".

Result: ✅ Message appears as expected (no PDFBox EOF).

## Update: Empty PDF Logging
- Added a warn-level log when a PDF preview is skipped due to empty content.
- Log message includes document id and name for traceability.

### MCP Verification (empty content logging)
- Triggered Preview on an empty-content PDF from Search Results.
- Observed log output:
  - `Preview skipped for empty PDF content. documentId=... name=...`

### Note
- Tomcat logged an invalid `Content-Disposition` header for non-ASCII filenames (e.g., "上").
  This is separate from the empty-content fix but may affect downloads for non-ASCII names.

## Update: Non-ASCII Filename Download Headers
- Switched download responses to RFC 5987-compatible `Content-Disposition` with UTF-8 filename.
- Affects document downloads, node content downloads, batch downloads, and audit export.

### MCP Verification (log check)
- Triggered preview/download for a document with non-ASCII name ("J0924032-02上罐体组件v2-模型.pdf").
- Observed no `Content-Disposition` encoding errors in logs; only expected empty-content preview warning.

## Update: UTF-8 Content-Disposition Verification
- Verified via API that `Content-Disposition` now includes `filename*` with UTF-8 encoding for non-ASCII filenames.
- Example header observed:
  `attachment; filename="=?UTF-8?Q?...?="; filename*=UTF-8''J0924032-02%E4%B8%8A...pdf`
