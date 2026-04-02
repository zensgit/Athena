# Phase 367B: Node Lock Lifetime And Expiry

## Goal

Upgrade Athena node locking from a plain boolean flag into an operator-visible lifecycle with:

- persistent vs ephemeral lock lifetime,
- explicit expiry timestamp,
- and automatic stale-lock cleanup on access.

This is the smallest lock slice that materially improves operational detail against Alfresco-style lock rigor without committing Athena yet to the full broader lock-service surface.

## Delivered

- Added `LockLifetime` with:
  - `PERSISTENT`
  - `EPHEMERAL`
- Extended `Node` with:
  - `lockLifetime`
  - `lockExpiresAt`
- Added lock helpers on `Node`:
  - `isLockExpired(...)`
  - `isEffectivelyLocked(...)`
  - `applyLock(...)`
  - `clearLock()`
- Extended `NodeService.lockNode(...)` to accept optional lifetime and duration.
- Added automatic expired-lock normalization in `NodeService` read and mutation paths.
- Extended `NodeDto` and frontend `Node` typing with:
  - `lockedDate`
  - `lockLifetime`
  - `lockExpiresAt`
- Extended `POST /api/v1/nodes/{nodeId}/lock` to accept:
  - `lifetime`
  - `durationMinutes`

## Design

This phase deliberately keeps Athena’s existing lock contract shape and improves it in-place.

Why this slice first:

- Athena already had basic lock owner and lock timestamp data.
- The bigger operational problem was stale locks and invisible lock lifetime semantics.
- Expiry-aware locks improve day-to-day operator behavior immediately and safely.

The API remains backward compatible:

- existing lock callers can still call `POST /nodes/{nodeId}/lock` with no parameters,
- default behavior remains persistent locking,
- ephemeral locking is opt-in.

## Semantics

- `PERSISTENT` lock:
  - no expiry timestamp,
  - behaves like Athena’s original lock but now carries explicit lifetime metadata.
- `EPHEMERAL` lock:
  - stores `lockExpiresAt`,
  - defaults to 30 minutes when no explicit duration is provided,
  - is automatically cleared once expired and a node is accessed again.

## Why This Matters

Compared with previous Athena behavior, this slice improves operational detail in ways users and operators can actually feel:

- stale locks no longer remain authoritative forever,
- lock API callers can choose a temporary lock window,
- node payloads now explain not just who locked something, but whether the lock is temporary and when it expires.

That is a pragmatic step toward surpassing Alfresco in day-to-day usability, even before Athena implements the broader lock-type and working-copy roadmap.

## Claude Code Usage

Claude Code was used as a parallel design assistant to compare Athena lock semantics against Alfresco lock behavior and to pressure-test the smallest safe lifetime-and-expiry slice. Final implementation and validation were completed in this workspace flow.
