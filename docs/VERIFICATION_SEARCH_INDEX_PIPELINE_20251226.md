# Verification: Upload Pipeline → Search Index (2025-12-26)

## Scope
- Confirm that a new upload is indexed automatically by the pipeline.

## Environment
- API: `http://localhost:7700/api/v1`
- Keycloak: `http://localhost:8180` (realm `ecm`, client `unified-portal`)
- Account: `admin/admin`

## Test Data
- Filename: `mcp-index-check-20251226_132252.txt`
- Document ID: `7b1c7b91-960c-4705-8a62-8c19b5f55953`
- Root folder ID: `d47a22e5-4aae-4bae-a9b1-8b045ba8f2a0`

## Steps
1. Upload file to the root folder using `POST /api/v1/documents/upload`.
2. Poll `GET /api/v1/search?q={filename}` for up to 12 seconds.

## Result
- ✅ Search index returned the uploaded filename within the polling window.
- ✅ No manual re-indexing required for this upload.

