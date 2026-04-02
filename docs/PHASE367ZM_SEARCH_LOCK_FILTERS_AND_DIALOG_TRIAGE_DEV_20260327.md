# Phase367ZM Search Lock Filters And Dialog Triage

## Goal

Extend Athena search triage from checkout-aware to lock-aware by making `locked / lockedBy` first-class search filters and exposing them in `SearchDialog`.

## Design

- Extend the shared search filter contract with:
  - `locked`
  - `lockedBy`
- Keep filter semantics symmetric with the existing checkout filters:
  - boolean state filter
  - owner text filter
- Apply the new filters consistently across:
  - `FullTextSearchService`
  - `FacetedSearchService`
  - `SearchController` filter diagnostics / copy helpers
  - frontend `SearchCriteria`
  - frontend `nodeService` search filter builder
  - `SearchDialog`
- Preserve the same prefill and saved-search flow already used for checkout triage.

## UI Changes

- `SearchDialog` basic section now includes:
  - `Lock State`
  - `Lock Owner`
- Active criteria chips now show active lock filters.
- Searches issued from the dialog can now narrow by locked documents and lock owner.

## Why This Slice

- Athena already surfaces lock chips and action gating in browse, preview, advanced search results, and ordinary search results.
- Without true lock filters, operators still had to visually scan rather than ask the search layer to narrow the set.
- This is a higher-leverage slice than more UI polish because it upgrades the underlying search contract.

## Benchmark Impact

- This still does not add a richer persisted lock type model.
- It does move Athena closer to a true operator-grade ECM triage console by making lock state searchable, not just visible.
