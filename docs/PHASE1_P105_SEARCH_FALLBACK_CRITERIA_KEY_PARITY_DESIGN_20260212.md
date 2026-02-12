# Phase 1 P105 Design: Search Fallback Criteria-Key Parity (Search + Advanced Search)

Date: 2026-02-12

## Problem

Athena provides a "fallback results" UX when search returns `0` results but the index is likely still refreshing:
- show an info banner ("Search results may still be indexing")
- optionally show the previous results while auto-retrying the search
- for high-precision/exact queries (e.g., CAD/binary filenames), **suppress** showing stale results by default, but allow an explicit opt-in ("Show previous results").

However, fallback evaluation was gated by a `criteriaKey` match between:
- the current search criteria, and
- the last known non-empty results snapshot ("fallback results").

Both pages included the query string in this key:
- `AdvancedSearchPage` included `query`
- `SearchResults` included `name`

That makes fallback impossible whenever the user changes the query (the common case), even if all other criteria are unchanged.

Observed symptoms:
- For an exact file query (e.g., `*.bin`), the "suppressed fallback" notice never appears, so the user only sees "No results found."
- Playwright E2E for fallback governance fails because the notice/actions are not rendered.

## Goals

1. Fallback evaluation should be based on **filters/scope** parity, not the query text.
2. Keep the safer default for exact/high-precision queries:
   - hide stale results by default
   - allow explicit opt-in reveal
3. Keep parity between Search and Advanced Search behaviors.

## Non-goals

- Change backend search semantics or indexing behavior.
- Implement new query languages or "similar results" features.

## Design

### 1) Criteria key excludes query text

Both pages compute a JSON string key for "criteria equality". We align behavior by **excluding the query** from this key:

- `SearchResults`:
  - remove `name` from `buildFallbackCriteriaKey()`
  - keep `fallbackLabel` for messaging so users know what results they are seeing

- `AdvancedSearchPage`:
  - remove `query` from `buildFallbackCriteriaKey()`
  - keep `fallbackLabel` for messaging

This enables fallback evaluation when:
- the current search returns zero results, and
- the user kept the same filter/scope constraints, and
- there are previous results captured for that filter/scope

### 2) Safety: suppress stale results for exact/high-precision queries

We keep the safety rule:
- for high-precision/exact queries (e.g., `*.bin`, CAD formats), the UI shows a notice but hides the stale fallback results by default.
- users can explicitly click "Show previous results" to reveal.

This relies on:
- `shouldSuppressStaleFallbackForQuery(query)` in `ecm-frontend/src/utils/searchFallbackUtils.ts`

## Implementation Notes

### Changed files

- Frontend
  - `ecm-frontend/src/pages/SearchResults.tsx`
    - remove `name` from fallback criteria key JSON
  - `ecm-frontend/src/pages/AdvancedSearchPage.tsx`
    - remove `query` from fallback criteria key JSON

### Behavior summary

- When a new query returns empty but filters match:
  - fallback evaluation triggers
  - if query looks high-precision/exact:
    - show notice: previous results hidden (with Retry + Show previous results)
  - otherwise:
    - show fallback results (with Retry + Hide previous results)

## Risks and Mitigations

- Risk: For non-exact single-token queries, fallback could show previous results even if the user changed the query.
  - Mitigation: banner text still indicates these are "previous results" (and when applicable, includes the previous query label).
  - Mitigation: exact/high-precision queries are suppressed by default to prevent "wrong file" confusion.

