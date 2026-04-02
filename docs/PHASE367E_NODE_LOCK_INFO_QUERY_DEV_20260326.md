# Phase 367E: Node Lock Info Query

## Goal

Add a lightweight lock-info query endpoint so callers can ask a focused question:

- is this node locked for me,
- by whom,
- for how long,
- and can I unlock it?

This is a read-only lock diagnostics slice that goes beyond Alfresco’s bare lock status enum by returning actionable timing and permission detail in one payload.

## Delivered

- Added `LockStatus` with:
  - `NO_LOCK`
  - `LOCK_OWNER`
  - `LOCKED_BY_OTHER`
  - `LOCK_EXPIRED`
- Added `LockInfoDto` with:
  - status
  - locked owner and timestamps
  - lifetime and expiry
  - remaining seconds
  - lock age seconds
  - `canUnlock`
- Added `NodeService.getLockInfo(UUID)` for caller-relative lock diagnostics.
- Added `GET /api/v1/nodes/{nodeId}/lock-info`.
- Added frontend `LockInfo` typing and `nodeService.getLockInfo(...)`.

## Design

This phase is intentionally read-only.

Why:

- Athena already has richer lock metadata than earlier in the week.
- What was still missing was a cheap, explicit operator query for that metadata.
- Returning lock diagnostics in a dedicated DTO is safer and more composable than forcing every client to infer lock semantics from the full node payload.

## Why This Matters

Compared with the benchmark, this slice improves operational detail by returning:

- caller-relative ownership status,
- precomputed unlockability,
- remaining time for ephemeral locks,
- and lock age.

That is better than a plain enum because UI and operator tooling can react immediately without secondary permission or time calculations.

## Claude Code Usage

Claude Code was used as a parallel design assistant to compare Athena’s proposed lock-info query with Alfresco-style lock status behavior and to pressure-test the minimum high-value DTO shape. Final implementation and validation were completed in this workspace flow.
