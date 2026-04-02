# Phase 367D: Effective Lock Enforcement Consistency

## Goal

Make Athena’s new effective-lock semantics consistent across edit, versioning, folder update, and search projection paths.

This phase does not add another lock feature. It removes inconsistent behavior where an expired lock could already be treated as inactive in one path but still block work or remain visible in another.

## Delivered

- Added `Node.describeActiveLock(...)` so lock conflict messages now include active lifetime and expiry details.
- Updated `NodeService` conflict paths to use effective-lock checks and richer conflict text.
- Updated `FolderService.updateFolder(...)` to clear expired locks before enforcing write conflicts.
- Updated `VersionService.createVersion(...)` to:
  - clear expired locks before work starts,
  - reject active foreign locks with lifecycle-aware conflict text.
- Updated `NodeDocument.fromNode(...)` so search projections no longer surface expired locks as active.

## Design

Athena had already gained lock lifetime and expiry metadata, but those semantics were not yet applied consistently.

That created a bad operator experience:

- node APIs could clear expired locks,
- but folder update and version creation could still behave as if the lock was active,
- and search projections could continue showing a stale lock bit.

This phase fixes the contract gap rather than adding more lock surface area.

## Why This Matters

Compared with previous Athena behavior, this phase improves operational detail in a way benchmark products often miss:

- stale locks stop being “zombie” blockers,
- lock conflict messages become more actionable,
- search-facing lock state better matches actual editability.

This is the kind of consistency work required before broader lock type or working-copy features can honestly be called better than the benchmark.

## Claude Code Usage

Claude Code was used as a parallel design assistant to identify this consistency gap after the earlier lock lifetime and keep-checked-out slices. Final implementation and validation were completed in this workspace flow.
