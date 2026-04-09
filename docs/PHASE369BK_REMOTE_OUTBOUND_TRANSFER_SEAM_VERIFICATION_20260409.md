# Phase 369BK: Remote Outbound Transfer Seam Verification

> **Date**: 2026-04-09

## Verification

### Focused tests

```bash
cd ecm-core
mvn -q -Dtest=TransferReplicationServiceTest,TransferReplicationControllerTest,AthenaTransferHttpClientTest test
```

### Diff sanity

```bash
git diff --check
```

## What to Confirm

- Transfer targets serialize transport, endpoint, auth, and verification
  metadata in list/create responses.
- `POST /api/v1/transfer/targets/{targetId}/verify` returns the verified target
  contract.
- Loopback transport still runs through the shared `TransferClient` seam.
- `ATHENA_HTTP` verifies folders and uploads content through the existing
  Athena HTTP APIs.

## Notes

- This phase delivers the first real outbound remote transport seam.
- It keeps loopback intact while making remote transport pluggable.
