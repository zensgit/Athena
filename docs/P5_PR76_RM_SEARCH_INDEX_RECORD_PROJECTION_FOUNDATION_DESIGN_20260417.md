# P5 PR-76 RM Search Index Record Projection Foundation Design

## Scope

`PR-76` is the first runtime slice accepted from the `P5` intake matrix.

It implements the minimum `RM search/index surfaces` foundation by exposing authoritative record-state projection in browse and search lists without adding a new RM API surface.

## Problem

Before this slice:

- `DocumentPreview` and `RecordsManagementPage` could show rich RM record state
- browse lists only showed a generic `Record` chip when a fallback node path happened to include `rm:record`
- the main browse path dropped `properties`, `aspects`, and `currentVersionLabel` coming from list DTOs
- search results had no RM record projection at all

That meant RM record state was not reliably visible outside preview/admin surfaces.

## Design

### 1. Reuse existing authoritative data

The slice deliberately avoids a new backend endpoint.

- browse/list nodes reuse existing `NodeDto` `properties` and `aspects`
- search results reuse existing indexed node `properties`
- frontend keeps using the existing `RecordStatusChip`

### 2. Additive search-result projection

`SearchResult` now carries additive RM fields derived from already indexed node properties:

- `record`
- `declaredBy`
- `declaredAt`
- `declaredVersionLabel`
- `declarationComment`
- `recordCategoryId`
- `recordCategoryName`
- `recordCategoryPath`
- `currentVersionLabel`

The projection is inferred from existing `rm:*` properties already present in indexed node documents. No new table, migration, or query was introduced.

### 3. Unified frontend declaration helper

Frontend now uses a single `Node -> RecordDeclaration` helper:

- `recordDeclarationUtils.ts`

This keeps browse and search on the same tooltip semantics and avoids re-deriving RM presentation inline in multiple components.

### 4. Thin UI consumption only

UI changes are intentionally narrow:

- `FileList` now feeds real declaration data into `RecordStatusChip`
- `SearchResults` now renders the same RM chip using the additive projection

This slice does not add:

- RM search filters
- RM coverage cards
- new RM-specific evidence pages
- browse-page hydration RPCs

## Files

### Backend

- `ecm-core/src/main/java/com/ecm/core/search/SearchResult.java`
- `ecm-core/src/main/java/com/ecm/core/search/FullTextSearchService.java`
- `ecm-core/src/main/java/com/ecm/core/search/FacetedSearchService.java`
- `ecm-core/src/test/java/com/ecm/core/controller/SearchControllerTest.java`

### Frontend

- `ecm-frontend/src/types/index.ts`
- `ecm-frontend/src/services/nodeService.ts`
- `ecm-frontend/src/components/browser/FileList.tsx`
- `ecm-frontend/src/pages/SearchResults.tsx`
- `ecm-frontend/src/utils/recordDeclarationUtils.ts`
- `ecm-frontend/src/utils/recordDeclarationUtils.test.ts`
- `ecm-frontend/src/services/nodeService.recordProjection.test.ts`

## Non-Goals

- no new RM browse/search endpoint
- no rework of `AdvancedSearchPage`
- no RM coverage indexing changes
- no new acceptance surface beyond existing browse/search lists and `RecordStatusChip`
