# Design: Saved Search Pins (2026-01-28)

## Goals
- Let users pin favorite saved searches for quick access.
- Surface pinned searches on the Admin dashboard.

## Scope
- Frontend-only persistence using localStorage.
- No backend changes.

## Frontend Design
- Store pinned saved search IDs in `localStorage` under `ecm_saved_search_pins`.
- Saved Searches page:
  - Add pin/unpin action per row.
  - Persist changes immediately.
- Admin Dashboard:
  - Add "Pinned Saved Searches" panel.
  - Load saved searches, filter by pinned IDs, show in pin order.
  - Actions: run search, unpin, and link to manage pins.

## Trade-offs
- Pinned list is per-browser (localStorage), not synced across devices.
- Admin dashboard is admin-only; non-admin users rely on Saved Searches page.

## Files
- `ecm-frontend/src/pages/SavedSearchesPage.tsx`
- `ecm-frontend/src/pages/AdminDashboard.tsx`
- `ecm-frontend/src/utils/savedSearchPins.ts`
- `ecm-frontend/src/utils/savedSearchUtils.ts`
