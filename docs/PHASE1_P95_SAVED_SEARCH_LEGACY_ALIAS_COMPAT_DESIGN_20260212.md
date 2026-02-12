# Phase 1 P95 Design: Saved Search Legacy Alias Compatibility

## Background
- `SavedSearch` data has multiple historical shapes in `queryParams`.
- Existing compatibility already covered top-level legacy fields (`q`, `mimeTypes`, `createdByList`, etc.).
- A remaining gap existed for alias-style fields used by older payloads:
  - `pathPrefix` (instead of `path`)
  - `createdFrom`/`createdTo` (instead of `dateFrom`/`dateTo`)
  - `previewStatus` as single string (instead of `previewStatuses` array)
  - `creators` (instead of `createdByList`)

## Goal
- Ensure `buildSearchCriteriaFromSavedSearch(...)` normalizes both modern and legacy alias shapes into one stable `SearchCriteria`.

## Scope
- Frontend only.
- Parser: `ecm-frontend/src/utils/savedSearchUtils.ts`
- Regression coverage:
  - Unit test in `savedSearchUtils.test.ts`
  - E2E saved-search load flow in `saved-search-load-prefill.spec.ts`

## Implementation
1. Key resolution hardening
- Reworked filter lookup helper to support multi-key fallback in priority order:
  - `filters` first, then `queryParams`.

2. Alias mapping additions
- `createdByList`: resolve from `createdByList` or `creators`.
- `createdFrom`/`createdTo`: resolve from `createdFrom`/`createdTo`, fallback to `dateFrom`/`dateTo`.
- `path`: resolve from `path`, fallback to `pathPrefix`.
- `previewStatuses`: resolve from `previewStatuses` or `previewStatus`.
  - Added status normalizer supporting:
    - array values
    - comma-separated string values
  - Values are normalized to uppercase tokens (`FAILED`, `PROCESSING`, etc.).

3. Regression tests
- Unit:
  - Added alias coverage case for `pathPrefix`, `createdFrom/createdTo`, `previewStatus`, `creators`.
- E2E:
  - Added saved-search load case using legacy alias payload and asserted advanced dialog prefill/summary parity.

## Expected Outcome
- Loading old saved searches no longer drops criteria due to alias drift.
- Advanced Search dialog prefill remains deterministic across mixed payload generations.
