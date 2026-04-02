# Phase367Z Virtual Checkout Working-Copy Graph

## Goal

Push Athena one step closer to Alfresco-style checkout/source graph semantics without introducing a persisted working-copy entity yet.

## Design

- Add `GET /api/v1/nodes/{nodeId}/relations/checkout-graph` under the existing `relations` namespace.
- Keep the model virtual and compatibility-safe: no new entity, no breaking change to `relations/checkout`, no change to existing version history endpoints.
- Return a graph-shaped payload with:
  - `documentNode`
  - `workingCopyNode` as a virtual node (`working-copy:{nodeId}`)
  - `baselineVersion`
  - `currentVersion`
  - graph edges:
    - `HAS_WORKING_COPY`
    - `WORKING_COPY_BASELINE`
    - `WORKING_COPY_CURRENT`
- Reuse existing checkout baseline metadata and current version metadata as graph anchors.
- Reuse caller-relative checkout affordances (`canCheckIn`, `canCancelCheckout`, `canKeepCheckedOut`) so the graph can become an action surface later.

## Why This Slice

- It is lower conflict than introducing a real working-copy entity or checkout destination model.
- It upgrades Athena from “checkout as flat status” to “checkout as explicit lineage graph”.
- It stays aligned with the current `relations/*` family, so UI consumers do not need a parallel mental model.
- It creates a stable intermediate contract for future `working-copy/source graph` and `checkout-to-destination` work.

## Backend Changes

- `NodeController` now exposes `relations/checkout-graph`.
- The controller resolves:
  - baseline version from `checkoutBaselineVersionId`
  - current version from `document.currentVersion`
  - a virtual working-copy node keyed as `working-copy:{nodeId}`
- New nested DTO records:
  - `NodeCheckoutGraphDto`
  - `NodeCheckoutGraphNodeDto`
  - `NodeCheckoutGraphEdgeDto`

## Frontend Changes

- `nodeService` now exposes `getNodeRelationCheckoutGraph(...)`.
- `AdvancedSearchPage` loads the checkout graph alongside existing relation details.
- Relations details now render a concise working-copy lineage summary such as:
  - working copy label
  - baseline version
  - current version
  - edge types present in the virtual graph

## Benchmark Impact

- Alfresco’s checkout semantics are stronger when a true working-copy/source model exists.
- This phase narrows the gap by making Athena’s checkout lineage explicit and inspectable instead of implicit across scattered fields.
- It is not the final surpass step, but it is the cleanest compatibility-safe bridge toward it.
