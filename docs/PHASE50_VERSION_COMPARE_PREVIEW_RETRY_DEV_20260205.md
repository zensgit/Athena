# Phase 50 - Version Compare Summary + Preview Retry (Dev)

## Goals
- Add a concise change summary when comparing document versions.
- Allow retrying failed preview generation directly from search results.

## Frontend Changes
- Version history compare dialog now shows a change summary (size delta, major flag change, checksum change, mime change, comment update).
- Search results show a "Retry preview" action when preview status is FAILED; triggers queue preview API.

## API Usage
- Reuse `POST /api/v1/documents/{id}/preview/queue` for retry actions.

## Files Updated
- `ecm-frontend/src/components/dialogs/VersionHistoryDialog.tsx`
- `ecm-frontend/src/pages/SearchResults.tsx`
- `ecm-frontend/src/services/nodeService.ts`
- `ecm-frontend/e2e/search-preview-status.spec.ts`
