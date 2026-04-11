# Phase 369BR: Replication Content Policy And Conflict Controls - Frontend Verification

## Verification Target
- Validate the replication-definition conflict-policy surface, request builder, and supporting docs.

## Expected Results
- `TransferReplicationPage` compiles with a conflict-policy select field and table summary chip.
- `transferReplicationService` exposes the conflict-policy type on the definition DTO, draft, and request payload.
- `transferReplicationService.test.ts` covers trimming, enum preservation, and the default `RENAME` fallback.

## Execution
- Run focused frontend lint and unit tests for the transfer replication page and service.
- Run a frontend build if the focused checks remain green.

## Notes
- The backend contract was inspected locally and does not yet include a replication-definition conflict-policy field, so this phase keeps the change frontend-facing while still shaping the outgoing payload.
