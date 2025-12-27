# UI Verification: Search Result Download Failure (2025-12-26)

## Scope
- Ensure a failed download from search results surfaces a user-facing error toast.

## Environment
- UI: `http://localhost:5500`
- API: `http://localhost:7700`
- Auth: Keycloak `http://localhost:8180` (admin/admin)

## Steps
1. Upload a small text file via API.
2. Trigger search indexing for the file.
3. Open Advanced Search and locate the file in results.
4. Intercept the download request and return HTTP 403.
5. Click **Download** on the result card.

## Result
- ✅ Error toast displayed: `Failed to download file`.

