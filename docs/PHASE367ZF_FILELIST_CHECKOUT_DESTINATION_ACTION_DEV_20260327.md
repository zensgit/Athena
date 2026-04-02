# Phase367ZF FileList Checkout Destination Action

## Goal

Add the checkout destination shortcut to the browse context menu so operators can jump directly from a checked-out document in `FileList` to its check-in target folder.

## Design

- Keep the change frontend-only.
- Do not preload checkout graph data for every row.
- Use lazy fetch on demand:
  - when the user clicks `Open Check-In Target`
  - call `nodeService.getNodeRelationCheckoutGraph(node.id)`
  - navigate to `destinationNode.id` if present

## Why This Slice

- It extends the destination workflow to the main browse surface.
- Lazy fetch avoids adding cost to normal folder rendering.
- It fits the existing checkout context menu cluster:
  - `Check Out`
  - `Check In`
  - `Cancel Checkout`
  - `Open Check-In Target`

## UI Change

- `FileList` context menu for checked-out documents now shows `Open Check-In Target`.
- On success:
  - navigate to `/browse/{destinationNode.id}`
- If no destination is present:
  - show an informational toast

## Benchmark Impact

- This is still built on a virtual checkout graph, not a persisted working-copy object.
- It does improve operator detail by making the destination shortcut available from history, search, preview, and browse surfaces.
