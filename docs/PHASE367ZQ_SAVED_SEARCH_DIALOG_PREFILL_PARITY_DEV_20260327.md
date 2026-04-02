# Phase367ZQ Saved Search Dialog Prefill Parity

## Goal

Close the remaining saved-search-to-search-dialog gap so Athena preserves `lock / checkout` triage criteria when operators load a saved search into `SearchDialog`.

## Design

- Extend `SavedSearchesPage.handleLoadToSearch(...)` so the `setSearchPrefill(...)` payload carries:
  - `locked`
  - `lockedBy`
  - `checkedOut`
  - `checkoutUser`
- Keep the change aligned with the existing replay path from `buildSearchCriteriaFromSavedSearch(...)`.
- Preserve the current UX:
  - load saved search
  - open `SearchDialog`
  - let the operator inspect or refine criteria before running search
- Keep the change frontend-only and scoped to the remaining prefill parity gap.

## Why This Slice

- Athena already converged lock/checkout semantics across ordinary search, advanced search, search dialog, and saved-search execution.
- Without this change, `Load to Search` still dropped the newest triage fields even though execute/replay paths already preserved them.
- This is a high-value consistency fix because operators often use saved searches as editable starting points, not only as one-click execution entries.

## Benchmark Impact

- This does not add a new ECM primitive.
- It does make Athena’s saved-search editing loop more coherent than a system where saved criteria survive execution but disappear when reopened for refinement.
