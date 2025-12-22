# Folder Contents contentType - Validation Report

Date: 2025-12-22

## Environment
- API base: `http://localhost:7700`
- Auth: admin token from `scripts/get-token.sh`

## Folder IDs Used
- Root: `d10b1a9e-5b83-4743-a58f-9870ca46d2a0`
- Documents: `f5801c2f-3f66-4dc2-a86e-81b9e41fbf63`

## Verification Steps
1. `GET /api/v1/folders/roots` to select system Root.
2. `GET /api/v1/folders/{rootId}/contents` to locate `Documents`.
3. `GET /api/v1/folders/{documentsId}/contents` and check document entries include `contentType`.

## Evidence (sample from Documents)
- `ui-e2e-1765937896254.txt` → `contentType: text/plain`
- `J0924032-02上罐体组件v2-模型.pdf` → `contentType: application/pdf`
- `ui-e2e-1765938658945.txt` → `contentType: text/plain`

## Result
✅ `contentType` is present for document entries returned by `/api/v1/folders/{id}/contents`.
