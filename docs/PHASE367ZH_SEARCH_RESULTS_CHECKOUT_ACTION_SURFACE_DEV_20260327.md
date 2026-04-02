# Phase367ZH Search Results Checkout Action Surface

## Goal

Upgrade the regular `SearchResults` page from a passive checkout destination shortcut into a fuller checkout action surface.

## Design

- Reuse the existing action rules from advanced search:
  - `getAdvancedSearchCheckoutActionReason`
  - `getAdvancedSearchCheckInActionReason`
  - `getAdvancedSearchCancelCheckoutActionReason`
- Reuse the existing checkout chip model from advanced search:
  - `getSearchResultCheckoutChip`
- Keep the change frontend-only.
- Refresh the current search after each successful checkout action by calling the existing `runSearch(lastSearchCriteria)`.

## UI Changes

- Result cards now show a checkout status chip in the header.
- Result card actions now support:
  - `Check Out`
  - `Check In`
  - `Cancel Checkout`
  - existing `Open Check-In Target`
- Add a lightweight `Check In` dialog:
  - optional new version file
  - version comment
  - major version toggle
  - keep checked out toggle

## Why This Slice

- This closes the actionable-surface gap Claude pointed out on the normal search page.
- It is more valuable than adding more metadata because it lets operators complete the checkout lifecycle directly from search results.
- It reuses already proven UI rules and backend endpoints rather than introducing a new contract.

## Benchmark Impact

- This still does not create a persisted working-copy entity.
- It does make Athena stronger on operator workflow detail by giving normal search the same checkout lifecycle affordances already present in advanced search and browse.
