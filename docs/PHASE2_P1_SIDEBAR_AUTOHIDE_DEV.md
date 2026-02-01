# Phase 2 (P1) - Sidebar Auto-hide Consistency (Development)

Date: 2026-02-01

## Summary
Ensure the sidebar auto-hide setting applies when navigating into folders from main content areas, not only from the left tree.

## Changes
- Collapse the sidebar after folder navigation from:
  - File list/grid double-click.
  - Breadcrumb navigation.
  - Search results (quick search cards).
  - Advanced search results.

## Files Touched
- ecm-frontend/src/pages/FileBrowser.tsx
- ecm-frontend/src/pages/SearchResults.tsx
- ecm-frontend/src/pages/AdvancedSearchPage.tsx
