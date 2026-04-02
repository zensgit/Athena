# Phase367ZO Advanced Search Lock Filter Convergence

## Goal

Make `AdvancedSearchPage` fully lock-aware so it converges with the now lock-aware shared search contract, `SearchDialog`, and ordinary search.

## Design

- Extend `AdvancedSearchCriteriaState` with:
  - `lockState`
  - `lockOwner`
- Thread the new lock criteria through the same channels already used for checkout:
  - URL parsing and serialization
  - saved-search template restore
  - request building
  - fallback criteria key
  - preview batch search payloads
- Keep the UI pattern parallel to checkout:
  - one state selector
  - one owner input
- Update advanced-search URL prefill helpers so lock criteria can flow back into the shared search dialog.

## UI Changes

- `AdvancedSearchPage` sidebar now includes `Lock` filters:
  - `All documents`
  - `Locked`
  - `Unlocked only`
  - `Lock owner`
- Lock filters now survive URL round trips and template restore.
- Preview batch operations now respect active lock filters when searching the matched set.

## Why This Slice

- Search dialog and ordinary search are already lock-aware after the previous slices.
- Without this change, advanced search remained the only search surface where lock triage was still mostly visual rather than declarative.
- This is the highest-value consistency slice before switching back to heavier model work.

## Benchmark Impact

- This still does not finish a richer persisted lock type or working-copy model.
- It does make Athena’s search stack more coherent than a benchmark implementation that spreads lock semantics unevenly across search surfaces.
