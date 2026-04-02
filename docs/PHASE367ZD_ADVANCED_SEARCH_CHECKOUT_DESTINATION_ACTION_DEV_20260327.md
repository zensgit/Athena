# Phase367ZD Advanced Search Checkout Destination Action

## Goal

Make checkout destination actionable directly inside `AdvancedSearchPage` relations details so operators can jump from search triage into the check-in target folder.

## Design

- Reuse existing `checkout-graph.destinationNode`.
- Do not add any backend changes.
- Add a single low-friction UI affordance:
  - `Open check-in target` button beside the checkout graph summary in relations details

## Why This Slice

- It extends the destination workflow beyond `VersionHistoryDialog`.
- It targets the search workbench, which is a common operator surface for triage and follow-up actions.
- It is more valuable than adding more descriptive text because it shortens the operator path from “found the checked-out item” to “open the target workspace”.

## UI Change

- `AdvancedSearchPage`
  - in relations details, when `nodeRelationCheckoutGraph.destinationNode.id` exists:
    - render `Open check-in target`
    - navigate to `/browse/{destinationNode.id}`

## Benchmark Impact

- Still not a full persisted working-copy/destination system.
- It does improve operator detail by making destination semantics navigable from both history and search contexts.
