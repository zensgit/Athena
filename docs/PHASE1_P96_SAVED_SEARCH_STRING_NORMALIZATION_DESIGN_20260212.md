# Phase 1 P96 Design: Saved Search String Normalization for Legacy Payloads

## Background
- Some imported/legacy saved searches encode list and boolean filters as strings, for example:
  - `tags: "a,b"`
  - `creators: "admin"`
  - `includeChildren: "false"`
  - `queryString` instead of `query` / `q`
- Without normalization, dialog prefill may drop criteria silently.

## Goal
- Normalize string-form legacy fields into stable `SearchCriteria` output so Advanced Search prefill remains consistent.

## Scope
- Frontend parser only:
  - `ecm-frontend/src/utils/savedSearchUtils.ts`
- Tests:
  - `ecm-frontend/src/utils/savedSearchUtils.test.ts`

## Implementation
1. List normalization expansion
- Updated `normalizeList` to accept comma-separated strings in addition to arrays.
- Applied uniformly to existing list-based fields (`mimeTypes`, `tags`, `categories`, `correspondents`, `createdByList/creators`).

2. Boolean normalization
- Added `asBoolean` helper to parse:
  - `true` / `false` booleans
  - `"true"` / `"false"` strings
- Used for `includeChildren`.

3. Query alias fallback
- Search text now resolves in order:
  - `query`
  - `q`
  - `queryString`

4. Unit coverage
- Added explicit case for:
  - `queryString`
  - comma-separated list strings
  - `includeChildren: "false"`

## Expected Outcome
- Saved-search load path is resilient to mixed historical payload shapes and import sources.
- Users see the same prefilled criteria regardless of list/boolean serialization style.
