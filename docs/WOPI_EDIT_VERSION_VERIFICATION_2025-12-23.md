# WOPI Edit + Version Increment Verification

Date: 2025-12-23

## Scope
- WOPI Office file Edit Online load
- Version increment verification via check-in API

## WOPI Edit Online (UI)
1. Opened `http://localhost:5500/browse/root`.
2. Context menu on `mcp-wopi-test.docx` → **Edit Online**.
3. Verified editor route loaded with WOPI iframe (`/editor/{docId}?provider=wopi&permission=write`).

Result: **PASS** (Collabora UI loaded inside iframe, document name visible).

## Version Increment (API)
- Document ID: `f27ce160-558d-4ef9-95a7-edf4eb713876`
- Command sequence:
  ```bash
  bash scripts/get-token.sh admin admin
  curl -H "Authorization: Bearer $TOKEN" \
    http://localhost:7700/api/v1/documents/${DOC_ID}/download -o /tmp/wopi-checkin.docx
  curl -X POST -H "Authorization: Bearer $TOKEN" \
    -F "file=@/tmp/wopi-checkin.docx" \
    -F "comment=mcp-wopi-checkin" \
    http://localhost:7700/api/v1/documents/${DOC_ID}/checkin
  curl -H "Authorization: Bearer $TOKEN" \
    http://localhost:7700/api/v1/documents/${DOC_ID}/versions
  ```
- Version count before: 3
- Version count after: 4

Result: **PASS** (version history incremented).

## Notes
- WOPI editing is cross-origin; automated content edits are not driven via MCP, so version increment was validated via API check-in on the same document.
