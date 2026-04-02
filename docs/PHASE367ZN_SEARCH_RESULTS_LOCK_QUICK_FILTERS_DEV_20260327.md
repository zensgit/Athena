# Phase367ZN Search Results Lock Quick Filters

## Goal

Bring ordinary search up to the same lock triage level as the shared search contract and dialog by adding lock quick filters to `SearchResults`.

## Design

- Reuse the new shared search filter contract:
  - `locked`
  - `lockedBy`
- Keep the ordinary-search lock controls parallel to the existing checkout controls:
  - state selector
  - owner text input
- Extend ordinary-search state synchronization to include:
  - active filter chips
  - `lastSearchCriteria` hydration
  - fallback criteria key generation
  - prefill handoff into `SearchDialog`

## UI Changes

- `SearchResults` facets rail now includes a `Lock` section.
- Operators can narrow ordinary search results by lock state and lock owner.
- Active filter chips now show lock state and lock owner.
- Jumping from ordinary search into the advanced dialog preserves active lock filters.

## Why This Slice

- The search contract and `SearchDialog` are already lock-aware; ordinary search still lagged behind.
- This is a low-conflict frontend slice that closes a real operator gap without widening backend APIs.
- It keeps the search surfaces consistent, which matters more now than one-off UI polish.

## Benchmark Impact

- This does not finish the richer lock model work.
- It does make Athena’s ordinary-search triage surface materially stronger by allowing operators to search for lock state instead of only spotting lock chips visually.
