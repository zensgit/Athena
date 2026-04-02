# Phase 367G: FileList Lock Badge

## Goal

Extend Athena lock visibility from detail view into browse view so users can see lock state before opening a document.

This phase adds the smallest browse-level lock indicator possible:

- a lock badge beside the file name,
- with a tooltip showing the lock owner when available,
- and no extra network calls.

## Delivered

- Added shared file-list lock badge helper for tooltip text.
- Added lock badge rendering to the `FileList` DataGrid name cell.
- Added lock badge rendering to the `FileList` grid/card name row.
- Reused existing `Node.locked` and `Node.lockedBy` data already returned in list payloads.
- Added focused unit tests for lock badge tooltip logic.

## Design

This phase deliberately avoids:

- new API calls,
- new columns,
- action gating,
- and bulk UI redesign.

Why this slice first:

- browse-level awareness is the first thing operators need,
- `FileList` already has compact name render points in both list and grid modes,
- an inline icon plus tooltip is enough to expose lock state without disturbing layout.

## Why This Matters

Compared with the benchmark, Athena now has:

- detail-level lock diagnostics in preview,
- and browse-level lock visibility in the main file listing.

That combination makes lock state more legible across everyday workflows than a single backend status alone.

## Claude Code Usage

Claude Code was used as a parallel design assistant to confirm that a browse-level lock badge in `FileList` was the smallest high-value next UX slice after preview lock-info consumption. Final implementation and validation were completed in this workspace flow.
