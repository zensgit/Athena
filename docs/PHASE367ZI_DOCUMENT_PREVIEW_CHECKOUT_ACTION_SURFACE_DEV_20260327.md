# Phase367ZI Document Preview Checkout Action Surface

## Goal

Upgrade `DocumentPreview` from a checkout status surface into a full checkout lifecycle surface.

## Design

- Reuse caller-relative `checkoutInfo` affordances:
  - `canCheckout`
  - `canCheckIn`
  - `canCancelCheckout`
  - `canKeepCheckedOut`
- Reuse existing frontend service calls:
  - `checkoutDocument`
  - `checkinDocument`
  - `cancelCheckoutDocument`
- Keep the change frontend-only.
- Refresh preview-side metadata with `loadNodeDetails()` after each successful action.

## UI Changes

- The existing checkout warning alert now supports:
  - `Check Out`
  - `Check In`
  - `Cancel Checkout`
  - `Open check-in target`
- Add a lightweight `Check In` dialog:
  - optional new version file
  - version comment
  - major version toggle
  - keep checked out toggle

## Why This Slice

- `DocumentPreview` is the deepest detail surface for a single document.
- It already had checkout chip + alert + destination shortcut, so the action surface fits naturally there.
- This is more valuable than adding more graph metadata because it lets operators complete the lifecycle without leaving preview.

## Benchmark Impact

- This still does not create a persisted working-copy entity.
- It does improve operator flow by making preview a full checkout lifecycle surface rather than a read-only status surface.
