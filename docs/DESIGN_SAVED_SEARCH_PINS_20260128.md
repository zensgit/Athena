# Design: Saved Search Pins (2026-01-28)

## Goals
- Let users pin favorite saved searches for quick access.
- Surface pinned searches on the Admin dashboard.
- Persist pins per user and sync across devices.

## Scope
- Backend persistence for pinned state.
- Frontend reads/writes pinned state via API.

## Backend Design
- Add `pinned` boolean column to `saved_searches` (default false).
- Expose pin toggle endpoint:
  - `PATCH /api/v1/search/saved/{id}/pin` with `{ "pinned": true|false }`
- Return pinned state in saved search list.

## Frontend Design
- Saved Searches page:
  - Add pin/unpin action per row.
  - Call pin toggle API and update UI state.
- Admin Dashboard:
  - Add "Pinned Saved Searches" panel.
  - Load saved searches, filter by `pinned`, show pinned first.
  - Actions: run search, unpin, and link to manage pins.

## Trade-offs
- Pinned list syncs across devices but depends on backend availability.
- Admin dashboard is admin-only; non-admin users rely on Saved Searches page.

## Files
- `ecm-core/src/main/java/com/ecm/core/entity/SavedSearch.java`
- `ecm-core/src/main/java/com/ecm/core/service/SavedSearchService.java`
- `ecm-core/src/main/java/com/ecm/core/controller/SavedSearchController.java`
- `ecm-core/src/main/java/com/ecm/core/repository/SavedSearchRepository.java`
- `ecm-core/src/main/resources/db/changelog/changes/020-add-saved-searches-table.xml`
- `ecm-frontend/src/services/savedSearchService.ts`
- `ecm-frontend/src/pages/SavedSearchesPage.tsx`
- `ecm-frontend/src/pages/AdminDashboard.tsx`
- `ecm-frontend/src/utils/savedSearchUtils.ts`
