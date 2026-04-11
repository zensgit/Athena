# Phase 369BQ: Replication Scheduling And Failure Policy - Frontend Verification

## Verification Target
- Validate the replication definition authoring flow and the supporting service payload builder.

## Expected Results
- `TransferReplicationPage` compiles with schedule/failure policy form fields and summary chips.
- `transferReplicationService` exposes schedule/failure policy fields on the replication definition DTO and request payload.
- `transferReplicationService.test.ts` covers the payload builder trimming and numeric conversion behavior.

## Execution
- Run focused frontend lint and unit tests for the transfer replication service and page.
- Run a frontend build if the focused checks remain green.
