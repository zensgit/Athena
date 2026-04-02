# Phase 367C: Check-In Keep Checked Out Semantics

## Goal

Add Alfresco-style `keepCheckedOut` behavior to Athena check-in so a user can create a new version while retaining checkout ownership on the document.

This is the smallest next slice that improves operational detail on the checkout/version path without introducing full working-copy nodes yet.

## Delivered

- Extended `POST /api/v1/documents/{documentId}/checkin` with optional `keepCheckedOut`.
- Added service-backed `NodeService.checkinDocument(UUID, boolean)` semantics.
- Preserved checkout state for the checkout owner when `keepCheckedOut=true`.
- Rejected `keepCheckedOut=true` when no new version file is supplied.
- Rejected admin keep-checkout takeover so checkout ownership does not silently transfer across users.
- Extended frontend version creation service to pass `keepCheckedOut`.

## Design

This phase keeps Athena’s current document-centric checkout model and adds a missing check-in affordance.

Why this before working-copy support:

- Athena already persists checkout ownership and version history.
- The biggest operational gap was that check-in always forced checkout release.
- `keepCheckedOut` adds a real user-facing workflow improvement without needing a new working-copy entity model.

## Semantics

- `keepCheckedOut=false`:
  - current Athena behavior remains unchanged,
  - check-in clears checkout metadata.
- `keepCheckedOut=true`:
  - requires a new version file,
  - only the checkout owner can request it,
  - version creation proceeds,
  - checkout remains with the same owner and receives a refreshed checkout timestamp.

## Why This Matters

Compared with previous Athena behavior, this slice improves edit workflows in a way users will actually notice:

- authors can upload an intermediate revision without losing checkout ownership,
- repeated edit cycles become less error-prone,
- admin actions no longer silently steal long-lived checkout state.

That closes a meaningful operational-detail gap versus Alfresco while staying inside Athena’s current data model.

## Claude Code Usage

Claude Code was used as a parallel design assistant to compare Athena’s check-in flow with Alfresco check-in semantics and to pressure-test the smallest safe `keepCheckedOut` slice. Final implementation and validation were completed in this workspace flow.
