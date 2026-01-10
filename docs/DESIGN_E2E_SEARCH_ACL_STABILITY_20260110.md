# Design: E2E Search ACL Stability (2026-01-10)

## Context
- The viewer search ACL test can inherit prior browse results from the shared node store.
- When a denied document yields zero search results, the UI may still show prior nodes while the index refreshes or before search results replace state.

## Decision
- Make the test wait for the search request for the exact query and assert the denied filename is absent in both the API response and the UI.
- Remove reliance on the "No results found" empty state, which is not guaranteed when fallback results are displayed.

## Implementation
- Update `e2e/search-view.spec.ts` to await `/api/v1/search?q=<filename>` and validate the response content.
- Keep UI assertion that the denied filename never appears in result cards.
