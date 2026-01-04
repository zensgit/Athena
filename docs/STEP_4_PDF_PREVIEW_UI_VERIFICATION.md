# Step 4 Verification: PDF Preview Layout Fill

## Scope
- Remove excess bottom whitespace in full-screen PDF preview.

## Changes Implemented
- `ecm-frontend/src/components/preview/DocumentPreview.tsx`:
  - Preview container height set to `100%`.
  - `DialogContent` no longer uses fixed `height`/`maxHeight`; relies on flex fill.
  - Removed unused breakpoint height logic.
- `ecm-frontend/e2e/ui-smoke.spec.ts`:
  - Added layout check for PDF preview: dialog height ≈ app bar + content height (gap < 12px).

## Verification
- UI test (PDF preview layout + version history):
  - Command: `ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 npx playwright test e2e/ui-smoke.spec.ts -g "version history"`
  - Result: ✅ Passed

## Notes
- The layout check uses bounding box measurements of the dialog, app bar, and content area.
