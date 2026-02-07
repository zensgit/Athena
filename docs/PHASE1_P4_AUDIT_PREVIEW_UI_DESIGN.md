# Audit + Preview UI P4 — Event Labels + Queue Details

Date: 2026-02-06

## Goals
- Improve audit readability by formatting event type labels.
- Surface preview retry details (attempts/next retry) in search result tooltips.

## Design
### 1) Audit event type labels
- Convert `EVENT_TYPE` to title case labels in the Admin Dashboard.
- Apply the same formatting to the event type autocomplete.

### 2) Preview queue tooltip detail
- When preview queue status is known, show attempts/next retry alongside failure reasons in the preview status tooltip.

## Files Changed
- `ecm-frontend/src/pages/AdminDashboard.tsx`
- `ecm-frontend/src/pages/SearchResults.tsx`

