# Phase367J Checkout State Projection And FileList Badge

## Goal

Extend Athena's checkout semantics from preview-level diagnostics into browse and search projection so checked-out documents are visible and safer to operate on before a user triggers a conflict.

## Scope

Backend:

- project `checkedOut` and `checkoutUser` into `NodeDocument`

Frontend:

- map `checkedOut / checkoutUser / checkoutDate` from list and detail node payloads
- add checkout badge tooltip helpers for browse
- render checkout badges in `FileList`
- gate `Annotate (PDF)`, `Edit Online`, `Move`, and `Delete` when a document is checked out by another user

## Why This Slice

Athena already had:

- persisted checkout state
- `keepCheckedOut`
- preview-level checkout diagnostics

But the browse layer still had two operator blind spots:

- list views did not surface checkout state
- context menu write actions only respected lock state, not foreign checkout state

This slice closes both gaps without introducing a full working-copy fork.

## Design

### Projection

`NodeDocument.fromNode(...)` now carries:

- `checkedOut`
- `checkoutUser`

This keeps checkout state searchable and ready for future admin/operator filters.

### Frontend mapping

`nodeService` now maps checkout fields in both:

- `apiNodeToNode()`
- `apiNodeDetailsToNode()`

That makes the browse layer consume the fields already emitted by `NodeDto`.

### Browse UX

New helper: `fileCheckoutBadgeUtils`

- `getFileCheckoutTooltip`
- `isCheckedOutByAnotherUser`
- `getFileCheckoutActionReason`

`FileList` renders a checkout badge alongside the existing lock badge and uses checkout-aware reasons to disable high-risk write actions when another user owns the checkout.

## Files

- `ecm-core/src/main/java/com/ecm/core/search/NodeDocument.java`
- `ecm-core/src/test/java/com/ecm/core/search/NodeDocumentCheckoutProjectionTest.java`
- `ecm-frontend/src/services/nodeService.ts`
- `ecm-frontend/src/components/browser/FileList.tsx`
- `ecm-frontend/src/utils/fileCheckoutBadgeUtils.ts`
- `ecm-frontend/src/utils/fileCheckoutBadgeUtils.test.ts`

## Risk

- this slice does not yet add dedicated checkout badges to every node surface
- checkout-aware search filters are only enabled at projection level in this phase, not yet promoted to explicit UI filters
