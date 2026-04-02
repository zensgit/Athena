# Phase367O Advanced Search Result Checkout Actions

## Goal

Turn `AdvancedSearchPage` result rows from passive status display into lightweight checkout-control surfaces by adding `Check Out` and `Cancel Checkout` actions.

## Scope

Frontend-only:

- add result-row checkout action helpers
- add `Check Out` button for eligible document results
- add `Cancel Checkout` button for checked-out document results
- refresh current search page after successful action

## Why This Slice

Athena already had:

- checkout filters in advanced search
- checkout and lock chips on result rows
- browse-layer checkout actions

But search results were still passive. This slice closes that gap without introducing a heavy upload dialog or changing backend APIs.

## Design

### Action rules

`Check Out`

- visible for document results that are not checked out
- disabled when a foreign lock blocks checkout

`Cancel Checkout`

- visible for checked-out document results
- disabled for foreign checkout unless current user is admin

### Refresh strategy

On success, result actions call `handleSearch(page)` so the current search state, pagination, filters, and result chips refresh immediately.

### Helper boundary

`advancedSearchActionUtils` keeps the result-row action rules separate from `FileList` helpers, avoiding type coupling between `SearchResult` and `Node`.

## Files

- `ecm-frontend/src/pages/AdvancedSearchPage.tsx`
- `ecm-frontend/src/utils/advancedSearchActionUtils.ts`
- `ecm-frontend/src/utils/advancedSearchActionUtils.test.ts`

## Risk

- this phase still does not add a search-result `Check In` upload flow
- action availability remains based on UI role + local row state, not full caller-relative checkout-info payload
