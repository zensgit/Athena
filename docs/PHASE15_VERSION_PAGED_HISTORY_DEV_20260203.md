# Phase 15 - Version History Pagination + Major Filter (Dev) - 2026-02-03

## Goal
Deliver the Phase 1 P0 requirement for paged version history with an optional "major-only" filter, aligning the UI with the existing backend paged endpoint.

## Scope
- Frontend uses `/documents/{id}/versions/paged` instead of the unbounded history list.
- Add a "Major versions only" toggle to the Version History dialog.
- Add incremental paging with a "Load more" CTA.

## Implementation Notes
- Added a paged version history client method in `nodeService` to map Spring Data page responses to UI `Version` objects.
- Version History dialog now tracks paging state (`page`, `totalElements`) and re-fetches on toggle changes.
- The dialog preserves ordering (versionNumber desc) and limits comparison availability to loaded rows.

## Files Updated
- `ecm-frontend/src/services/nodeService.ts`
- `ecm-frontend/src/components/dialogs/VersionHistoryDialog.tsx`

## API Contracts
- `GET /api/v1/documents/{documentId}/versions/paged?page={page}&size={size}&majorOnly={bool}`

## Notes
- No backend changes required; endpoint already exists in `DocumentController`.
- Defaults to page size 20; can be tuned if needed.
