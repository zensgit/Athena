# Phase367ZK Search Results Checkout Quick Filters

## Goal

Close the remaining ordinary-search filtering gap by bringing checkout quick filters into `SearchResults`.

## Design

- Reuse the existing search contract that already supports:
  - `checkedOut`
  - `checkoutUser`
- Keep the change frontend-only.
- Model ordinary search checkout state as a single-select triage control:
  - `All documents`
  - `Checked out`
  - `Available`
- Add a freeform `Checkout user` text input for targeted operator lookups.
- Keep the filters inside the existing facets rail and active-filter chip strip.
- When jumping from ordinary search to advanced search, pass `checkedOut` and `checkoutUser` through `SearchPrefill`.

## UI Changes

- `SearchResults` facets rail now includes a `Checkout` section.
- Operators can filter ordinary search results by checkout state and checkout user.
- Active ordinary-search filter chips now include checkout state and checkout user.

## Why This Slice

- Advanced search already supports checkout filters; ordinary search did not.
- This is lower conflict than adding new backend filters because the request contract already exists.
- It improves day-to-day operator triage by reducing the need to leave ordinary search for common checkout narrowing.

## Benchmark Impact

- This does not add a richer working-copy model.
- It does improve operator detail by making ordinary search closer to an ECM triage console rather than a plain text search page.
