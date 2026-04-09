# Phase 369BK: Remote Outbound Transfer Seam

> **Date**: 2026-04-09

## Goal

Extend the transfer/replication backbone from local loopback replication into a
remote outbound seam with two transport modes:

- `LOOPBACK` keeps the existing local copy path for same-repo replication.
- `ATHENA_HTTP` executes outbound Athena-to-Athena replication over existing
  folder and upload APIs.

The seam also adds a target verification contract so operators can validate a
remote destination before scheduling replication jobs.

## Contract Slice

The target contract now carries:

- `transportType`
- `endpointUrl`
- `endpointPath`
- `authType`
- `authUsername`
- `authSecret`
- `lastVerifiedAt`
- `verificationStatus`
- `verificationMessage`

The controller test now exercises:

- expanded transfer target list/create payloads
- `POST /api/v1/transfer/targets/{targetId}/verify`

## Implementation Notes

- `LOOPBACK` remains the default transport.
- `ATHENA_HTTP` uses a dedicated `AthenaTransferHttpClient` and reuses existing
  Athena APIs:
  - `GET /api/v1/folders/{id}` for verification
  - `POST /api/v1/folders` for remote folder creation
  - `POST /api/v1/documents/upload` for remote document upload
- Verification is treated as a first-class admin operation rather than a hidden
  background detail.
- `TransferReplicationService` now dispatches by `transportType` through a
  `TransferClient` abstraction instead of hardcoding loopback copy behavior.

## Scope Boundaries

- No dedicated remote receiver/transmitter protocol yet.
- No Alfresco receiver compatibility.
- No scheduler UI changes.
- No frontend changes.
