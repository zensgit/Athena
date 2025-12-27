# WOPI Edit + Version Increment Verification (2025-12-25)

## Scope
Modify an Office file (xlsx) and confirm version increment via WOPI PutFile.

## Environment
- Backend: http://localhost:7700
- Keycloak: http://localhost:8180
- User: admin

## Test Document (Copy)
- File: wopi-edit-test-debug.xlsx
- Document ID: 74e680a3-5b9d-45c7-8eb4-1faef99f3180

## Steps
1. Download the copied xlsx.
2. Modify cell values:
   - A1 = `wopi-edit-<timestamp>`
   - A2 = `<UTC ISO timestamp>`
3. Call WOPI PutFile (`POST /wopi/files/{id}/contents?access_token=...`).
4. Check version history count before/after.

## Results
- Versions before: 0
- Versions after: 1
- WOPI PutFile HTTP: 200

## Conclusion
WOPI update succeeded and created a new version entry after modifying cell values.
