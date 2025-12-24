# PDF Preview Layout Fix Report

Date: 2025-12-24

## Goal
Reduce the bottom whitespace in the PDF preview dialog by matching the preview container height to the AppBar height instead of a fixed 200px offset.

## Changes
- PDF preview containers now use `calc(100vh - 64px)` instead of `calc(100vh - 200px)`.
- Applies to PDF, image, text, and WOPI preview containers.

## Files Updated
- `ecm-frontend/src/components/preview/DocumentPreview.tsx`

## Verification
- Command: `npx playwright test e2e/pdf-preview.spec.ts`
- Result: PASS (3 tests, ~10s)

## Notes
- Visual confirmation: open a PDF preview at 100%+ zoom; the preview container should extend to the bottom without the large empty gap.
