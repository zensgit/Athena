# Phase367X: Direct Checkout Lineage API

## Goal

Remove the last major dependency on currently loaded version-history pages by introducing a direct checkout-lineage API that returns baseline/current version DTOs alongside caller-relative checkout metadata.

## Scope

- Add `GET /api/v1/documents/{documentId}/checkout-lineage`
- Return:
  - checkout info
  - baseline version DTO
  - current version DTO
- Use that API in `VersionHistoryDialog` so lineage compare works immediately even before version paging has loaded the relevant rows.

## Design

### Why this slice

Before this phase:

- Athena could derive lineage from paged version history
- it could auto-load more pages
- but immediate lineage compare still depended on version-history state already held in the dialog

This phase removes that dependency by exposing the needed lineage endpoints directly.

### Backend contract

`checkout-lineage` returns:

- `CheckoutInfoDto checkout`
- `VersionDto baselineVersion`
- `VersionDto currentVersion`

The controller resolves:

- checkout state from existing `NodeService.getCheckoutInfo(...)`
- baseline version from persisted `checkoutBaselineVersionId`
- current version from the document’s current version reference

This reuses existing DTOs rather than introducing a parallel compare-only model.

### Frontend behavior

`VersionHistoryDialog` now loads:

- checkout relation
- direct checkout lineage

So `Compare checkout lineage` can use:

- loaded version rows when available
- direct baseline/current DTOs when not yet loaded

This makes lineage compare immediately actionable instead of page-state dependent.

## Files

- `ecm-core/src/main/java/com/ecm/core/dto/CheckoutLineageDto.java`
- `ecm-core/src/main/java/com/ecm/core/controller/DocumentController.java`
- `ecm-core/src/test/java/com/ecm/core/controller/DocumentControllerCheckoutTest.java`
- `ecm-frontend/src/services/nodeService.ts`
- `ecm-frontend/src/components/dialogs/VersionHistoryDialog.tsx`

## Claude Code

Claude Code was used as a parallel design assistant to validate that a direct checkout-lineage endpoint was the smallest next slice that removed dependence on already-loaded version pages. Final implementation and validation were completed in this workspace.
