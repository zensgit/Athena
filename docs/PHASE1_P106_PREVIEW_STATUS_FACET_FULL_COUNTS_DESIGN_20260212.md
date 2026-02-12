# Phase 1 P106 Design: Preview Status Facet Counts From Full-Result Aggregations

Date: 2026-02-12

## Background

- Advanced Search and Search Results display a **Preview Status** facet:
  - `READY`, `PROCESSING`, `QUEUED`, `FAILED`, `UNSUPPORTED`, `PENDING`
- Previously, preview status facet counts could be computed from the **current page** of hits.
  - This breaks pagination: the UI may show `Found 12 results` but `Unsupported (10)` when page size is 10.
- Preview status semantics are not a pure terms aggregation:
  - `PENDING` means `previewStatus` is **missing** in the index.
  - `UNSUPPORTED` should include both canonical `previewStatus=UNSUPPORTED` and legacy documents that are effectively unsupported (unsupported mime / known unsupported failure phrases).
  - `FAILED` should exclude unsupported-only failures so that `FAILED` matches the UI effective status.

## Goal

- Return preview status facet counts that represent the **entire result set** (not just the current page).
- Keep facet bucket semantics aligned with:
  - backend preview status filtering semantics, and
  - UI effective status behavior.

## Scope

Backend:
- `ecm-core/src/main/java/com/ecm/core/search/PreviewStatusFilterHelper.java`
- `ecm-core/src/main/java/com/ecm/core/search/FacetedSearchService.java`

Frontend:
- `ecm-frontend/src/pages/AdvancedSearchPage.tsx`
- `ecm-frontend/src/pages/SearchResults.tsx`

Tests:
- `ecm-frontend/e2e/search-preview-status.spec.ts`
- `ecm-core/src/test/java/com/ecm/core/search/PreviewStatusFilterHelperTest.java`

## API / Contract

`POST /api/v1/search/faceted` supports requesting `facetFields=["previewStatus", ...]`.

When requested, the server returns:

- `facets.previewStatus` with stable buckets (including 0-count buckets) in order:
  - `READY`, `PROCESSING`, `QUEUED`, `FAILED`, `UNSUPPORTED`, `PENDING`

## Implementation

### Backend: keyed filters aggregation (not terms)

`previewStatus` is implemented as a **filters aggregation** because a terms aggregation cannot model:

- `PENDING` (missing field)
- `UNSUPPORTED` (union of canonical unsupported + legacy failed-with-unsupported-signals)
- `FAILED` (failed excluding unsupported signals)

Implementation details:

1. `previewStatus` added to `DEFAULT_FACET_FIELDS`.
2. `FacetedSearchService` special-cases `previewStatus`:
   - `buildPreviewStatusFacetAggregation()` builds a keyed filters aggregation with one filter per bucket key.
3. `PreviewStatusFilterHelper.buildFacetBucketQuery(...)` builds a per-bucket query:
   - Always filters `nodeType=DOCUMENT` (mirror UI behavior).
   - `PENDING`: `must_not exists(previewStatus)`.
   - `UNSUPPORTED`: `should previewStatus=UNSUPPORTED` OR `should (previewStatus=FAILED AND unsupported signals)`; `minimum_should_match=1`.
   - `FAILED`: `previewStatus=FAILED` AND `must_not unsupported signals` (so stale/legacy unsupported failures do not inflate FAILED).
   - All other buckets are a direct `term previewStatus=<bucket>`.
4. `extractPreviewStatusFacet(...)` reads the keyed filter buckets and always emits all buckets (0-count allowed).

### Frontend: prefer aggregation-derived facets (fallback retained)

1. Advanced Search:
   - `AdvancedSearchPage` requests `previewStatus` in `facetFields`.
   - If `facets.previewStatus` is present, it uses that for chip counts (full-result).
   - Otherwise falls back to the previous per-page reduction.

2. Search Results:
   - `SearchResults` prefers `searchFacets.previewStatus` only when:
     - no other non-preview filters are applied, and
     - no preview-status filter is already applied.
   - Otherwise it falls back to per-page counts to avoid misleading scope changes when the UI is already filtered.

## Expected Outcome

- Advanced Search preview status chips show **accurate full-result counts** across pagination.
- UNSUPPORTED vs FAILED counts match the UI effective status and backend filtering semantics.
- E2E asserts `Found N results` and `Unsupported (N)` align.

