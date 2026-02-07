# Mail Automation P17 — Diagnostics Processed Time Range Filters

Date: 2026-02-06

## Goal
Improve diagnostics precision by allowing operators to filter mail activity by processed time window.

## Design
- Add two frontend filters in Recent Mail Activity:
  - `Processed from`
  - `Processed to`
- Bind both filters to existing diagnostics API query parameters:
  - `processedFrom`
  - `processedTo`
- Apply the same time filters to CSV export to keep UI view and exported data consistent.
- Include both fields in `Clear filters` reset behavior.

## Implementation Notes
- Backend already supports `processedFrom/processedTo` in:
  - `GET /api/v1/integration/mail/diagnostics`
  - `GET /api/v1/integration/mail/diagnostics/export`
- This phase is frontend-only and reuses existing API contract.

## Files Changed
- `ecm-frontend/src/pages/MailAutomationPage.tsx`
