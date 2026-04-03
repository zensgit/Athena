# Phase 368ZY: Following Navigation And Profile Surface

## Summary

This phase completes the first pass of following discoverability and profile-level visibility without expanding the backend model.

## Delivered

- Added an explicit `My Following` account-menu entry in the main layout that routes to `/activities?scope=following`.
- Added followed-user visibility in the people directory search results.
- Added followed-site visibility in the sites registry table.
- Kept the implementation on the existing `followingService` contract and reused the already-delivered selected-profile and selected-site follow actions.

## Frontend Changes

### Navigation

- `ecm-frontend/src/components/layout/MainLayout.tsx`
  - Added a `My Following` menu entry next to `Activity Feed`.

- `ecm-frontend/src/components/layout/MainLayout.menu.test.tsx`
  - Extended menu assertions so both admin and viewer roles verify the new entry.

### People Directory

- `ecm-frontend/src/pages/PeopleDirectoryPage.tsx`
  - Added a local `followedUserIds` set populated from `followingService.list()`.
  - Kept the selected-profile follow button as the authoritative action.
  - Synced the cached set after follow/unfollow mutations.
  - Added a compact `Following` chip to search result rows for followed users.

### Sites

- `ecm-frontend/src/pages/SitesPage.tsx`
  - Added a local `followedSiteIds` set populated during the page load.
  - Synced the set after site follow/unfollow mutations and after site-detail refresh.
  - Added a compact `Following` chip to the site registry table so followed sites are visible before selection.

## Notes

- This phase intentionally avoids new backend work.
- The layout menu files already had uncommitted `My Following` changes in the working tree at the start of this pass, so this phase preserved those changes and extended the rest of the UI around them.
