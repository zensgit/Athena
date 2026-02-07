# Mail Automation P18 — Diagnostics Quick Time Range Presets

Date: 2026-02-06

## Goal
Reduce operator effort by providing one-click diagnostics time window presets.

## Design
- Add quick range actions under diagnostics filters:
  - `Last 24h`
  - `Last 7d`
  - `Last 30d`
  - `Clear time`
- Presets set both `Processed from` and `Processed to` using local datetime input format (`YYYY-MM-DDTHH:mm`).
- Reuse existing diagnostics filter binding so list reload and CSV export automatically respect the selected range.

## Files Changed
- `ecm-frontend/src/pages/MailAutomationPage.tsx`
