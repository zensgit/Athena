# Mail Automation P19 — Rule Error Leaderboard Details + Copy

Date: 2026-02-06

## Goal
Help operators move from high-level failure counts to actionable diagnostics without leaving the Mail Automation page.

## Design
- Extend the existing diagnostics rule error leaderboard.
- For each failed rule in leaderboard:
  - keep one-click filter action (`Status=ERROR + Rule`),
  - add a `Show details` toggle.
- Details panel shows up to the 3 most recent error records for that rule:
  - processed timestamp,
  - compact error summary.
- Add one-click copy action per error row to copy full error text.

## Implementation Notes
- Error details are derived from existing `recentProcessed` payload (frontend-only change).
- Details auto-collapse if the selected expanded rule is no longer present in current diagnostics window.

## Files Changed
- `ecm-frontend/src/pages/MailAutomationPage.tsx`
