# Design: Search Accurate Totals + Facets Via Aggregations (2026-02-10)

## Context
Athena's search UI relies on:

- accurate total hit counts (for pagination and "Showing X of Y results")
- facet counts that represent the *whole* result set, not just the current page

Elasticsearch defaults can return non-exact totals (for performance) and facet fallback logic can accidentally compute counts from only the returned hits.

## Problem
1. Total hit counts may be approximate or capped depending on Elasticsearch defaults. This can lead to incorrect `totalElements` in API responses and confusing UI paging.
2. Facet counts can be misleading if computed from only the returned hits rather than Elasticsearch aggregations.

## Goals
- Always request accurate total hits from Elasticsearch for user-facing search endpoints.
- Prefer aggregation-derived facets when available; fall back only when aggregations are missing.

## Non-Goals
- Change analyzers, mapping, or scoring behavior.
- Rework the UI facet UX in this change set.

## Design
### Accurate totals
Enable `track_total_hits` for the Elasticsearch queries used by:

- `FullTextSearchService`
- `FacetedSearchService`

This ensures the returned total hit count is exact (subject to ES behavior for deleted docs, refresh, etc.).

### Facets via aggregations (preferred)
When Elasticsearch aggregations are returned, build facets from aggregation buckets to reflect the full result set.

Fallback behavior:

- If aggregations are missing/empty (unexpected cluster config, partial response, etc.), fall back to the previous in-memory facet computation.

## Implementation Notes
### Files
- `ecm-core/src/main/java/com/ecm/core/search/FullTextSearchService.java`
- `ecm-core/src/main/java/com/ecm/core/search/FacetedSearchService.java`

### Key Changes
- Add `withTrackTotalHits(true)` to relevant search requests.
- Prefer `buildFacetsFromAggregations(...)` results over in-memory facets when aggregations exist.

## Risks / Tradeoffs
- `track_total_hits=true` can increase cost for very large result sets. For Phase 1 P0, correctness is prioritized for admin / power-user workflows.

## Validation
See `docs/VERIFICATION_PHASE1_P0_20260210.md`.

