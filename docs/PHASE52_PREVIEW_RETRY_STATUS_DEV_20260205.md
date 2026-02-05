# Phase 52 - Preview Retry Status in Search (Dev)

## Goals
- Expose preview retry queue status (attempts, next retry) directly on search result cards.

## Frontend Changes
- Search results preview status now shows retry queue details when available.
- Retry action stores queue status for display.
- Added bulk retry button for failed previews on the current page.

## Files Updated
- `ecm-frontend/src/pages/SearchResults.tsx`
- `ecm-frontend/src/services/nodeService.ts`
