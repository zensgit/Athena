# Phase367ZL Search Dialog Checkout Prefill And Filters

## Goal

Close the remaining ordinary-search to advanced-search gap by making `SearchDialog` understand checkout criteria and consume checkout prefill.

## Design

- Extend `SearchDialog` basic search controls with:
  - `Checkout State`
  - `Checkout User`
- Keep the existing `SearchCriteria` contract and reuse:
  - `checkedOut`
  - `checkoutUser`
- Extend `SearchPrefill` URL/store hydration so checkout state can flow from:
  - ordinary search
  - advanced search URL
  - last search criteria
- Persist the same checkout filters into saved search templates.

## UI Changes

- `SearchDialog` basic section now includes checkout state and checkout user.
- Active criteria chips now include checkout state and checkout user.
- Opening `SearchDialog` from a checkout-filtered ordinary search now preserves those filters.

## Why This Slice

- `SearchResults` already gained checkout quick filters, but the advanced dialog path still dropped them.
- This is a low-conflict frontend-only slice that closes a real workflow gap without widening backend contracts.
- It aligns ordinary search, advanced dialog search, and advanced URL prefill semantics.

## Benchmark Impact

- This does not create a fuller working-copy model.
- It does improve operator detail by keeping checkout triage state consistent across search entry points, which is stronger than a fragmented search UX.
