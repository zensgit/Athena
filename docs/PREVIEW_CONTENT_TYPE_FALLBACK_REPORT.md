# Preview Content-Type Fallback Report

Date: 2025-12-23

## Goal
Prevent PDF preview from falling back to "Preview not available" when the node metadata is missing or generic by using the downloaded blob content type as a fallback.

## Changes
- `DocumentPreview` now records the blob content type and prefers it for preview rendering.
- PDF-specific UI controls (page nav, zoom, rotate) and fallback checks now use the effective content type.

## Files Updated
- `ecm-frontend/src/components/preview/DocumentPreview.tsx`

## Verification
- Command: `npx playwright test e2e/pdf-preview.spec.ts`
- Result: PASS (3 tests, ~13s)

## Notes
- This keeps WOPI routing unchanged while ensuring PDF preview works even when the listing response lacks a reliable MIME type.
