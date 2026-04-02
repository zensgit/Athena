# Phase 367B: Node Lock Lifetime And Expiry Verification

## Backend

- `cd ecm-core && mvn -q -Dtest=NodeServiceLockTest,NodeControllerLockTest,NodeServiceCheckoutTest,DocumentControllerCheckoutTest test`

## Frontend

- `cd ecm-frontend && ./node_modules/.bin/eslint src/types/index.ts`
- `cd ecm-frontend && npm run -s build`

## Diff hygiene

- `git diff --check -- ecm-core/src/main/java/com/ecm/core/entity/LockLifetime.java ecm-core/src/main/java/com/ecm/core/entity/Node.java ecm-core/src/main/java/com/ecm/core/dto/NodeDto.java ecm-core/src/main/java/com/ecm/core/controller/NodeController.java ecm-core/src/main/java/com/ecm/core/service/NodeService.java ecm-core/src/main/resources/db/changelog/db.changelog-master.xml ecm-core/src/main/resources/db/changelog/changes/034-add-node-lock-lifetime-columns.xml ecm-core/src/test/java/com/ecm/core/service/NodeServiceLockTest.java ecm-core/src/test/java/com/ecm/core/controller/NodeControllerLockTest.java ecm-frontend/src/types/index.ts`

## Scope verified

- Lock endpoint accepts optional lifetime and duration parameters.
- Ephemeral locks persist `lockLifetime` and `lockExpiresAt`.
- Expired locks are automatically cleared on subsequent node access.
- Node payload typing compiles with the extended lock metadata.
- Existing checkout persistence tests still pass alongside the new lock slice.

## Notes

- This slice does not yet add lock types such as read-only vs write lock, lock inheritance, or bulk lock orchestration.
- The broader “surpass benchmark in all functional, operational, and detail surfaces” goal remains in progress.
