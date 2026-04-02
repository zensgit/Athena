# Phase367W: Version History Load Checkout Lineage

## Goal

Make checkout lineage compare useful for long version histories by letting `VersionHistoryDialog` keep loading paged version history until checkout baseline/current versions are present.

## Scope

- Detect when active checkout lineage is only partially present in the loaded version page window.
- Add `Load checkout lineage versions` to the active checkout banner.
- Reuse existing paged version history API and existing checkout relation metadata.

## Design

### Problem

Before this phase, checkout lineage compare only worked immediately when:

- baseline version was already in the loaded page window
- current version was already in the loaded page window

That meant heavily versioned documents could still force manual “Load more” loops before lineage compare became usable.

### Behavior

When the dialog knows:

- the document is actively checked out
- checkout baseline id exists
- baseline/current are not both present in loaded versions
- more history pages exist

it now shows `Load checkout lineage versions`.

That action:

- keeps requesting paged version history
- appends additional pages
- stops when both baseline and current are present, or when history is exhausted

### Why this matters

This is a direct operator usability improvement:

- no new backend contract
- no heavy working-copy model
- real reduction in manual paging friction

## Files

- `ecm-frontend/src/components/dialogs/VersionHistoryDialog.tsx`

## Claude Code

Claude Code was used as a parallel design assistant to validate that lineage-aware incremental loading was the smallest next useful slice after contextual compare actions. Final implementation and validation were completed in this workspace.
