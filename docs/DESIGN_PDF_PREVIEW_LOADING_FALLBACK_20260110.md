# Design: PDF Preview Loading + Fallback UX (2026-01-10)

## Goal
- Make PDF preview loading and fallback states clearer and actionable.

## Approach
- Add consistent loading panels for document fetch and server-rendered preview.
- Overlay a lightweight loading veil while PDF pages render.
- When client PDF rendering fails or PDFs are empty, surface a banner with retry and download actions.
- Expose stable test IDs and basic ARIA status metadata for loading states.

## Impact
- Users see progress while PDFs render and have clear recovery options.
- Server-rendered fallback stays usable without losing context.
- Accessibility and E2E stability improve for loading/fallback states.

## Files
- ecm-frontend/src/components/preview/DocumentPreview.tsx
- ecm-frontend/e2e/pdf-preview.spec.ts
