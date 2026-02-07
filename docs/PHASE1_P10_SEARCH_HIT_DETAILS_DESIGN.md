# Search P10 — Field-Level Hit Details

Date: 2026-02-06

## Goal
Improve search explainability by showing which field matched and a short highlighted snippet, instead of only generic match chips.

## Design
- Reuse existing search response fields: `highlights` and `matchFields`.
- Build ordered hit details by:
  - prioritizing fields already in `matchFields`;
  - then appending remaining highlight fields.
- Display up to 3 hit detail rows per result card.
- Each row shows:
  - field label (chip),
  - highlighted snippet (`<em>...</em>` from backend highlight payload).

## Files Changed
- `ecm-frontend/src/pages/SearchResults.tsx`
