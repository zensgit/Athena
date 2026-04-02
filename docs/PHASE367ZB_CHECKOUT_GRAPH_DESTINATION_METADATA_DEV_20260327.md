# Phase367ZB Checkout Graph Destination Metadata

## Goal

Extend Athena's virtual checkout graph so it also exposes the check-in destination, narrowing the remaining gap toward Alfresco-style checkout destination semantics.

## Design

- Keep the existing virtual checkout graph additive and compatibility-safe.
- Extend `GET /api/v1/nodes/{nodeId}/relations/checkout-graph` with:
  - `destinationNode`
  - a new edge `CHECKIN_DESTINATION`
- Reuse the checked-out document's current parent folder as the destination anchor.
- Do not introduce:
  - a persisted working-copy entity
  - checkout destination persistence
  - breaking changes to `relations/checkout` or `checkout-lineage`

## Backend Changes

- `NodeController` now derives `destinationNode` from `document.parent`.
- The graph now includes:
  - `documentNode`
  - `workingCopyNode`
  - `destinationNode`
  - `baselineVersion`
  - `currentVersion`
  - `CHECKIN_DESTINATION` edge from working copy to destination folder

## Frontend Changes

- `nodeService` maps the new `destinationNode` field into `NodeCheckoutGraph`.
- `VersionHistoryDialog` checkout banner now surfaces:
  - active working-copy node
  - check-in target folder
  - baseline/current lineage

## Why This Slice

- It is the lowest-conflict way to move from “working copy lineage” toward “working copy plus destination”.
- It improves operator clarity without requiring a true working-copy entity.
- It is closer to the remaining Alfresco-like semantic gap than adding more chips or compare shortcuts.

## Benchmark Impact

- Athena still does not persist a true working-copy object, so this is not full parity with Alfresco checkout destination semantics.
- It does make Athena’s operator-facing checkout model more explicit by showing where the active working copy is expected to check back in.
