# Phase367ZR Checkout Graph Dialog Operator Surface

## Goal

Promote Athena's virtual checkout graph from an internal data source for destination shortcuts into a first-class operator surface on primary work areas.

## Design

- Add a shared `CheckoutGraphDialog` component that:
  - loads `GET /api/v1/nodes/{nodeId}/relations/checkout-graph` on demand
  - shows the active checkout summary
  - exposes baseline/current/destination chips
  - lists graph edges for operator diagnostics
  - keeps `Open Check-In Target` as a direct action
- Add a small shared formatter utility so checkout-graph summary and edge labels stop being hand-built inline.
- Integrate the dialog into:
  - `SearchResults`
  - `FileList`
- Keep the slice frontend-only and lazy-loaded on demand, so it does not add extra graph requests during ordinary page render.

## Why This Slice

- Athena already had the virtual checkout graph API, but most operator surfaces only used it for `Open Check-In Target`.
- That meant the graph existed semantically, yet remained mostly hidden outside advanced relations/history surfaces.
- A shared dialog closes that gap without forcing a larger working-copy persistence refactor.

## Benchmark Impact

- This still uses a virtual graph rather than a persisted working-copy entity.
- It does move Athena closer to surpassing reference implementations in operator diagnostics by making checkout lineage visible and actionable from ordinary browse/search work areas.
