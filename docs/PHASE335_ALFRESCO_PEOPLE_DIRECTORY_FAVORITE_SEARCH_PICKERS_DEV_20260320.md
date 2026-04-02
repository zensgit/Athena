# Phase 335 - Alfresco People Directory Favorite Search Pickers

## Goal

Upgrade the People Directory favorites workflow from raw UUID entry to a practical Alfresco-style search-and-select picker for both node favorites and favorite workspaces.

## Scope

- keep the existing people relation APIs
- let users search nodes by name/path before adding favorites
- let users search folders before adding favorite workspaces
- keep UUID paste as a fallback instead of the primary flow

## Frontend Design

`PeopleDirectoryPage` now opens a searchable picker for each writable favorites dialog:

- favorite picker:
  - searches documents and folders through the existing `nodeService.searchNodes(...)`
  - lets the user select a node from the results
  - pre-fills the UUID field from the selected result
- favorite workspace picker:
  - searches only folder results
  - lets the user select a workspace folder before saving
  - keeps manual UUID paste available as a fallback

This keeps the current people relation APIs untouched while removing the friction of copying IDs from the file browser or search results.

## Files

- `ecm-frontend/src/pages/PeopleDirectoryPage.tsx`

## Result

Athena now offers a more usable Alfresco-like selection flow for writable favorites inside People Directory, without any workflow-side changes or new backend endpoints.
