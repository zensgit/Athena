# UI Verification: Search → Preview → Download (2025-12-26)

## Scope
- Verify UI search returns the target document.
- Verify preview opens from search results.
- Verify download action triggers from search results.

## Environment
- UI: `http://localhost:5500`
- API: `http://localhost:7700`
- Auth: Keycloak `http://localhost:8180` (admin/admin)

## Steps
1. Log in via Keycloak as admin.
2. Open Advanced Search, query `mcp-lifecycle-20251226_102147`.
3. Confirm search results page shows the file.
4. Click **View** on the result and confirm preview opens.
5. Close preview.
6. Click **Download** on the same result.

## Result
- ✅ Search results show `mcp-lifecycle-20251226_102147.txt`.
- ✅ Preview opens from search results and shows file contents.
- ✅ Download action triggers (no UI errors observed).

