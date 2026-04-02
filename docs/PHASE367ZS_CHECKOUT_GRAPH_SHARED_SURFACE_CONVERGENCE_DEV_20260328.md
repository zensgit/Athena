# Phase367ZS Checkout Graph Shared Surface Convergence

## Goal

Reduce the remaining checkout-graph UX split by making the same shared operator surface available from history, preview, and advanced relations views.

## Design

- Reuse the shared `CheckoutGraphDialog` introduced in Phase367ZR instead of continuing to hand-build graph visibility per page.
- Integrate the dialog into:
  - `VersionHistoryDialog`
  - `DocumentPreview`
  - `AdvancedSearchPage`
- Replace the most verbose hand-built checkout-graph summary strings with `formatCheckoutGraphSummary(...)` so graph semantics stay aligned across work areas.
- Keep page-specific affordances such as:
  - `Compare checkout lineage` in version history
  - `Open check-in target` shortcuts
  - advanced relations context

## Why This Slice

- Athena already exposed the virtual checkout graph and had one shared dialog for browse/search.
- The remaining high-frequency surfaces still rendered graph context with page-local string building, which kept operator semantics fragmented.
- This slice moves the graph closer to becoming a real platform surface rather than a set of per-page embellishments.

## Benchmark Impact

- This still sits on top of a virtual working-copy graph, not a persisted working-copy entity.
- It does improve operator coherence beyond reference implementations that expose checkout status but do not provide a cross-surface graph workflow.
