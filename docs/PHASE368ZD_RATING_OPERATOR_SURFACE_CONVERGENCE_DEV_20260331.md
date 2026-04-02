# Phase 368ZD — Rating Operator Surface Convergence

> **Scope**: Extend rating from PropertiesDialog into FileList list/grid high-density surfaces
> **Date**: 2026-03-31

---

## 1. Problem Statement

Phase 368ZC delivered the rating backend + PropertiesDialog panel, but the primary
work surface (FileList list/grid view) had no rating visibility. Users had to open
the Properties dialog to see or toggle likes/stars.

## 2. What Was Built

### RatingBadge Component

New `components/ratings/RatingBadge.tsx` — a compact, self-loading badge that:

- Calls `getSummary()` + `getMyRatings()` on mount
- Shows a like toggle button (ThumbUp filled/outlined)
- Shows like count (when > 0)
- Shows star average chip with Star icon (when ratings exist)
- Single-click like toggle (calls `rate()` / `removeRating()` inline)
- `compact` prop: always renders (even with zero counts) for consistent column width

### FileList List View (DataGrid)

New "Rating" column (`width: 110`, `sortable: false`) added before the actions column.
Renders `<RatingBadge nodeId={row.id} compact />` for DOCUMENT nodes, null for folders.

### FileList Grid View (Card)

`<RatingBadge nodeId={node.id} compact />` added after the file type / preview status
chips row, visible on every document card.

### Frontend Tests

`RatingBadge.test.tsx` — 7 Jest + RTL tests covering:
- Empty state rendering (non-compact hides, compact shows)
- Like count and star average display
- Filled vs outline like icon based on user state
- Like toggle calls `rate()` when not liked
- Like toggle calls `removeRating()` when already liked
- Compact mode always renders

## 3. Files Created

| File | Purpose |
|------|---------|
| `components/ratings/RatingBadge.tsx` | Compact self-loading rating badge |
| `components/ratings/RatingBadge.test.tsx` | 7 Jest tests |

## 4. Files Modified

| File | Change |
|------|--------|
| `components/browser/FileList.tsx` | +RatingBadge import; +Rating column in list view; +RatingBadge in grid card |

## 5. NOT Modified

Backend unchanged. All preview/rendition/search/ops-governance files untouched.
