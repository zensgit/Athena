# PHASE368E Ops Recovery Dry-Run Effective Summary Convergence

## Goal

Continue the `preview / rendition / search` source-of-truth line by removing another recovery control-plane fork:

- `OpsRecoveryController` dry-run samples should return the same effective preview summary fields already used by batch queue/replay/clear results.
- `PreviewDiagnosticsPage` should show those richer dry-run preview semantics directly in the `Ops Recovery Dry-Run` sample table.

## Why This Phase

Before this change, `OpsRecovery` dry-run responses only exposed:

- `previewStatus`
- `failureCategory`

That left dry-run samples weaker than the batch recovery paths, which already returned:

- `previewFailureReason`
- `previewFailureCategory`
- `previewLastUpdated`

This created a visible operator mismatch:

- executing a recovery batch gave richer effective preview context,
- but the dry-run that was supposed to explain the plan still hid the normalized reason and update timestamp.

## Scope

### Backend

Extended `RecoveryDryRunItemDto` in `OpsRecoveryController` to include:

- `previewFailureReason`
- `previewFailureCategory`
- `previewLastUpdated`

Applied the richer contract consistently to all dry-run builders:

- `evaluateDryRun(...)`
- `evaluateDeadLetterClearDryRun(...)`
- `evaluateDeadLetterReplayDryRun(...)`

All three now reuse `resolveEffectivePreviewSummary(...)` instead of assembling partial preview semantics independently.

### Frontend

Extended `RecoveryDryRunItem` in `opsRecoveryService.ts` to preserve the richer contract.

Updated the `Ops Recovery Dry-Run` table in `PreviewDiagnosticsPage.tsx` to render a dedicated `Preview` column with:

- effective status
- effective failure category
- normalized preview reason
- preview updated timestamp

That makes dry-run output align with other preview governance surfaces instead of remaining a reduced sample view.

## Files

### Backend

- `ecm-core/src/main/java/com/ecm/core/controller/OpsRecoveryController.java`
- `ecm-core/src/test/java/com/ecm/core/controller/OpsRecoveryControllerSecurityTest.java`

### Frontend

- `ecm-frontend/src/services/opsRecoveryService.ts`
- `ecm-frontend/src/pages/PreviewDiagnosticsPage.tsx`

## Outcome

Athena’s recovery control-plane now presents dry-run samples with the same effective preview semantics as execution results.

This is another practical step beyond older Alfresco-style operator surfaces:

- less ambiguity during dry-run analysis
- consistent rendition-backed preview semantics across planning and execution
- better recovery triage without forcing operators to jump to another table first
