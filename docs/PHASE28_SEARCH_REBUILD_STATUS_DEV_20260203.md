# Phase 28 Search Rebuild Status (2026-02-03)

## Goal
Surface search index rebuild progress to admins in the Search Results diagnostics panel.

## Changes
- Added `/search/index/rebuild/status` display in the Access scope panel.
- Shows rebuild state (in progress/idle) and indexed document count.

## Files
- `ecm-frontend/src/services/nodeService.ts`
- `ecm-frontend/src/pages/SearchResults.tsx`
- `ecm-frontend/e2e/search-view.spec.ts`
