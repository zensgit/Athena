# Search Preview Queue P7 — Details Panel

Date: 2026-02-06

## Goal
Provide a lightweight list of queued preview retries on the current search results page.

## Design
- When preview queue status is available for items on the current page, show a small list under the preview status filters.
- Each entry shows document name, status, attempts, and next retry time (if available).

## Files Changed
- `ecm-frontend/src/pages/SearchResults.tsx`

