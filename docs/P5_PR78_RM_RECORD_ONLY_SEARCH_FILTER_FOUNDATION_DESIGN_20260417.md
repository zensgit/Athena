# P5 PR-78 RM Record-Only Search Filter Foundation Design

## Scope

`PR-78` adds the minimum end-to-end `recordOnly` search filter across the existing search stack.

It keeps the slice deliberately narrow:

- no new backend endpoint
- no new index field
- no migration
- no new RM evidence surface

The goal is to let operators search only declared records by reusing the same authoritative RM projection already shipped in `P5 PR-76` and `P5 PR-77`.

## Problem

Before this slice:

- browse, search, and advanced search could display RM record projection
- users still could not query against that projection
- saved-search/template/url state could silently lose a future `recordOnly` flag if it was not threaded through every existing helper

That meant RM declaration state was visible but not actionable as a first-class search filter.

## Design

### 1. Backend filter semantics reuse RM projection, not a new field

The slice does not introduce a dedicated indexed boolean such as `record=true`.

Instead, backend query builders reuse the already-authoritative RM projection semantics:

- a node is treated as a record when any indexed `rm:*` declaration property exists
- `recordOnly=true` becomes a bool filter that matches any of those projection fields
- `recordOnly=false` becomes the inverse of that same projection query

This keeps query semantics aligned with the record badge/projection logic already exposed in search results.

### 2. Centralize record projection logic in one backend helper

`SearchRecordProjectionHelper` becomes the single search-side helper for:

- `isRecordProjection(...)`
- `buildRecordProjectionQuery()`
- `applyRecordOnlyFilter(...)`

That removes duplicate projection logic from:

- `FullTextSearchService`
- `FacetedSearchService`

and keeps filter semantics and result projection semantics on one path.

### 3. Extend existing search filter plumbing only

The slice threads `recordOnly` through the current search stack:

- `SearchFilters`
- `SearchController`
- `FullTextSearchService`
- `FacetedSearchService`
- frontend search criteria/types
- URL state helpers
- saved-search/template helpers
- search dialog prefill/reset logic
- `SearchResults`
- `AdvancedSearchPage`

The intent is additive propagation, not a new search mode.

### 4. Preserve existing evidence surfaces

Search UI changes are intentionally thin:

- `SearchResults` gets a `Record declarations only` checkbox and active chip
- `AdvancedSearchPage` gets the same checkbox
- saved search / prefill / URL restore keep the flag stable

No new RM admin page, report page, or alternate evidence surface is introduced.

## Files

### Backend

- `ecm-core/src/main/java/com/ecm/core/search/SearchFilters.java`
- `ecm-core/src/main/java/com/ecm/core/search/SearchRecordProjectionHelper.java`
- `ecm-core/src/main/java/com/ecm/core/search/FullTextSearchService.java`
- `ecm-core/src/main/java/com/ecm/core/search/FacetedSearchService.java`
- `ecm-core/src/main/java/com/ecm/core/controller/SearchController.java`
- `ecm-core/src/test/java/com/ecm/core/search/SearchRecordProjectionHelperTest.java`
- `ecm-core/src/test/java/com/ecm/core/controller/SearchControllerTest.java`

### Frontend

- `ecm-frontend/src/types/index.ts`
- `ecm-frontend/src/store/slices/uiSlice.ts`
- `ecm-frontend/src/utils/searchPrefillUtils.ts`
- `ecm-frontend/src/utils/advancedSearchStateUtils.ts`
- `ecm-frontend/src/utils/savedSearchUtils.ts`
- `ecm-frontend/src/components/search/SearchDialog.tsx`
- `ecm-frontend/src/services/nodeService.ts`
- `ecm-frontend/src/pages/SearchResults.tsx`
- `ecm-frontend/src/pages/AdvancedSearchPage.tsx`
- `ecm-frontend/src/services/nodeService.recordProjection.test.ts`
- `ecm-frontend/src/utils/advancedSearchStateUtils.test.ts`
- `ecm-frontend/src/utils/searchPrefillUtils.test.ts`
- `ecm-frontend/src/utils/savedSearchUtils.test.ts`

## Non-Goals

- no new search endpoint
- no new saved-search authoring workflow
- no dedicated indexed `record` boolean field
- no RM-only search page
- no additional RM evidence/audit surface beyond the existing search flow
