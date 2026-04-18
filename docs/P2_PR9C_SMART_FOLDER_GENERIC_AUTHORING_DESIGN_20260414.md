# P2 PR-9C Smart Folder Generic Authoring Design

## Date
- 2026-04-14

## Goal
- close the deferred generic smart-folder create path without inventing a new folder-settings surface

## Scope
- `CreateFolderDialog`
- `nodeSlice`
- `nodeService`
- frontend tests only

## Why This Shape
- `PR-9B` already exposed a saved-search-driven smart-folder flow
- the remaining gap was generic folder creation from the main browser shell
- there is still no dedicated folder edit/settings UI in Athena, so this phase limits itself to authoring during create

## Design

### 1. Generic Create Dialog Support
- `CreateFolderDialog` now supports a smart-folder mode
- authoring fields:
  - folder name
  - description
  - `Create as Smart Folder`
  - required `Search Query`
  - optional `Path Prefix`
- when smart mode is enabled:
  - the current folder path is used as the default `pathPrefix`
  - save is blocked unless a non-empty search query is present

### 2. Frontend Contract Completion
- `nodeSlice.createFolder` now accepts:
  - `description`
  - `isSmart`
  - `queryCriteria`
- `nodeService.createFolder(...)` now forwards those fields to `/folders`
- this also fixes an older omission where generic folder creation silently dropped `description`

### 3. Folder Response Mapping
- `FolderResponse -> Node` mapping now preserves:
  - `description`
  - `smart`
  - `queryCriteria`
- this keeps the newly created folder's smart semantics available in the current frontend state even though broader folder list/detail APIs still do not expose smart metadata uniformly

## Primary Files
- [CreateFolderDialog.tsx](/Users/chouhua/Downloads/Github/Athena/ecm-frontend/src/components/dialogs/CreateFolderDialog.tsx:1)
- [nodeSlice.ts](/Users/chouhua/Downloads/Github/Athena/ecm-frontend/src/store/slices/nodeSlice.ts:1)
- [nodeService.ts](/Users/chouhua/Downloads/Github/Athena/ecm-frontend/src/services/nodeService.ts:1)
- [types/index.ts](/Users/chouhua/Downloads/Github/Athena/ecm-frontend/src/types/index.ts:12)
- [CreateFolderDialog.test.tsx](/Users/chouhua/Downloads/Github/Athena/ecm-frontend/src/components/dialogs/CreateFolderDialog.test.tsx:1)
- [nodeService.createFolder.test.ts](/Users/chouhua/Downloads/Github/Athena/ecm-frontend/src/services/nodeService.createFolder.test.ts:1)

## Non-goals
- no folder edit/settings screen
- no raw `queryCriteria` JSON authoring
- no expansion of folder listing/browse API contracts to surface smart metadata everywhere
