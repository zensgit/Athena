# Phase368C FileList Typed Lock Operator Surface Verification

## Scope

Verified the new browse-level typed/deep lock operator surface and its focused backend endpoint coverage.

## Commands

```bash
cd ecm-core && mvn -q -Dtest=NodeControllerLockTest,LockServiceTest,NodeControllerLockInfoTest test
cd ecm-frontend && ./node_modules/.bin/eslint src/services/nodeService.ts src/components/browser/FileList.tsx
cd ecm-frontend && npm run -s build
git diff --check -- ecm-frontend/src/services/nodeService.ts ecm-frontend/src/components/browser/FileList.tsx ecm-core/src/test/java/com/ecm/core/controller/NodeControllerLockTest.java docs/PHASE368C_FILELIST_TYPED_LOCK_OPERATOR_SURFACE_DEV_20260329.md docs/PHASE368C_FILELIST_TYPED_LOCK_OPERATOR_SURFACE_VERIFICATION_20260329.md
```

## Result

- Focused backend lock tests passed.
- Frontend ESLint passed.
- Frontend production build passed.
- `git diff --check` passed for the targeted files.

## Assertions Covered

- Typed lock endpoint accepts type/lifetime/deep/info parameters.
- Deep unlock endpoint accepts recursive flag.
- Batch lock and batch unlock endpoints accept UUID list payloads.
- Browse-level context menu can now invoke typed lock and deep unlock via `nodeService`.
