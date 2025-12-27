# Search + Preview + Download Verification (2025-12-26)

## Scope
- Verify search finds the uploaded lifecycle test file.
- Verify preview API returns supported content for the file.
- Verify download content matches the uploaded source file.

## Environment
- API base: `http://localhost:7700/api/v1`
- Keycloak: `http://localhost:8180` (realm: `ecm`, client: `unified-portal`)
- Account: `admin/admin`

## Test Data
- Document name: `mcp-lifecycle-20251226_102147.txt`
- Document ID: `8cc38bee-76e1-4648-a19c-a99ef476cf46`
- Source file: `tmp/mcp-lifecycle-20251226_102147.txt`
- Downloaded file: `tmp/mcp-lifecycle-download-20251226_131030.txt`

## Steps
1. Request access token from Keycloak (password grant).
2. Ensure the document is indexed with `POST /api/v1/search/index/{documentId}`.
3. Search with `GET /api/v1/search?q=mcp-lifecycle-20251226_102147`.
4. Preview with `GET /api/v1/documents/{documentId}/preview`.
5. Download content with `GET /api/v1/nodes/{documentId}/content`.
6. Compare SHA-256 of source vs. downloaded file.

## Results
- Search matched the document ID and filename.
- Preview returned `supported=true` with text content for page 1.
- SHA-256 matches between source and downloaded content.

## Evidence
- Search response contains:
  - `name`: `mcp-lifecycle-20251226_102147.txt`
  - `id`: `8cc38bee-76e1-4648-a19c-a99ef476cf46`
- Preview response contains:
  - `supported`: `true`
  - `pageCount`: `1`
  - `textContent`: `mcp lifecycle test 20251226_102147`
- Content hash:
  - Source: `0527ff2c331ea1df39cf4867d1e75488aee3860a0028fe2e3750bc5eef4d6625`
  - Download: `0527ff2c331ea1df39cf4867d1e75488aee3860a0028fe2e3750bc5eef4d6625`

