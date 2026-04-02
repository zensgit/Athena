# Phase 368ZN - Preview Diagnostics Batch Item Helper Convergence

## Goal
Reduce remaining inline batch-item construction in `PreviewDiagnosticsController` by routing all preview queue mutation outcomes through shared helper methods.

## Scope
- Replace inline `PreviewQueueBatchItemDto` construction for dead-letter replay failure branches.
- Replace inline `PreviewQueueDeclinedRequeueItemDto` construction for declined requeue failure branches.
- Replace inline `PreviewQueueDeclinedRequeueDryRunItemDto` construction for declined dry-run failure branches.
- Keep response shape unchanged.

## Constraints
- Do not touch preview/search/ops-governance hot files outside `PreviewDiagnosticsController`.
- Do not change public endpoint contracts.

## Verification Plan
- Focused `PreviewDiagnosticsControllerSecurityTest` methods covering declined requeue, dead-letter replay, and batch queue aggregation.
- `mvn test` for the targeted controller security test subset.
- `git diff --check` on changed files.
