# Phase367I Checkout Info Operator Diagnostics

## Goal

Push Athena closer to Alfresco-style working-copy semantics by exposing caller-relative checkout state and action affordances, without introducing a full working-copy fork yet.

## Why This Slice

Athena already persists checkout state, supports `keepCheckedOut`, and now has strong lock diagnostics. The missing piece is operator clarity:

- whether the current user can check out the document
- whether they can check in or cancel checkout
- whether a checkout is theirs or another user's
- what currently blocks checkout

This slice mirrors the value of `lock-info` for checkout semantics.

## Scope

Backend:

- add `CheckoutStatus`
- add `CheckoutInfoDto`
- add `NodeService.getCheckoutInfo(UUID)`
- add `GET /api/v1/documents/{documentId}/checkout-info`

Frontend:

- add `CheckoutInfo` types and service client
- add checkout info formatting helpers
- surface checkout status chip and alert in `DocumentPreview`

## API Shape

`GET /api/v1/documents/{documentId}/checkout-info`

Response fields:

- `status`: `AVAILABLE | CHECKED_OUT_BY_YOU | CHECKED_OUT_BY_OTHER`
- `checkoutUser`
- `checkoutDate`
- `checkoutAgeSeconds`
- `canCheckout`
- `canCheckIn`
- `canCancelCheckout`
- `canKeepCheckedOut`
- `requiresNewVersionFile`
- `blockingReason`

## Design Notes

- `AVAILABLE` does not always mean actionable. It can still carry a `blockingReason`, for example a foreign active lock or missing write permission.
- `CHECKED_OUT_BY_OTHER` still advertises admin affordances through `canCheckIn` and `canCancelCheckout`.
- `DocumentPreview` consumes checkout info independently of node details so the UI can evolve without overloading `NodeDto`.

## Files

- `ecm-core/src/main/java/com/ecm/core/entity/CheckoutStatus.java`
- `ecm-core/src/main/java/com/ecm/core/dto/CheckoutInfoDto.java`
- `ecm-core/src/main/java/com/ecm/core/service/NodeService.java`
- `ecm-core/src/main/java/com/ecm/core/controller/DocumentController.java`
- `ecm-core/src/test/java/com/ecm/core/service/NodeServiceCheckoutTest.java`
- `ecm-core/src/test/java/com/ecm/core/controller/DocumentControllerCheckoutTest.java`
- `ecm-frontend/src/types/index.ts`
- `ecm-frontend/src/services/nodeService.ts`
- `ecm-frontend/src/utils/checkoutInfoUtils.ts`
- `ecm-frontend/src/utils/checkoutInfoUtils.test.ts`
- `ecm-frontend/src/components/preview/DocumentPreview.tsx`

## Risk

- this is operator metadata, not full working-copy/source lineage
- list views still do not consume checkout diagnostics yet
- full Alfresco-style working-copy relationships remain a later phase
