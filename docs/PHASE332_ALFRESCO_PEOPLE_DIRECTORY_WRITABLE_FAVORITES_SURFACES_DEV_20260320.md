# Phase 332 - Alfresco People Directory Writable Favorites Surfaces

## Goal

Push Athena beyond the earlier read/remove-only people workspace by letting the current user manage favorites and favorite workspaces directly inside `PeopleDirectoryPage`.

## Scope

- add `Add by node ID` for favorites
- add `Add workspace` for favorite sites
- switch remove actions to the new people relation resources
- keep preview/discuss/open flows unchanged

## Frontend Design

`PeopleDirectoryPage` now exposes two lightweight dialogs:

- favorite dialog:
  - accepts a node UUID
  - creates a new relation via `peopleService.createFavorite(...)`
- favorite workspace dialog:
  - accepts a folder UUID
  - creates a new relation via `peopleService.createFavoriteSite(...)`

Existing remove actions now use the people relation endpoints so the directory page becomes a full self-service collaboration hub instead of only a reporting surface.

## Files

- `ecm-frontend/src/services/peopleService.ts`
- `ecm-frontend/src/pages/PeopleDirectoryPage.tsx`

## Result

Athena now gives current users a writable favorites workspace directly inside the people directory, which is a stronger collaboration entry point than the earlier read-only profile panels.
