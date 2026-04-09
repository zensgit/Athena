# Phase 369BL: Transfer Replication Operator Surface

> **Date**: 2026-04-09

## Goal

Expose the transfer/replication backbone as an admin operator surface in the
frontend so operators can manage:

- transfer targets
- replication definitions
- target verification
- replication execution and job inspection

This phase is the frontend-side operator surface on top of the remote outbound
transfer seam.

## Surface Contract

The operator surface now includes:

- admin navigation entry: `Transfer Replication`
- route: `/admin/transfer-replication`
- target inventory and create/edit/delete
- local `LOOPBACK` and remote `ATHENA_HTTP` target metadata
- target verification
- replication definition create/edit/run flows
- replication job status table with auto-refresh while jobs are active

## Implementation Notes

- New frontend service: `transferReplicationService.ts`
- New page: `TransferReplicationPage.tsx`
- Admin menu wiring lives in `MainLayout.tsx`
- Route wiring lives in `App.tsx`
- The page keeps the first version operator-friendly, with ID-driven forms for:
  - target folder UUID
  - source node UUID
  - target selection for definitions

## Scope Boundaries

- No backend changes in this slice.
- No deep replication diagnostics UI yet.
- No transport-specific wizarding beyond the basic `ATHENA_HTTP` fields.
