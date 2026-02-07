# Audit P11 — Quick Time Range Filters

Date: 2026-02-06

## Goal
Reduce manual input for audit filtering/export by adding one-click time windows in Admin Dashboard.

## Design
- Add quick range buttons: `24h`, `7d`, `30d`.
- On click:
  - set export preset to `custom`;
  - auto-fill `From` and `To` datetime fields;
  - keep existing filter/export endpoints unchanged.
- If user edits `From/To` manually or picks non-custom preset, quick range state becomes `custom` (not highlighted).

## Files Changed
- `ecm-frontend/src/pages/AdminDashboard.tsx`
