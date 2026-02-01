# Phase 3 (P1) - Preview Status Badges (Development)

Date: 2026-02-01

## Summary
Surface preview queue status (processing/queued/failed) directly in list/grid/search results so users understand post-upload progress.

## Changes
- Added preview status chips for documents with non-ready preview states.
- Included optional failure reason tooltip when available.

## Files Touched
- ecm-frontend/src/components/browser/FileList.tsx
- ecm-frontend/src/pages/SearchResults.tsx
