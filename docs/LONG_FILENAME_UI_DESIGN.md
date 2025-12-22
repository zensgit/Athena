# Long Filename UI Adjustment Design

## Goal
Improve readability when filenames wrap to three lines by slightly reducing font size and line-height, without changing layout density.

## Changes
- Search results cards: reduce font size and line height when line clamp is 3 lines.
- File browser (grid/list): reduce font size and line height when line clamp is 3 lines.

## Rationale
Long filenames currently wrap into three lines but appear visually heavy; a modest font reduction improves balance while keeping information visible.

## Files
- `ecm-frontend/src/pages/SearchResults.tsx`
- `ecm-frontend/src/components/browser/FileList.tsx`
