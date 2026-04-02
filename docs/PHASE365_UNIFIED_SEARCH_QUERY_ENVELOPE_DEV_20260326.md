# Phase 365: Unified Search Query Envelope

## Goal

Introduce a single search envelope contract for Athena so results, request diagnostics, stats, and pivot analysis can be requested through one stable API without breaking existing advanced-search endpoints.

## Delivered

- Added `POST /api/v1/search/query` in `SearchController`.
- Added `SearchQueryEnvelopeRequest`, `SearchQueryEnvelopeRequestEcho`, and `SearchQueryEnvelopeResponse`.
- Added include-based composition for:
  - `results`
  - `context`
  - `stats`
  - `pivot`
- Added optional `includeRequest=true` request echo, aligned with Alfresco `includeRequest` intent.
- Refactored existing advanced-search `context/stats/pivot` handlers to reuse shared internal builders instead of maintaining separate response assembly logic.

## Request Shape

The new envelope reuses Athena-native request fields instead of introducing a large incompatible model:

- `query`
- `filters`
- `sortBy`
- `sortDirection`
- `pageable`
- `highlightEnabled`
- `facets`
- `includeRequest`
- `include`

This keeps compatibility with current frontend request structures while establishing a single future-facing entrypoint.

## Response Shape

- `request`
  - Returned only when `includeRequest=true`
  - Echoes normalized query, page/size, filters, facets, include sections
- `results`
  - Present when `include` contains `results`
- `context`
  - Present when `include` contains `context`
- `stats`
  - Present when `include` contains `stats`
- `pivot`
  - Present when `include` contains `pivot`

## Design Notes

- The first slice does not remove or break:
  - `/api/v1/search/advanced`
  - `/api/v1/search/advanced/context`
  - `/api/v1/search/advanced/stats`
  - `/api/v1/search/advanced/stats/pivot`
- Existing endpoints now behave as facades over the same shared response-building helpers used by `/api/v1/search/query`.
- This mirrors the direction of Alfresco `SearchQuery + SearchRequestContext`, but stays intentionally smaller and Athena-native for the first cut.

## Why This Matters

Before this phase, Athena search semantics were spread across several parallel endpoints with duplicated normalization logic. After this phase:

- the request contract becomes composable,
- request echo becomes first-class,
- old endpoints remain stable,
- and the frontend has a single future migration target.

## Follow-up

- Move frontend advanced search consumers onto `/api/v1/search/query`.
- Expand the envelope toward richer sort/include semantics instead of page-specific flags.
- Fold preview dry-run/export consumers into the same normalized request contract.
