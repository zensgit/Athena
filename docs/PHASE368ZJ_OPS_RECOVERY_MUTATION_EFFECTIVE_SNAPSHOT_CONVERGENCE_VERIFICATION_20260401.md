# Phase368ZJ Ops Recovery Mutation Effective Snapshot Convergence Verification

## Commands

```bash
cd ecm-core && mvn -q -Dtest='OpsRecoveryControllerSecurityTest#requiresAdminRole+queueByReasonForAdmin+queueByWindowPrefersRenditionSummary+clearBatchForAdmin' test
cd ecm-core && mvn -q -Dtest='OpsRecoveryControllerSecurityTest#replayByFilterForAdmin+replayByFilterPrefersRenditionSummary+clearByFilterForAdmin+clearByFilterForUnsupportedNonRetryable' test
git diff --check -- ecm-core/src/main/java/com/ecm/core/controller/OpsRecoveryController.java docs/PHASE368ZJ_OPS_RECOVERY_MUTATION_EFFECTIVE_SNAPSHOT_CONVERGENCE_DEV_20260401.md docs/PHASE368ZJ_OPS_RECOVERY_MUTATION_EFFECTIVE_SNAPSHOT_CONVERGENCE_VERIFICATION_20260401.md
```

## Result

- Focused `OpsRecoveryControllerSecurityTest` queue, replay, and clear mutation coverage passed.
- `git diff --check` passed for the controller patch and both phase documents.

## Covered Behavior

- `queue-by-reason` preserves explicit queue mutation state while keeping compatible `failureCategory` output.
- `queue-by-window` still prefers rendition-backed effective snapshot details when queue status is sparse.
- `replay-by-filter` and `clear-by-filter` now use the same shared mutation batch item builder.
- Admin authorization behavior for these surfaces remains unchanged.
