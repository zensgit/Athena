# WOPI View (Read) Verification

Date: 2025-12-24

## Scope
- File Browser "View" action for Office files
- WOPI read-only preview

## Steps
1. Opened `http://localhost:5500/browse/root` as admin.
2. For `工作簿1.xlsx`, opened the context menu → **View**.
3. Verified preview dialog opened with WOPI iframe and document name visible.
4. Confirmed Collabora shows **Read-only** status in the UI.

## Result
- **PASS**: View action loads WOPI preview in read-only mode for Excel files.

## Evidence
- MCP snapshot showed dialog header `工作簿1.xlsx` and WOPI iframe with document name; Collabora status displayed "Read-only".
