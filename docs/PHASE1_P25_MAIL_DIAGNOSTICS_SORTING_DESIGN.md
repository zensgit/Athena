# Mail Automation P25 — Diagnostics Sorting Controls + API Support

Date: 2026-02-06

## Goal
Allow operators to control diagnostics ordering and keep UI/URL/export/backend behavior aligned.

## Design
### Frontend
- Add diagnostics sort controls:
  - `Sort by`: `processedAt | status | rule | account`
  - `Order`: `desc | asc`
- Persist sort/order in:
  - URL query (`dSort`, `dOrder`)
  - local storage diagnostics filters payload
- Include sort/order in diagnostics fetch and CSV export requests.

### Backend
- Extend diagnostics endpoints with optional query params:
  - `sort`
  - `order`
- Apply resolved sort to processed-mail query (`PageRequest + Sort`).
- Include `SortBy`/`SortOrder` in CSV metadata header rows.
- Keep backward compatibility:
  - default `sort=processedAt`
  - default `order=desc`

## Files Changed
- `ecm-frontend/src/pages/MailAutomationPage.tsx`
- `ecm-frontend/src/services/mailAutomationService.ts`
- `ecm-core/src/main/java/com/ecm/core/integration/mail/controller/MailAutomationController.java`
- `ecm-core/src/main/java/com/ecm/core/integration/mail/service/MailFetcherService.java`
