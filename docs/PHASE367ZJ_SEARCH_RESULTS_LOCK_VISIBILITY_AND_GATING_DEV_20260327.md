# Phase367ZJ Search Results Lock Visibility And Gating

## Goal

Bring the regular `SearchResults` page up to the same lock-awareness level already present in advanced search and browse.

## Design

- Reuse the existing lock chip helper from advanced search:
  - `getSearchResultLockChip`
- Reuse the existing action-level lock guard from browse:
  - `getFileLockActionReason`
- Keep the change frontend-only.
- Limit lock gating to write-path actions on result cards:
  - `Annotate (PDF)`
  - `Edit Online`
- Keep read-path actions available:
  - `View`
  - `Download`
  - `View Online` when the user is already read-only

## UI Changes

- Result card headers now show a lock chip alongside the existing checkout chip when the node is locked.
- `Annotate (PDF)` now shows a disable reason when the document is locked by another user.
- `Edit Online` now shows a disable reason when the document is locked by another user.

## Why This Slice

- It closes the remaining ordinary-search gap where checkout was visible but lock state was not.
- It aligns normal search with the operator detail already available in advanced search, preview, and browse.
- It is low conflict because it reuses existing frontend helpers and does not widen backend contracts.

## Benchmark Impact

- This does not add a richer persisted lock model.
- It does improve operator detail by making ordinary search results both lock-aware and action-safe, which is stronger than a bare status-only surface.
