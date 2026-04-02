# Phase368ZK Ops Recovery Dry-Run History Item Builder Convergence

## Goal

Remove the remaining controller-local DTO hand-building in `OpsRecoveryController` dry-run and history surfaces so that effective preview summary fields are assembled through shared item builders instead of being repeated in multiple branches.

## Problem

After `Phase368ZJ`, batch mutation responses already used a shared builder, but these paths still hand-built DTOs:

- `evaluateDryRun(...)`
- `evaluateDeadLetterClearDryRun(...)`
- `evaluateDeadLetterReplayDryRun(...)`
- `toRecoveryHistoryItem(...)`

That left repeated copies of:

- `previewStatus`
- `failureCategory`
- `previewFailureReason`
- `previewFailureCategory`
- `previewLastUpdated`

across JSON and CSV-producing flows.

## Implementation

### Shared dry-run item builder

Added `buildRecoveryDryRunItem(...)` to centralize `RecoveryDryRunItemDto` construction.

It accepts:

- explicit `documentId`
- optional `Document`
- resolved `EffectivePreviewSummary`
- `DryRunPrediction`

This lets normal document-backed dry-run paths and invalid-entry dry-run paths share the same DTO builder.

### Shared history item builder

Added `buildRecoveryHistoryItem(...)` so `toRecoveryHistoryItem(...)` no longer assembles preview fields inline.

### Unknown summary helper

Added `unknownEffectivePreviewSummary()` for invalid dead-letter entry dry-run samples. This keeps the dry-run item builder shared even when there is no backing document.

## Result

`/dry-run`, `/dry-run/export`, `/history`, and `/history/export` now all consume the same preview summary assembly logic inside `OpsRecoveryController`, reducing duplicated DTO field wiring and making later extraction to a deeper shared helper straightforward.
