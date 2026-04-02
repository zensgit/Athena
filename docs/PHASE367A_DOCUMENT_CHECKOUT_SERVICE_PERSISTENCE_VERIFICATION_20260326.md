# Phase 367A: Document Checkout Service Persistence Verification

## Backend

- `cd ecm-core && mvn -q -Dtest=NodeServiceCheckoutTest,DocumentControllerCheckoutTest,DocumentControllerPreviewRepairTest test`

## Frontend

- `cd ecm-frontend && ./node_modules/.bin/eslint src/types/index.ts`
- `cd ecm-frontend && npm run -s build`

## Diff hygiene

- `git diff --check -- ecm-core/src/main/java/com/ecm/core/dto/NodeDto.java ecm-core/src/main/java/com/ecm/core/service/NodeService.java ecm-core/src/main/java/com/ecm/core/controller/DocumentController.java ecm-core/src/test/java/com/ecm/core/service/NodeServiceCheckoutTest.java ecm-core/src/test/java/com/ecm/core/controller/DocumentControllerCheckoutTest.java ecm-frontend/src/types/index.ts`

## Scope verified

- `NodeService` persists checkout, checkin, and cancel-checkout transitions.
- Checkout rejects missing and deleted documents.
- Checkout enforces `WRITE` permission and rejects conflicting locks.
- Checkin and cancel-checkout require the checkout owner or an admin.
- `DocumentController` returns `NodeDto` payloads containing checkout metadata.
- Frontend typing compiles against the new `checkedOut / checkoutUser / checkoutDate` fields.

## Notes

- This slice does not yet add working-copy nodes, `keepCheckedOut`, checkout-to-destination, or timed lock semantics.
- The broader “surpass benchmark in all functional, operational, and detail surfaces” goal remains in progress.
