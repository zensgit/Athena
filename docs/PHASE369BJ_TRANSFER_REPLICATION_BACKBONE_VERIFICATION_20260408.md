# Phase 369BJ: Transfer/Replication Backbone Verification

> **Date**: 2026-04-08

## Verification

### Focused backend tests

```bash
cd ecm-core
mvn -q -Dtest=TransferReplicationServiceTest,TransferReplicationControllerTest test
```

### Diff sanity

```bash
git diff --check
```

## What to Confirm

- Transfer targets can be created as folder-backed loopback destinations.
- Replication definitions bind a source node to a transfer target.
- Execute-now creates an async replication job and completes local loopback
  copy execution.
- Controller endpoints return the expected `201`/`202`/page payload contracts.

## Notes

- This phase establishes the backbone only.
- It intentionally stops short of Alfresco-style remote transfer protocol,
  receiver endpoints, and replication scheduling.
