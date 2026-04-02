# Phase367U: Version History Checkout Lineage Compare

## Goal

Turn checkout/source relationship from passive metadata into an operator action by wiring active checkout lineage directly into `VersionHistoryDialog`.

## Scope

- Load active checkout relation inside `VersionHistoryDialog`.
- Show an active checkout lineage banner when a document is checked out.
- Offer one-click `Compare checkout lineage` that compares:
  - checkout baseline version
  - current version

## Design

### Why this slice

After the previous phases, Athena already exposes:

- checkout baseline/current metadata
- version-level baseline/current markers
- virtual checkout relation metadata

The next smallest high-value step is to let operators act on that relationship directly from version history, rather than manually hunting for the two relevant versions.

### UX

When version history opens for a checked-out document:

- a warning banner shows:
  - checkout owner
  - baseline version label
  - current version label
  - keep-checked-out capability
- if both baseline and current versions are present in the loaded history, a `Compare checkout lineage` action opens the compare dialog prefilled with `baseline -> current`

This is a pragmatic operator-first step toward working-copy/source ergonomics without needing a full working-copy node model.

## Files

- `ecm-frontend/src/components/dialogs/VersionHistoryDialog.tsx`
- `ecm-frontend/src/services/nodeService.ts`

## Claude Code

Claude Code was used as a parallel design assistant to validate that `VersionHistoryDialog` was the smallest next place to make checkout source relationship operational instead of merely visible. Final implementation and validation were completed in this workspace.
