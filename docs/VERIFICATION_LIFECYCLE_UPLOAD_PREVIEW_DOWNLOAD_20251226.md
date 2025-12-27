# Upload → Preview → Download Verification

Date: 2025-12-26

## Setup
- Test file: `tmp/mcp-lifecycle-20251226_102147.txt`
- Content: `mcp lifecycle test 20251226_102147`
- Target folder: `mcp-lifecycle-20251226_102344` (id `50fa71a6-36ad-4432-91a5-336e4496498c`)

## Steps
1. Created folder under root via `POST /api/v1/folders`.
2. Uploaded file via `POST /api/v1/documents/upload` with `folderId`.
3. Opened UI at `/browse/50fa71a6-36ad-4432-91a5-336e4496498c` and used context menu → View.
4. Downloaded file via `GET /api/v1/nodes/8cc38bee-76e1-4648-a19c-a99ef476cf46/content` and compared contents.

## Results
- Upload succeeded (`documentId` `8cc38bee-76e1-4648-a19c-a99ef476cf46`).
- UI preview opened and displayed file contents correctly.
- Downloaded content matched the original file (no diff).
