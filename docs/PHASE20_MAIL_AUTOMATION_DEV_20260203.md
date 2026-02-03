# Phase 20 Mail Automation: Fetch Summary Persistence (2026-02-03)

## Goal
Persist the latest mail fetch summary on the backend and surface it on the Mail Automation UI so operators see the last run after page reloads.

## Changes
### Backend
- Added in-memory tracking for the latest fetch summary and timestamp in `MailFetcherService`.
- Added endpoint `GET /api/v1/integration/mail/fetch/summary` to expose the latest summary and `fetchedAt` timestamp.

Files:
- `ecm-core/src/main/java/com/ecm/core/integration/mail/service/MailFetcherService.java`
- `ecm-core/src/main/java/com/ecm/core/integration/mail/controller/MailAutomationController.java`

### Frontend
- Added `getFetchSummary()` API call in mail automation service.
- Mail Automation page now loads and displays the last fetch summary/time from the backend during initial load and refresh.

Files:
- `ecm-frontend/src/services/mailAutomationService.ts`
- `ecm-frontend/src/pages/MailAutomationPage.tsx`

## Notes
- The summary is kept in memory and resets on service restart. It is updated on both scheduled and manual fetch runs.
- This aligns the UI with expected ECM operational behavior (showing the latest job outcome without re-triggering fetch).
