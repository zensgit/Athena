# Phase367ZT Checkout Graph Explicit Node Set

## Goal

Move Athena's virtual checkout graph closer to a complete working-copy/source model by making graph nodes explicit instead of inferring everything from a few special fields plus edges.

## Design

- Extend `GET /api/v1/nodes/{nodeId}/relations/checkout-graph` with a new additive `nodes` collection.
- Populate `nodes` with first-class graph nodes for:
  - document
  - working copy
  - baseline version
  - current version
  - check-in destination folder
- Keep existing fields such as `documentNode`, `workingCopyNode`, `destinationNode`, `baselineVersion`, and `currentVersion` for compatibility.
- Update the shared checkout-graph dialog to render graph nodes from the explicit node set rather than re-deriving chips from per-field data.

## Why This Slice

- Before this change, Athena had graph edges pointing at version IDs, but the versions were not first-class graph nodes in the payload.
- That made the graph semantically incomplete and kept UI consumption tied to special-case fields.
- This slice is still additive and low-risk, but it materially improves the shape of the checkout graph toward a fuller working-copy/source graph.

## Benchmark Impact

- Athena still does not persist a real working-copy entity.
- It does now expose a more complete operator graph than a checkout model that only reports status plus a few labels.
