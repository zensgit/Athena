# Phase367ZU Checkout Graph Document Version Edges

## Goal

Make Athena's virtual checkout graph semantically more complete by adding explicit document-to-version edges, so version nodes are not left disconnected or only reachable through working-copy-specific edges.

## Design

- Extend `GET /api/v1/nodes/{nodeId}/relations/checkout-graph` with additive document-level version edges:
  - `DOCUMENT_CURRENT_VERSION`
  - `CHECKOUT_BASELINE_VERSION`
- For available documents:
  - emit `DOCUMENT_CURRENT_VERSION` when a current version exists
- For checked-out documents:
  - keep existing working-copy edges
  - also emit `DOCUMENT_CURRENT_VERSION`
  - also emit `CHECKOUT_BASELINE_VERSION`
- Preserve the existing DTO fields and node ordering; this slice is graph-semantic enrichment, not a breaking contract rewrite.

## Why This Slice

- After Phase367ZT, baseline/current versions became explicit graph nodes, but they were still only partially connected.
- In particular, available documents still returned a current-version node with no graph edge at all.
- Adding document-level version edges makes the graph more coherent for both machines and operators, and moves Athena closer to a real working-copy/source graph.

## Benchmark Impact

- Athena still uses a virtual working-copy graph rather than a persisted working-copy entity.
- It now exposes richer lineage semantics than a checkout model that only reports owner/status and a few inferred labels.
