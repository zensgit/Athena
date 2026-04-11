# Phase 369BQ: Replication Scheduling And Failure Policy - Backend Verification

## Verification Target
- Validate the controller contract for replication definition scheduling/failure-policy fields and the replication jobs list payload.

## Expected Results
- `POST /api/v1/replication/definitions` binds the schedule and failure-policy request fields into `ReplicationDefinitionMutationRequest`.
- `PUT /api/v1/replication/definitions/{definitionId}` binds the same fields on update and returns them in the DTO response.
- `GET /api/v1/replication/jobs` returns a page payload whose content includes the job diagnostics fields exposed by `ReplicationJobDto`.

## Execution
- Run `mvn -pl ecm-core -Dtest=TransferReplicationControllerTest test`.
- If the controller test passes, no additional backend modules need to be exercised for this sidecar.
