# Phase367ZA Version History Checkout Graph Consumption

## Goal

Make `VersionHistoryDialog` consume the new virtual checkout working-copy graph so the dialog stops depending only on the older `checkout relation + checkout-lineage` pairing.

## Design

- Keep existing compatibility paths:
  - `GET /api/v1/nodes/{nodeId}/relations/checkout`
  - `GET /api/v1/documents/{documentId}/checkout-lineage`
- Add `GET /api/v1/nodes/{nodeId}/relations/checkout-graph` as an additional source in `VersionHistoryDialog`.
- Use graph data as the preferred source for:
  - baseline version
  - current version
  - active working-copy label
  - keep-checked-out capability
- Retain old relation/lineage data as fallback so existing behavior survives if the new graph endpoint is unavailable.

## UI Changes

- The active checkout warning banner now prefers graph-derived lineage labels.
- The banner also surfaces the virtual working-copy node label.
- `Compare checkout lineage` and `Load checkout lineage versions` now operate from graph-first effective lineage anchors instead of relation-only anchors.
- This removes another source of split semantics between:
  - version markers in the loaded table
  - direct lineage API fallback
  - checkout relation summary

## Why This Slice

- Low conflict: one dialog, no public API removals, no entity changes.
- High leverage: `VersionHistoryDialog` is the densest checkout/source operator surface in the UI.
- It turns the new graph API into a real source of UI truth instead of leaving it as a sidecar endpoint.

## Benchmark Impact

- Alfresco’s checkout model is still richer when a true persisted working copy exists.
- This phase makes Athena’s operator-facing lineage flow more coherent by centering it around an explicit graph contract.
- It is an incremental step toward fuller `working-copy/source graph` semantics without destabilizing current behavior.
