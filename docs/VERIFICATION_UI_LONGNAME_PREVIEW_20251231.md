# Verification: UI Long Name & Preview Error Actions (2025-12-31)

## Scope
- Long file name typography (3-line clamp + font scale) in list/grid and search results.
- Preview error actions (retry, server preview, open, download) in document preview.

## Environment
- UI: http://localhost:5500/browse/root
- Tooling: MCP Chrome DevTools
- Lint: `npm run lint`

## Steps
1. Run frontend lint.
2. Open the root file list view.
3. Confirm list view renders normally after typography changes.
4. (Manual) Trigger a preview failure to verify new action buttons.

## Results
- Lint: PASS.
- UI load: PASS (root list view renders without errors).
- Long-name 3-line visual check: NOT VERIFIED (no sufficiently long file name in root list to force 3-line clamp).
- Preview failure actions: NOT VERIFIED (requires a preview failure case).

## Notes
- For preview failure verification: open a document that fails preview (e.g., temporarily block `/nodes/{id}/content` or use a missing node) and confirm buttons: Retry, Try server preview, Open file, Download.
- For long-name verification: create/rename a file to >80 visible chars and confirm 3-line clamp + smaller font.
