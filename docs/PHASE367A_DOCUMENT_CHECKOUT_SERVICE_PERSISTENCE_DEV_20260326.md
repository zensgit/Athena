# Phase 367A: Document Checkout Service Persistence

## Goal

Move Athena document checkout semantics off controller-local entity mutation and onto a service-backed persistence path so checkout, checkin, and cancel-checkout become:

- permission-checked,
- ownership-aware,
- and consistently persisted.

This is a smaller but high-value slice toward surpassing Alfresco operationally because it makes checkout behavior reliable before Athena takes on the larger working-copy and lock-model backlog.

## Delivered

- Added service-backed checkout lifecycle methods in `NodeService`:
  - `checkoutDocument(UUID)`
  - `checkinDocument(UUID)`
  - `cancelCheckoutDocument(UUID)`
- Enforced:
  - missing/deleted document rejection,
  - `WRITE` permission for checkout,
  - lock-owner conflict rejection,
  - owner-or-admin enforcement for checkin and cancel-checkout.
- Persisted checkout lifecycle state through `documentRepository.save(...)` instead of mutating entities only in `DocumentController`.
- Updated `DocumentController` checkout endpoints to delegate to `NodeService`.
- Extended `NodeDto` so API consumers receive:
  - `checkedOut`
  - `checkoutUser`
  - `checkoutDate`
- Extended frontend `Node` typing with the same checkout metadata.

## Design

This slice intentionally does **not** attempt to introduce Alfresco-style working copies yet.

Why this first:

- Athena already had `Document.checkoutUser` and `Document.checkoutDate`.
- The bigger gap was that controller endpoints were not the authoritative lifecycle path.
- A working-copy model on top of non-authoritative checkout state would compound inconsistency.

So this phase makes checkout semantics **authoritative and observable** first, while keeping the public API stable.

## Scope

Backend:

- `DocumentController` now delegates checkout lifecycle operations to `NodeService`.
- `NodeService` owns validation and persistence for checkout transitions.
- `NodeDto` projects persisted checkout metadata for document nodes.

Frontend:

- shared `Node` typing now exposes checkout metadata needed by future UI affordances.

## Why This Matters

Compared with previous Athena behavior, this slice improves operational detail in three ways:

- checkout state now survives through the intended service path,
- checkin/cancel-checkout are no longer controller-side state toggles,
- API consumers can render who checked out a document and when.

That does not yet equal Alfresco’s working-copy feature set, but it is the correct foundation for surpassing it later without layering new semantics on unstable lifecycle behavior.

## Claude Code Usage

Claude Code was used as a parallel design assistant to compare Athena checkout semantics against Alfresco lock/checkout behavior and to pressure-test the smallest safe persistence-first slice. Final implementation and validation were completed in this workspace flow.
