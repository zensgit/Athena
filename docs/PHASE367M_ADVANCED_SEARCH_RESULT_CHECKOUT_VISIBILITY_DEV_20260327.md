# Phase367M Advanced Search Result Checkout Visibility

## Goal

Complete the first usable checkout-search loop by surfacing checkout state directly in `AdvancedSearchPage` result cards.

## Scope

Frontend-only:

- carry `checkedOut` and `checkoutUser` into `SearchResult`
- add a tiny checkout-chip helper for advanced-search result cards
- render checkout chip in search results next to MIME/size/score chips

## Why This Slice

`Phase367L` added checkout filters to advanced search, but without a result-level indicator operators still had to infer whether the returned set actually contains checked-out documents. This slice closes that gap with minimal UI surface area.

## Design

- `SearchResult` now includes `checkedOut` and `checkoutUser`
- both `handleSearch(...)` and `mapNodeToSearchResult(...)` preserve those fields
- `advancedSearchCheckoutUtils` formats the result-row chip:
  - `Checked out by alice`
  - fallback `Checked out`
- result cards show the chip only when `checkedOut === true`

This keeps the implementation low-conflict and avoids entangling the result list with richer checkout actions.

## Files

- `ecm-frontend/src/pages/AdvancedSearchPage.tsx`
- `ecm-frontend/src/utils/advancedSearchCheckoutUtils.ts`
- `ecm-frontend/src/utils/advancedSearchCheckoutUtils.test.ts`

## Risk

- this phase adds visibility only; it does not yet add checkout actions inside advanced-search result rows
- result cards still do not surface caller-relative ownership semantics beyond the checkout owner label
