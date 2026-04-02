# Phase367ZG Search Results Checkout Destination Action

## Goal

Add the checkout destination shortcut to the regular `SearchResults` page so operators can jump from a checked-out search hit directly to its check-in target folder.

## Design

- Keep the change frontend-only.
- Do not add graph data to the normal search result payload.
- Use lazy fetch on click:
  - call `nodeService.getNodeRelationCheckoutGraph(node.id)`
  - navigate to `destinationNode.id` if present

## Why This Slice

- It closes the gap between advanced search and normal search surfaces.
- It avoids adding per-result fetch overhead during normal search rendering.
- It is more valuable than adding more static status text because it shortens the operator path from “found the checked-out document” to “open the target workspace”.

## UI Change

- Checked-out document cards in `SearchResults` now show:
  - `Open Check-In Target`
- On click:
  - navigate to `/browse/{destinationNode.id}`
- If no destination exists:
  - show informational toast

## Benchmark Impact

- This is still built on the virtual checkout graph, not a persisted working-copy object.
- It improves operator detail by making checkout destination actionable from normal search in addition to advanced search, preview, history, and browse.
