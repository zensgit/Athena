# Phase 1 P87 Design: Advanced Search Aspects/Custom Properties Saved-Search Parity

## Background
- Advanced Search dialog already supports:
  - `Aspects` selection
  - `Custom Properties` key/value entries
- Existing saved-search integration only preserved a subset of filters (mime type, date, tags, preview status, folder scope, etc.).
- Result: loading a saved search into Advanced Search could lose `Aspects` and `Custom Properties`.

## Goal
- Ensure saved-search flow is parity-complete for:
  - Save from Advanced Search -> payload includes `aspects` and `properties`
  - Load saved search -> dialog prefill restores `aspects` checkboxes and custom-property chips

## Scope
- Frontend only:
  - `SearchDialog` save payload and prefill hydration
  - Saved-search mapping utility
  - Saved-search load action prefill payload
  - Targeted E2E + unit test coverage

## Implementation
1. Extend prefill shape
- File: `ecm-frontend/src/store/slices/uiSlice.ts`
- Added to `SearchPrefill`:
  - `aspects?: string[]`
  - `properties?: Record<string, any>`

2. Normalize/mapping from saved-search payload
- File: `ecm-frontend/src/utils/savedSearchUtils.ts`
- Added property normalizer and mapped:
  - `filters.aspects -> SearchCriteria.aspects`
  - `filters.properties (fallback queryParams.properties) -> SearchCriteria.properties`

3. Save payload includes aspects/properties
- File: `ecm-frontend/src/components/search/SearchDialog.tsx`
- In `buildSavedSearchQueryParams()`:
  - write `filters.aspects` when selected
  - write `filters.properties` from non-empty custom-property entries

4. Prefill hydration restores aspects/properties
- File: `ecm-frontend/src/components/search/SearchDialog.tsx`
- On prefill load:
  - set `searchCriteria.aspects` and `searchCriteria.properties`
  - restore `customProperties` chips from prefill properties

5. Load-to-Advanced action forwards aspects/properties
- File: `ecm-frontend/src/pages/SavedSearchesPage.tsx`
- `setSearchPrefill(...)` now includes:
  - `aspects`
  - `properties`

6. Keep advanced-search request payload aligned
- File: `ecm-frontend/src/services/nodeService.ts`
- Include `aspects` and `properties` in advanced filters when present.

## Non-goals
- No backend schema change in this phase.
- No new backend filtering capability for aspects/properties in Elasticsearch query planner.
- This phase is focused on saved-search persistence + prefill consistency.

## Risk and compatibility
- Backward compatible:
  - old saved searches without these fields still load.
  - added fields are optional.
- Unknown filter keys remain safe for persistence via existing saved-search JSON model.

