# Phase367ZP Saved Search Lock Checkout Replay

## Goal

Close the remaining saved-search replay gap so Athena preserves `lock / checkout` triage criteria when operators run saved searches.

## Design

- Extend `buildSearchCriteriaFromSavedSearch(...)` so it maps the shared advanced-search state back into:
  - `locked`
  - `lockedBy`
  - `checkedOut`
  - `checkoutUser`
- Keep the mapping symmetric with the existing advanced-search state helpers.
- Extend saved-search execution result typing so lock/checkout node fields are not discarded when the backend returns them.
- Keep the change frontend-only.

## Why This Slice

- Search surfaces are already converging on shared lock/checkout triage semantics.
- Without this change, saved searches remained a semantic gap: operators could save lock-aware searches, but replay into ordinary search criteria was incomplete.
- This is a high-value consistency slice because it affects pinned searches, dashboard shortcuts, and saved-search execution.

## Benchmark Impact

- This does not add new ECM primitives.
- It does make Athena’s saved-search workflow more coherent than a system where advanced filters are only partially replayable.
