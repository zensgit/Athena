# Phase 14 E2E Stability (Dev) - 2026-02-02

## Goals
- Stabilize E2E flows for search, PDF preview, version history, and mail automation.
- Remove Elasticsearch sort error caused by `_id` fielddata access.
- Add UI affordances for mail automation connection status and preview source visibility.
- Enable optional auth bypass for Playwright E2E runs.

## Scope
- Backend: search indexing and sort behavior.
- Frontend: mail automation summary UI, preview source chip, version history compare/ checksum column.
- E2E: API helpers, login bypass, retry logic for version check-in, search reindex helper usage.

## Changes
### Backend
- Added `refreshIndex()` and `indexDocumentsByName()` support; exposed reindex-by-query endpoint and refresh param:
  - `ecm-core/src/main/java/com/ecm/core/search/SearchIndexService.java`
  - `ecm-core/src/main/java/com/ecm/core/controller/SearchController.java`
  - `ecm-core/src/main/java/com/ecm/core/repository/NodeRepository.java`
- Fixed Elasticsearch relevance sort by removing `_id` fielddata usage and using `nameSort` as tiebreaker:
  - `ecm-core/src/main/java/com/ecm/core/search/FullTextSearchService.java`

### Frontend
- Mail Automation: added connection summary card with last success/failure and test connection action:
  - `ecm-frontend/src/pages/MailAutomationPage.tsx`
- Preview: added source chip with tooltip fallback reason:
  - `ecm-frontend/src/components/preview/DocumentPreview.tsx`
- Version history: added checksum column and compare dialog/menu item:
  - `ecm-frontend/src/components/dialogs/VersionHistoryDialog.tsx`
- Auth bypass flag for E2E (localStorage seed on init):
  - `ecm-frontend/src/services/authService.ts`
  - `ecm-frontend/Dockerfile` (build arg for `REACT_APP_E2E_BYPASS_AUTH`)

### E2E
- Added `reindexByQuery` helper and adopted refresh-aware reindexing in tests:
  - `ecm-frontend/e2e/helpers/api.ts`
  - `ecm-frontend/e2e/search-view.spec.ts`
  - `ecm-frontend/e2e/pdf-preview.spec.ts`
- Added skip-login support to mail automation + version history tests:
  - `ecm-frontend/e2e/mail-automation.spec.ts`
  - `ecm-frontend/e2e/version-share-download.spec.ts`
- Added retry logic for check-in to reduce transient failures:
  - `ecm-frontend/e2e/version-share-download.spec.ts`

## Notes
- Playwright can bypass login when:
  - Frontend is built with `REACT_APP_E2E_BYPASS_AUTH=1`.
  - Tests run with `ECM_E2E_SKIP_LOGIN=1` and a valid access token.
- `docker-compose.override.yml` uses a prebuilt frontend image; run `npm run build` and rebuild the container after UI changes.
