# Phase 7 P1 Search Preview Status Filters - Development

## Summary
Expose preview status chips in search results and add quick preview-status filtering.

## Scope
- Always show preview status chips (ready/processing/queued/failed/pending).
- Add preview status filter chips with counts in the facet sidebar.
- Surface active preview filters with removable chips.

## Implementation
- Added `selectedPreviewStatuses` state and filter helpers.
- Computed preview status counts from the current result set.
- Applied client-side filtering for selected preview statuses.

## Files Changed
- `ecm-frontend/src/pages/SearchResults.tsx`
