# Phase 368ZN - Verification

## Checks
- `cd ecm-core && mvn -q -Dtest='PreviewDiagnosticsControllerSecurityTest#diagnosticsQueueDeclinedRequeueActionsPreferSharedPreviewSnapshot+diagnosticsDeadLetterForAdmin+diagnosticsDeadLetterReplayBatchPrefersSharedEffectivePreviewSnapshot+diagnosticsBatchQueueAggregatesOutcome+diagnosticsBatchQueuePrefersSharedEffectivePreviewSnapshot' test`
- `git diff --check`

## Expected Result
- Preview diagnostics batch and dry-run responses continue to expose the same effective preview summary fields.
- Inline batch item construction is reduced to shared helper calls only.
