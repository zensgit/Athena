# PHASE368I Ops Recovery Async Task Request Snapshot Surface Verification

## Verified

### Backend

Focused MVC regression passed:

```bash
cd ecm-core && mvn -q -Dtest=OpsRecoveryControllerSecurityTest test
```

What this verified:

- async history export create responses now include `request.exportType / limit / days`
- async history export list rows now include the persisted request snapshot
- deduplicated start responses preserve the same request snapshot
- retry responses preserve the original request snapshot

Dependent compile fixture was also updated so the async task center still builds with the richer DTO shape:

- `ecm-core/src/test/java/com/ecm/core/asynctask/AsyncTaskLifecycleServiceTest.java`

### Frontend

Lint passed:

```bash
cd ecm-frontend && ./node_modules/.bin/eslint \
  src/pages/PreviewDiagnosticsPage.tsx \
  src/services/opsRecoveryService.ts \
  src/utils/opsRecoveryAsyncTaskUtils.ts \
  src/utils/opsRecoveryAsyncTaskUtils.test.ts
```

Focused utility test passed:

```bash
cd ecm-frontend && CI=true npm test -- --watch=false --runInBand \
  src/utils/opsRecoveryAsyncTaskUtils.test.ts
```

Production build passed:

```bash
cd ecm-frontend && npm run -s build
```

What this verified:

- async request snapshot types compile through the frontend service layer
- the request summary/detail formatter is stable for standard and compare exports
- `PreviewDiagnosticsPage` renders with the new async task request column

### Patch Hygiene

`git diff --check` passed for the phase files:

```bash
git diff --check -- \
  ecm-core/src/main/java/com/ecm/core/controller/OpsRecoveryController.java \
  ecm-core/src/test/java/com/ecm/core/controller/OpsRecoveryControllerSecurityTest.java \
  ecm-core/src/test/java/com/ecm/core/asynctask/AsyncTaskLifecycleServiceTest.java \
  ecm-frontend/src/services/opsRecoveryService.ts \
  ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx \
  ecm-frontend/src/utils/opsRecoveryAsyncTaskUtils.ts \
  ecm-frontend/src/utils/opsRecoveryAsyncTaskUtils.test.ts \
  docs/PHASE368I_OPS_RECOVERY_ASYNC_TASK_REQUEST_SNAPSHOT_SURFACE_DEV_20260329.md \
  docs/PHASE368I_OPS_RECOVERY_ASYNC_TASK_REQUEST_SNAPSHOT_SURFACE_VERIFICATION_20260329.md
```

## Notes

This phase is intentionally scoped to async history export task visibility, not export payload content. The exported CSV semantics had already been converged in earlier phases.

The key verification point here is that Athena’s async task center now exposes the normalized request scope that operators need in order to understand deduplicated, retried, and active tasks without leaving the page.
