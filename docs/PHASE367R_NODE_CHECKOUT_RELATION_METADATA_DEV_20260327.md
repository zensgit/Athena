# Phase367R: Node Checkout Relation Metadata

## Goal

Move Athena one step closer to Alfresco-style working-copy semantics by promoting checkout state into the existing node-relations surface as a first-class virtual relation, without introducing a full working-copy entity model yet.

## Scope

- Extend node relations summary with checkout metadata.
- Add `GET /api/v1/nodes/{nodeId}/relations/checkout`.
- Reuse existing caller-relative `CheckoutInfoDto` semantics instead of inventing another checkout contract.
- Surface checkout relation details in `AdvancedSearchPage` relation summary/details.

## Design

### Why relations

Athena already has an established virtual-relations surface for:

- parents / children
- sources / targets
- versions
- renditions

Checkout/source lineage is the next missing semantic axis. Making it visible through the same control plane is a lower-conflict step toward working-copy parity than introducing a full working-copy entity model immediately.

### Backend shape

`NodeController` now does two things:

1. `relations/summary` includes:
   - `checkedOut`
   - `checkoutUser`
   - `checkoutDate`

2. `relations/checkout` returns a new virtual relation payload with:
   - document flag
   - checkout owner/date
   - current version label
   - caller-relative affordances:
     - `canCheckout`
     - `canCheckIn`
     - `canCancelCheckout`
     - `canKeepCheckedOut`
     - `requiresNewVersionFile`
     - `blockingReason`

This deliberately reuses `NodeService.getCheckoutInfo(...)` so the relations surface stays consistent with the existing checkout diagnostics endpoint.

### Frontend shape

`AdvancedSearchPage` already renders node relation summary/details for a representative document. This phase extends that panel instead of creating yet another checkout-only widget:

- summary chips now show checkout presence/owner
- relation details now include checkout relation metadata and keep-checked-out support

## Why this before full working-copy entities

- It gives operators a clearer relation-oriented mental model now.
- It reuses the existing relation and diagnostics surfaces.
- It preserves optionality for a later true working-copy/source entity design.

## Files

- `ecm-core/src/main/java/com/ecm/core/controller/NodeController.java`
- `ecm-core/src/test/java/com/ecm/core/controller/NodeControllerRelationsTest.java`
- `ecm-frontend/src/services/nodeService.ts`
- `ecm-frontend/src/pages/AdvancedSearchPage.tsx`

## Claude Code

Claude Code was used as a parallel design assistant to compare the next smallest working-copy-adjacent slice. Final implementation and validation were completed in this workspace.
