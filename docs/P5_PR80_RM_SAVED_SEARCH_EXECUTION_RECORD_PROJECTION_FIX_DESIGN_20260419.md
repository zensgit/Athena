# P5 PR80 RM Saved Search Execution Record Projection Fix Design

## Scope

This slice closes a gap left after `PR-76` through `PR-79`.

The missing path was:

- `SavedSearchesPage -> Run saved search`
- `SearchResults?savedSearchId=...`
- `executeSavedSearch(...)`

Backend `saved search execute` responses already carry RM projection fields, but the frontend mapping dropped them before the results reached `SearchResults`.

## Problem

`executeSavedSearch(...)` in `nodeSlice` only mapped base search fields and always returned:

- empty `aspects`
- no `record`
- no `declared*`
- no `recordCategory*`

That meant declared records opened through saved-search execution could lose `RecordStatusChip` even though:

- regular search results had RM projection
- advanced search results had RM projection
- saved-search prefill preserved `recordOnly` and `recordCategoryPaths`

## Design

Keep the fix minimal and local to the saved-search execution chain.

### Frontend data contract

Extend `savedSearchService.SearchResultItem` to reflect the existing backend payload:

- `currentVersionLabel`
- `record`
- `declaredBy`
- `declaredAt`
- `declaredVersionLabel`
- `declarationComment`
- `recordCategoryId`
- `recordCategoryName`
- `recordCategoryPath`
- preview projection fields

No backend API change is required.

### Saved-search result mapping

Add a dedicated mapper in `nodeSlice` for saved-search execution results.

The mapper mirrors the already shipped search projection semantics:

- infer `nodeType`
- preserve RM properties under `rm:*`
- derive `record`
- add `rm:record` to `aspects` when projection indicates a declared record
- preserve current version label and preview projection

This keeps `getRecordDeclarationFromNode(...)` working without any extra UI branching.

## Files

- `ecm-frontend/src/services/savedSearchService.ts`
- `ecm-frontend/src/store/slices/nodeSlice.ts`
- `ecm-frontend/src/store/slices/nodeSlice.test.ts`

## Acceptance

- a saved-search execution result that includes RM projection keeps `record / declared* / recordCategory*`
- `SearchResults` reached through `?savedSearchId=` can render the same `RecordStatusChip` as normal search and advanced search
- no backend change, migration, or new endpoint is introduced
