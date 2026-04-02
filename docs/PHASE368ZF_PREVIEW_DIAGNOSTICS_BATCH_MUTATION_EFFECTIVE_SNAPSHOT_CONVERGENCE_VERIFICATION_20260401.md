# Phase368ZF Preview Diagnostics Mutation Effective Snapshot Convergence Verification

## Scope

Verify that `PreviewDiagnostics` mutation endpoints preserve explicit queue mutation state while backfilling sparse queue responses from the shared effective preview snapshot.

## Commands

```bash
cd ecm-core && mvn -q -Dtest='PreviewDiagnosticsControllerSecurityTest#diagnosticsQueueDeclinedRequeueActionsPreferSharedPreviewSnapshot+diagnosticsBatchQueueAggregatesOutcome+diagnosticsBatchQueuePrefersSharedEffectivePreviewSnapshot+diagnosticsDeadLetterReplayBatchPrefersSharedEffectivePreviewSnapshot+diagnosticsDeadLetterForAdmin' test
git diff --check -- ecm-core/src/main/java/com/ecm/core/controller/PreviewDiagnosticsController.java ecm-core/src/test/java/com/ecm/core/controller/PreviewDiagnosticsControllerSecurityTest.java docs/PHASE368ZF_PREVIEW_DIAGNOSTICS_BATCH_MUTATION_EFFECTIVE_SNAPSHOT_CONVERGENCE_DEV_20260401.md docs/PHASE368ZF_PREVIEW_DIAGNOSTICS_BATCH_MUTATION_EFFECTIVE_SNAPSHOT_CONVERGENCE_VERIFICATION_20260401.md
```

## Expected

- existing `dead-letter/replay-batch` behavior keeps explicit queued `PROCESSING` status
- new queue-batch focused test proves sparse queue status is backfilled from shared effective preview snapshot
- new dead-letter replay focused test proves the same sparse-status backfill path for replay flow
- existing declined requeue focused test continues to prove shared effective preview snapshot consumption after the refactor
- no whitespace or patch formatting regressions

## Result

- command suite passed on 2026-04-01
- focused MVC coverage now pins both explicit mutation priority and shared snapshot fallback behavior
