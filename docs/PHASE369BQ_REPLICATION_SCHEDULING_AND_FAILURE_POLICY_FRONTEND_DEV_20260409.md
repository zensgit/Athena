# Phase 369BQ: Replication Scheduling And Failure Policy - Frontend Dev

## Scope
- Updated the transfer replication operator surface to author schedule and failure policy fields on replication definitions.
- Added lightweight diagnostics summaries on the replication definitions table.
- Kept the change frontend-only and assumed future backend support for the new replication definition fields.

## Changed Files
- `ecm-frontend/src/pages/TransferReplicationPage.tsx`
- `ecm-frontend/src/services/transferReplicationService.ts`
- `ecm-frontend/src/services/transferReplicationService.test.ts`

## Notes
- Schedule fields are surfaced as cron expression and timezone.
- Failure policy fields are surfaced as max attempts, retry delay, backoff multiplier, and quiet period.
- The jobs table already carries transport and retry diagnostics from prior phases, so this phase keeps diagnostics lightweight and definition-focused.
