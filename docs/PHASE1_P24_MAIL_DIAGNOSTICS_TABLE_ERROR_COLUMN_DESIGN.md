# Mail Automation P24 — Diagnostics Processed Table Error Column

Date: 2026-02-06

## Goal
Expose failure detail directly in the processed messages table to reduce context switching during troubleshooting.

## Design
- Add `Error Message` column to `Processed Messages` table.
- For `ERROR` rows:
  - show compact error summary,
  - provide one-click copy action for full error text.
- For non-error rows, show `-`.
- Reuse existing clipboard + toast behavior from diagnostics error leaderboard actions.

## Files Changed
- `ecm-frontend/src/pages/MailAutomationPage.tsx`
