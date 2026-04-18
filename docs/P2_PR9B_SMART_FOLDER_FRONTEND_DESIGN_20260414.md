# P2 PR-9B Smart Folder Frontend Design

## Date
- 2026-04-14

## Goal
- expose the already-safe smart-folder backend through a minimal user-facing flow without expanding generic folder authoring yet

## Scope
- `SavedSearchesPage`
- `savedSearchService`
- frontend tests only

## Why This Shape
- backend bridge already exists at `/api/v1/search/saved/{id}/smart-folder`
- `SavedSearchesPage` already owns the most natural user intent for query-backed folder creation
- extending generic `CreateFolderDialog` would force query authoring UX and DTO plumbing into a broader write path than needed for the first shippable slice

## Design

### 1. Service Bridge
- add `savedSearchService.createSmartFolder(id, input)`
- payload:
  - `name`
  - `description`
  - optional `parentId`

### 2. Saved Search Action
- add a per-row action on `SavedSearchesPage`
- action opens a lightweight dialog:
  - prefilled folder name from saved search name
  - optional description
- submit flow:
  - call `savedSearchService.createSmartFolder(...)`
  - show success toast
  - navigate to `/browse/{folderId}`

### 3. Deliberate Non-goals
- no generic smart-folder controls in `CreateFolderDialog`
- no raw `queryCriteria` authoring UI
- no changes to `nodeService.createFolder(...)`

## Files
- [savedSearchService.ts](/Users/chouhua/Downloads/Github/Athena/ecm-frontend/src/services/savedSearchService.ts:1)
- [SavedSearchesPage.tsx](/Users/chouhua/Downloads/Github/Athena/ecm-frontend/src/pages/SavedSearchesPage.tsx:1)
- [savedSearchService.test.ts](/Users/chouhua/Downloads/Github/Athena/ecm-frontend/src/services/savedSearchService.test.ts:1)
- [SavedSearchesPage.test.tsx](/Users/chouhua/Downloads/Github/Athena/ecm-frontend/src/pages/SavedSearchesPage.test.tsx:1)

## Deferred
- richer parent-folder selection
- smart-folder badges in browser listings
- generic smart-folder create/update authoring from folder dialogs
