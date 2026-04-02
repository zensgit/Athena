# PHASE368F Ops Recovery Dry-Run Export Effective Summary Verification

## Verified

### Backend

Focused MVC regression passed:

```bash
cd ecm-core && mvn -q -Dtest=OpsRecoveryControllerSecurityTest test
```

What this verified:

- `POST /api/v1/ops/recovery/dry-run/export` is admin-only.
- the export responds as CSV.
- the CSV includes `previewFailureReason`, `previewFailureCategory`, and `previewLastUpdated`.
- the exported data prefers rendition-backed unsupported semantics.

### Frontend

Lint passed:

```bash
cd ecm-frontend && ./node_modules/.bin/eslint \
  src/pages/PreviewDiagnosticsPage.tsx \
  src/services/opsRecoveryService.ts
```

Production build passed:

```bash
cd ecm-frontend && npm run -s build
```

Formatting / patch hygiene passed:

```bash
git diff --check -- \
  ecm-core/src/main/java/com/ecm/core/controller/OpsRecoveryController.java \
  ecm-core/src/test/java/com/ecm/core/controller/OpsRecoveryControllerSecurityTest.java \
  ecm-frontend/src/services/opsRecoveryService.ts \
  ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx \
  docs/PHASE368F_OPS_RECOVERY_DRY_RUN_EXPORT_EFFECTIVE_SUMMARY_DEV_20260329.md \
  docs/PHASE368F_OPS_RECOVERY_DRY_RUN_EXPORT_EFFECTIVE_SUMMARY_VERIFICATION_20260329.md
```

## Notes

This verification is intentionally scoped to `Ops Recovery Dry-Run` export behavior. It confirms that the exported CSV now carries the same effective preview semantics as the in-page dry-run samples.
