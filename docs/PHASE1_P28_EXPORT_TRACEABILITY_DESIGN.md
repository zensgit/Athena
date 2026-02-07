# Mail Automation P28 — Export Traceability & Filter Snapshot

Date: 2026-02-06

## Goal
Ensure diagnostics CSV export is traceable and reproducible from UI state.

## Design
### Backend
- Added export metadata enrichment in diagnostics CSV:
  - `RequestId`
  - `GeneratedBy`
  - `GeneratedAt`
  - `FilterAccountId`
  - `FilterRuleId`
  - `FilterStatus`
  - `FilterSubject`
  - `FilterProcessedFrom`
  - `FilterProcessedTo`
  - `SortBy`
  - `SortOrder`
- Controller now generates `requestId` per export call and resolves actor via security context.
- Audit payload now includes request id + sort/order for export events.

### Frontend
- Added diagnostics export scope preview text:
  - `Export scope snapshot: ...`
- Snapshot reflects active diagnostics filters and sort/order state.

## Files Changed
- `ecm-core/src/main/java/com/ecm/core/integration/mail/controller/MailAutomationController.java`
- `ecm-core/src/main/java/com/ecm/core/integration/mail/service/MailFetcherService.java`
- `ecm-frontend/src/pages/MailAutomationPage.tsx`
