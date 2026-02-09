# PHASE1 P66 - Mail Reporting Default Range (30 Days)

## Goal
Reduce confusion in the **Mail Automation -> Mail Reporting** panel when the most recent processed mail is older than the default range.

Previously, the reporting UI defaulted to **Last 7 days**. In dev/test environments, it's common for processed mail history to be older than 7 days, which makes the report appear empty even though historical data exists.

## Change Summary
- Frontend-only: default the **Days** filter in Mail Reporting to **Last 30 days**.
- Keep the existing selectable options: `7, 14, 30, 60, 90`.
- No backend API/DB changes.

## Implementation Details
- `ecm-frontend/src/pages/MailAutomationPage.tsx`
  - Change the initial state:
    - from: `const [reportDays, setReportDays] = useState(7);`
    - to: `const [reportDays, setReportDays] = useState(30);`

## Acceptance Criteria
1. Visiting `/admin/mail` triggers the initial report query with `days=30`.
2. The report "Days" selector shows **Last 30 days** by default.
3. Users can still switch back to shorter ranges (e.g. 7 days) when needed.

