# Phase367ZC Checkout Destination Navigation

## Goal

Turn checkout destination metadata into an actual operator shortcut so users can jump directly to the check-in target from active checkout surfaces.

## Design

- Keep using the existing virtual `checkout-graph` contract.
- Do not add new backend APIs in this phase.
- Add destination-driven UI affordances:
  - `VersionHistoryDialog` active checkout banner gets `Open check-in target`
  - `AdvancedSearchPage` relations details include the destination folder in the checkout graph summary

## Why This Slice

- It is a workflow improvement, not just more metadata.
- It keeps the current low-conflict strategy: frontend-only consumption of already available graph data.
- It helps close the remaining Alfresco-like operator gap by making checkout destination actionable from lineage-aware screens.

## UI Changes

- `VersionHistoryDialog`
  - shows `Open check-in target` when `checkoutGraph.destinationNode.id` is available
  - navigates to `/browse/{destinationNode.id}`
  - closes the dialog after navigation
- `AdvancedSearchPage`
  - checkout graph summary now includes `target {destination label}`

## Benchmark Impact

- This does not create a full persisted working-copy/destination workflow.
- It does make Athena’s operator path more direct by turning virtual checkout destination semantics into a real navigation affordance.
