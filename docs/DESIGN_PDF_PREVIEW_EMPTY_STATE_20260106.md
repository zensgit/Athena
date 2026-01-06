# Design: PDF Preview Empty-State Recovery (2026-01-06)

## Goal
- Provide clearer recovery messaging when PDF previews are empty or server-rendered pages are missing.

## Approach
- Track empty PDF blobs to show a dedicated message.
- When server previews return zero pages, show a specific error with retry/fallback actions.
- Keep server preview fallback behavior intact.

## Files
- ecm-frontend/src/components/preview/DocumentPreview.tsx
