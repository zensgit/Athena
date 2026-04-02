# Phase 367C: Check-In Keep Checked Out Verification

## Backend

- `cd ecm-core && mvn -q -Dtest=NodeServiceCheckoutTest,DocumentControllerCheckoutTest test`

## Frontend

- `cd ecm-frontend && ./node_modules/.bin/eslint src/services/nodeService.ts`
- `cd ecm-frontend && npm run -s build`

## Diff hygiene

- `git diff --check -- ecm-core/src/main/java/com/ecm/core/service/NodeService.java ecm-core/src/main/java/com/ecm/core/controller/DocumentController.java ecm-core/src/test/java/com/ecm/core/service/NodeServiceCheckoutTest.java ecm-core/src/test/java/com/ecm/core/controller/DocumentControllerCheckoutTest.java ecm-frontend/src/services/nodeService.ts docs/PHASE367C_CHECKIN_KEEP_CHECKED_OUT_DEV_20260326.md docs/PHASE367C_CHECKIN_KEEP_CHECKED_OUT_VERIFICATION_20260326.md`

## Scope verified

- Check-in supports optional `keepCheckedOut`.
- Checkout owner can keep a document checked out across a new version creation.
- Admin cannot use `keepCheckedOut` to take over another user’s checkout.
- `keepCheckedOut` without a new version file is rejected as a bad request.
- Frontend version service compiles against the extended check-in contract.

## Notes

- This slice does not yet add working-copy nodes, alternate checkout destination folders, or checkout/source relationships.
- The broader “surpass benchmark in all functional, operational, and detail surfaces” goal remains in progress.
