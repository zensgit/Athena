# Phase 369BJ: Transfer/Replication Backbone

> **Date**: 2026-04-08

## Goal

Add Athena's first transfer/replication backbone so the repository has a real
target/definition/job model instead of a planning-only gap.

This first pass is intentionally local and loopback-oriented:

- transfer targets point at an existing destination folder
- replication definitions bind a source node to a target
- execute-now runs asynchronously and copies the source tree into the target
  folder

It does not attempt Alfresco-style remote transport, manifest exchange, or
receiver-side protocol compatibility yet.

## Implementation

### Domain model

- Added [TransferTarget.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/entity/TransferTarget.java)
  for folder-backed transfer destinations.
- Added [ReplicationDefinition.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/entity/ReplicationDefinition.java)
  for saved source-to-target definitions.
- Added [ReplicationJob.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/entity/ReplicationJob.java)
  for async execution state and reporting.

### Service/controller backbone

- Added [TransferReplicationService.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/TransferReplicationService.java)
  with:
  - transfer target CRUD
  - replication definition CRUD
  - async execute-now replication jobs
  - local loopback execution via
    [NodeService.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/service/NodeService.java)
    `copyNode(..., deep=true)`
- Added [TransferReplicationController.java](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/java/com/ecm/core/controller/TransferReplicationController.java)
  under:
  - `/api/v1/transfer/targets`
  - `/api/v1/replication/definitions`
  - `/api/v1/replication/jobs`

### Persistence

- Added [060-create-transfer-replication-tables.xml](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/resources/db/changelog/changes/060-create-transfer-replication-tables.xml)
  and included it from
  [db.changelog-master.xml](/Users/huazhou/Downloads/Github/Athena/ecm-core/src/main/resources/db/changelog/db.changelog-master.xml).

## Scope Boundaries

- No frontend/operator UI in this phase.
- No remote HTTP transport, target verification handshake, or receiver API.
- No manifest/requisite/report exchange.
- No scheduler UI or persisted cron definitions.
- No schema-level parity with Alfresco transfer/replication yet.
