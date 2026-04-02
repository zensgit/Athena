# Phase 367E: Node Lock Info Query Verification

## Backend

- `cd ecm-core && mvn -q -Dtest=NodeServiceLockInfoTest,NodeControllerLockInfoTest test`

## Frontend

- `cd ecm-frontend && ./node_modules/.bin/eslint src/types/index.ts src/services/nodeService.ts`
- `cd ecm-frontend && npm run -s build`

## Diff hygiene

- `git diff --check -- ecm-core/src/main/java/com/ecm/core/entity/LockStatus.java ecm-core/src/main/java/com/ecm/core/dto/LockInfoDto.java ecm-core/src/main/java/com/ecm/core/service/NodeService.java ecm-core/src/main/java/com/ecm/core/controller/NodeController.java ecm-frontend/src/types/index.ts ecm-frontend/src/services/nodeService.ts ecm-core/src/test/java/com/ecm/core/service/NodeServiceLockInfoTest.java ecm-core/src/test/java/com/ecm/core/controller/NodeControllerLockInfoTest.java docs/PHASE367E_NODE_LOCK_INFO_QUERY_DEV_20260326.md docs/PHASE367E_NODE_LOCK_INFO_QUERY_VERIFICATION_20260326.md`

## Scope verified

- Lock info endpoint returns caller-relative status.
- Payload includes timing metadata and precomputed `canUnlock`.
- Expired locks are reported distinctly from active foreign locks.
- Frontend type and service layer compile against the new endpoint.

## Notes

- This slice is read-only and does not yet add lock-type enforcement or batch lock operations.
- The broader “surpass benchmark in all functional, operational, and detail surfaces” goal remains in progress.
