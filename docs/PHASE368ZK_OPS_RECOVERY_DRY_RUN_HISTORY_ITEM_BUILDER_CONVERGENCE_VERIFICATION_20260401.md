# Phase368ZK Ops Recovery Dry-Run History Item Builder Convergence Verification

## Commands

```bash
cd ecm-core && mvn -q -Dtest='OpsRecoveryControllerSecurityTest#dryRunPredictsSkippedForPermanentFailures+dryRunPrefersRenditionSummaryForUnsupportedFailures+dryRunReplayByFilterPrefersRenditionSummary+exportDryRunPrefersRenditionSummary+dryRunSupportsClearByFilterMode+dryRunSupportsReplayByFilterMode+listHistoryForAdmin+exportHistoryCsvForAdmin' test
git diff --check -- ecm-core/src/main/java/com/ecm/core/controller/OpsRecoveryController.java docs/PHASE368ZK_OPS_RECOVERY_DRY_RUN_HISTORY_ITEM_BUILDER_CONVERGENCE_DEV_20260401.md docs/PHASE368ZK_OPS_RECOVERY_DRY_RUN_HISTORY_ITEM_BUILDER_CONVERGENCE_VERIFICATION_20260401.md
```

## Result

- Focused `OpsRecoveryControllerSecurityTest` dry-run, dry-run export, history, and history export coverage passed.
- `git diff --check` passed for the controller patch and both phase documents.

## Covered Behavior

- queue-by-window dry-run still predicts `SKIPPED` for permanent failures without forcing replay
- unsupported failures still prefer rendition-backed effective preview summary in dry-run JSON and CSV
- replay/clear dry-run filter variants still produce correct sample payloads
- history JSON and history CSV still include the same effective preview summary fields
