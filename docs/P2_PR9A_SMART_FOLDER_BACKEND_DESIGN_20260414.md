# P2 PR-9A Smart Folder Backend Design

## Date
- 2026-04-14

## Scope
- backend only
- frontend UI entry intentionally deferred to `PR-9B`

## Goal
- close Athena's partial Smart Folder path at the repository/API layer before exposing more UI surface

## Problems Fixed
- smart folders could be created but not safely updated
- invalid `queryCriteria` could degrade into silent empty results
- search result ordering was lost after `findAllById(...)`
- physical children could still be created or moved under smart folders through non-UI paths
- saved searches had no first-class bridge to smart folder creation

## Design

### 1. Smart Folder Validation
- `FolderService.createFolder(...)` now validates smart-folder payloads before persistence.
- `FolderService.updateFolder(...)` now supports `isSmart/queryCriteria` and blocks incompatible transitions.
- validation rules:
  - smart folder requires non-empty `queryCriteria`
  - `queryCriteria` must resolve to at least one selector: `query`, `filters`, or `pathPrefix`
  - non-empty physical folder cannot be converted to smart
  - `queryCriteria` is rejected for non-smart folders

### 2. Runtime Query Execution
- `FolderService.getFolderContents(...)` now routes smart folders through a dedicated helper instead of inline best-effort logic.
- search hit order is preserved by re-sorting loaded nodes according to returned hit order.
- invalid smart-folder criteria now fail as explicit `IllegalArgumentException` instead of returning `Page.empty(...)`.
- `getFolderContentsFiltered(...)` now delegates smart folders through the query-backed path rather than reading physical children.

### 3. Physical Child Guards
- `FolderService.createFolder(...)` rejects creating a folder under a smart folder.
- `NodeService.createNode(...)`, `moveNode(...)`, and `copyNode(...)` reject using a smart folder as a physical target parent.
- `FolderService.canAcceptItems(...)` and `canAcceptFileType(...)` return `false` for smart folders.

### 4. Saved Search Bridge
- `SavedSearchService.createSmartFolder(...)` creates a smart folder from a saved search owned by the current user.
- `SavedSearchController` exposes:
  - `POST /api/v1/search/saved/{id}/smart-folder`
- bridge semantics:
  - default folder name falls back to saved search name
  - saved search `queryParams` become smart-folder `queryCriteria`
  - parent folder stays caller-controlled

## Primary Files
- [FolderService.java](/Users/chouhua/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/FolderService.java:49)
- [FolderController.java](/Users/chouhua/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/controller/FolderController.java:106)
- [NodeService.java](/Users/chouhua/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/NodeService.java:54)
- [SavedSearchService.java](/Users/chouhua/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/SavedSearchService.java:259)
- [SavedSearchController.java](/Users/chouhua/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/controller/SavedSearchController.java:86)

## Deferred To PR-9B
- `SavedSearchesPage` smart-folder action
- optional smart-folder fields in `CreateFolderDialog`
- frontend tests for `nodeService.createFolder(...)` and saved-search UI flow
