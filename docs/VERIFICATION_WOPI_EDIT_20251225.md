# WOPI Online Edit Verification (2025-12-25)

## Scope
Confirm Office document opens in Collabora Online (write mode) via Edit Online.

## Environment
- Frontend: http://localhost:5500
- Backend: http://localhost:7700
- Keycloak: http://localhost:8180
- User: admin

## Document
- 工作簿1.xlsx (Document ID: 9cd1becc-98dd-4d31-abc4-ad3ef1ba7d03)

## Steps
1. `Browse /` → Actions → **Edit Online** for 工作簿1.xlsx.
2. Confirm Collabora UI loads within the editor iframe.
3. Verify toolbar tabs and Save button are present (write mode).
4. Check version history count before/after open.

## Results
- Collabora Online loaded successfully.
- UI tabs visible: File, Home, Insert, Layout, Data, Review, Format, View, Help.
- Save button visible (write mode).
- Version count before/after open: 14 → 14 (no content change applied).

## Notes
- This validation confirms editor availability and write-mode access. No edits were applied to avoid modifying existing content.
