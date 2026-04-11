# Phase 369BQ: Replication Scheduling And Failure Policy - Backend Dev

## Scope
- Kept the sidecar strictly on the transfer replication controller test surface and did not touch service, entity, repository, or frontend code.
- Added controller coverage for replication definition create and update requests that carry the schedule and failure-policy fields defined by the backend DTO contract.
- Tightened the replication jobs list assertion so the page payload is checked for the job diagnostics fields the controller actually exposes.

## Changed Files
- `ecm-core/src/test/java/com/ecm/core/controller/TransferReplicationControllerTest.java`

## Notes
- The replication definition mutation contract includes `cronExpression`, `scheduleTimezone`, `autoRetryEnabled`, `maxRetryAttempts`, `retryBackoffMinutes`, and `jobRetentionDays`.
- The replication job DTO contract includes `scheduledFor`, `attemptNumber`, `transportStatus`, and the rest of the job diagnostics returned by the controller.
- The controller test now captures the bound request records so JSON-to-record mapping is verified, not just the mocked response body.
