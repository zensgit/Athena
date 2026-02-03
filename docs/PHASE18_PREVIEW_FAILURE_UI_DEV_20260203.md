# Phase 18 - Preview Failure UI Hints (Dev) - 2026-02-03

## Goal
Make preview failure reasons more discoverable in list/search views.

## Scope
- Add a dedicated info icon when preview status is FAILED and a failure reason exists.
- Keep existing status chips intact.

## Implementation Notes
- Search results and file browser now render an info icon with tooltip for failure reasons.
- Search index payloads include preview status + failure reason so search results can render the status chip.
- Preview status persistence now reindexes the document so search results stay in sync after preview runs.
- Preview status updates retry once on optimistic locking to avoid PROCESSING getting stuck during concurrent queue work.
- This surfaces reasons without relying solely on chip tooltips.

## Files Updated
- `ecm-core/src/main/java/com/ecm/core/preview/PreviewService.java`
- `ecm-core/src/main/java/com/ecm/core/search/NodeDocument.java`
- `ecm-core/src/main/java/com/ecm/core/search/SearchResult.java`
- `ecm-core/src/main/java/com/ecm/core/search/FullTextSearchService.java`
- `ecm-frontend/src/pages/SearchResults.tsx`
- `ecm-frontend/src/components/browser/FileList.tsx`
- `ecm-frontend/src/services/nodeService.ts`
- `ecm-frontend/e2e/search-preview-status.spec.ts`
