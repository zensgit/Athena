# Phase367V: Version History Checkout Context Compare

## Goal

Make checkout source relationship more operational inside `VersionHistoryDialog` by adding direct context-menu compare actions against checkout baseline and checkout current versions.

## Scope

- Add `Compare with checkout baseline` to version-row context menu.
- Add `Compare with checkout current` to version-row context menu.
- Reuse existing:
  - version markers
  - checkout relation loading
  - compare dialog

## Design

### Why this slice

Athena already supports:

- baseline/current markers
- one-click lineage compare banner

The next practical improvement is to let operators start from any version row and compare it directly to the active checkout endpoints. That reduces manual picker work and makes source/current semantics available exactly where version investigation already happens.

### Behavior

- `Compare with checkout baseline`
  - disabled when no checkout baseline is loaded
  - disabled on the baseline row itself
- `Compare with checkout current`
  - disabled when no checkout current is loaded
  - disabled on the current row itself

Both actions reuse the existing compare dialog and comparison pipeline.

## Files

- `ecm-frontend/src/components/dialogs/VersionHistoryDialog.tsx`

## Claude Code

Claude Code was used as a parallel design assistant to validate that contextual compare actions were the smallest next operator-facing source-relationship improvement after the lineage banner. Final implementation and validation were completed in this workspace.
