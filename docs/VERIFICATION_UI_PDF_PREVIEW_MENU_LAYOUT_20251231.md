# Verification: UI PDF Preview Menu + Layout (2025-12-31)

## Scope
- Confirm PDF context menu shows "Annotate (PDF)" in list view.
- Confirm preview header states read-only with annotations available.
- Confirm preview content fills dialog height (no bottom gap).
- Confirm frontend responds over HTTP.

## Environment
- URL: http://localhost:5500/browse/root
- Rebuild: docker compose up -d --build ecm-frontend
- Tooling: MCP Chrome DevTools

## Steps
1. Rebuild frontend container and reload the UI.
2. Open actions menu for a PDF row in root list view.
3. Open View and check the preview header and annotate button.
4. Capture layout metrics via DevTools (content vs viewport).
5. Run: curl http://localhost:5500/ (status check).

## Results
- Menu label: PASS ("Annotate (PDF)" displayed).
- Preview header: PASS ("PDF preview is read-only, annotations available" with Annotate button).
- Layout: PASS (content bottom aligns with viewport height).
- HTTP: PASS (200).

## Metrics
- viewportHeight: 720
- contentTop: 128
- contentBottom: 720
- contentHeight: 592

## Notes
- MCP screenshot capture timed out; DOM measurements used instead.
