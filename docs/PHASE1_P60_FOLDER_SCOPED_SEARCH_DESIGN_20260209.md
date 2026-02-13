# PHASE1 P60 - Folder-Scoped Search (Design)

Date: 2026-02-09

## Goal

When users trigger search while browsing a folder, default to "search within this folder" (optionally including subfolders), similar to Alfresco's folder-scoped search. This reduces noise and prevents accidental cross-folder result leakage in common workflows.

## UX / Product Behavior

- From `/browse/:nodeId` where `nodeId` is a folder:
  - Clicking the search icon opens Advanced Search with scope prefilled to the current folder.
  - UI shows a visible chip: `Scope: This folder`.
  - UI offers a toggle: `Include subfolders` (default `true`).
- On Search Results page:
  - Scope chip is shown below the search bar when active.
  - Users can clear the scope chip to re-run the same query without folder scoping.

## API Changes

### 1) Quick full-text search

`GET /api/v1/search`

New query params:

- `folderId` (optional, UUID): limit results to a folder scope
- `includeChildren` (optional, boolean, default `true`): include subfolders when `folderId` is provided

Example:

```bash
curl -G 'http://localhost:7700/api/v1/search' \
  --data-urlencode 'q=report' \
  --data-urlencode 'folderId=<FOLDER_UUID>' \
  --data-urlencode 'includeChildren=true'
```

### 2) Advanced search / Faceted search

`SearchFilters` adds:

- `folderId?: string`
- `includeChildren: boolean = true`

Folder scope is applied in:

- `POST /api/v1/search/advanced`
- `POST /api/v1/search/faceted` (via `SearchFilters` usage)

## Backend Implementation

Folder scope is implemented consistently in both full-text and filtered queries:

- `includeChildren=false`:
  - Apply `term(parentId = folderId)` to return direct children only.
- `includeChildren=true`:
  - Resolve folder path from PostgreSQL (`nodeRepository.findByIdAndDeletedFalse`).
  - Apply a strict prefix filter: `path` starts with `folderPath + "/"`.
    - The trailing slash avoids sibling collisions (e.g., `/Root/foo2` matching `/Root/foo`).
- Safety:
  - If folder does not exist or has no path, force empty results by filtering `parentId="__none__"` to avoid information leakage.
- Index field compatibility:
  - Prefix filter is applied as `OR(prefix(path.keyword), prefix(path))` to tolerate mapping differences.

## Frontend Implementation

- `MainLayout`:
  - When user clicks Search while browsing a folder, dispatches `setSearchPrefill({ folderId, includeChildren: true })` then opens Search dialog.
- `SearchDialog`:
  - Supports prefill; shows `Scope: This folder` chip + `Include subfolders` toggle in the Basic Search section (always visible when scoped).
  - When scoped, disables `Path prefix` input to avoid conflicting scopes.
- `SearchResults`:
  - Preserves scope when performing "quick searches" from the results page.
  - Provides a scope chip that can be removed to clear the scope and re-run.
- `nodeService`:
  - Quick search endpoint (`GET /api/v1/search`) now forwards `folderId/includeChildren` when present.
  - Keeps the "punctuation-friendly" fast path when the only filter is folder scope.

## Testing

- E2E: `ecm-frontend/e2e/folder-scoped-search.spec.ts`
  - Creates two sibling folders, uploads docs, indexes them
  - Searches from within folder A and verifies only A's doc appears
  - Clears scope and verifies both docs appear

## Compatibility / Rollout Notes

- Existing clients that do not send `folderId` are unaffected.
- Admin ACL bypass behavior is unchanged; folder scope is applied prior to ACL filtering for non-admin users.

