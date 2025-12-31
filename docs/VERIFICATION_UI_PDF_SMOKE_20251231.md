# Verification: UI PDF Preview Smoke (2025-12-31)

## Scope
- Confirm PDF list-view menu label is "Annotate (PDF)".
- Confirm preview header indicates read-only with annotations available.
- Confirm preview content fills dialog height (no bottom gap).

## Environment
- URL: http://localhost:5500/browse/root
- Tooling: MCP Chrome DevTools

## Steps
1. Reload the root browse page.
2. Open a PDF row actions menu.
3. Verify the menu shows "Annotate (PDF)".
4. Click View and verify preview header and Annotate button.
5. Measure dialog content bounds via DevTools.

## Results
- Menu label: PASS ("Annotate (PDF)" displayed).
- Preview header: PASS ("PDF preview is read-only, annotations available" with Annotate button).
- Layout: PASS (content bottom aligns with viewport height).

## Metrics
- viewportHeight: 720
- contentTop: 128
- contentBottom: 720
- contentHeight: 592

## Notes
- No additional UI regressions observed in list view.
