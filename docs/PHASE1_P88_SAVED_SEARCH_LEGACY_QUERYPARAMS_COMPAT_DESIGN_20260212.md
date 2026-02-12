# Phase 1 P88 Design: Saved Search Legacy QueryParams Compatibility

## Background
- We already restored modern saved-search parity (`queryParams.filters.*`) for Advanced Search prefill.
- A remaining gap exists for legacy saved searches that store criteria at the top level of `queryParams` (for example `mimeTypes`, `aspects`, `properties`, `q`) instead of nested under `filters`.
- Symptom: load-to-dialog can appear partially empty even though criteria exists in saved payload.

## Goal
- Make saved-search loading backward compatible with both shapes:
  - Modern: `queryParams.filters.<field>`
  - Legacy: `queryParams.<field>`

## Scope
- Frontend mapping + tests only.
- No backend schema or migration.

## Implementation
1. Dual-source field resolution in mapper
- File: `ecm-frontend/src/utils/savedSearchUtils.ts`
- Added `getFilterValue(key)`:
  - prefer `filters[key]`
  - fallback to `queryParams[key]`
- Added parsing improvements:
  - query text: `queryParams.query` fallback `queryParams.q`
  - numeric coercion for `minSize/maxSize` string inputs
  - `createdBy` fallback from `createdByList[0]`
  - `contentType` fallback from first `mimeTypes` item
  - full mapping for `aspects/properties/tags/categories/correspondents/path/folderId/includeChildren/previewStatuses/date*`

2. Test coverage for legacy payload
- File: `ecm-frontend/src/utils/savedSearchUtils.test.ts`
- Added unit case for top-level queryParams shape.

3. E2E coverage for legacy load-to-dialog
- File: `ecm-frontend/e2e/saved-search-load-prefill.spec.ts`
- Added scenario with mocked legacy saved search payload and assertions for:
  - query prefill
  - folder scope + includeChildren flag
  - aspects restored
  - custom properties restored

## Risks
- Low risk: compatibility-only read path change.
- Existing modern shape behavior preserved because `filters.*` remains first-priority source.

