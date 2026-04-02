# Phase367ZE Document Preview Checkout Destination Action

## Goal

Add the checkout destination shortcut to `DocumentPreview` so the operator can jump directly from an active checkout warning into the check-in target folder.

## Design

- Reuse the existing `checkout-graph` contract from `nodeService`.
- Keep the change frontend-only.
- Extend `DocumentPreview` node-details loading to fetch:
  - node details
  - lock info
  - checkout info
  - checkout graph
- Upgrade the existing checkout warning `Alert` with:
  - `Open check-in target` action button
  - appended destination label in the alert body

## Why This Slice

- `DocumentPreview` is the main detail surface where operators stop to inspect a checked-out document.
- The shortcut fits naturally into the existing checkout alert, so the UI cost is very low.
- This is a better next step than widening search/browse again because it closes the destination workflow on a detail surface, not just on list or history surfaces.

## UI Change

- When `checkoutGraph.destinationNode.id` exists, the checkout alert now shows:
  - `Open check-in target`
  - destination label text
- Clicking the action navigates to `/browse/{destinationNode.id}`.

## Benchmark Impact

- This still does not create a true persisted working-copy object.
- It does improve operator detail by making checkout destination actionable from history, search, and preview surfaces.
