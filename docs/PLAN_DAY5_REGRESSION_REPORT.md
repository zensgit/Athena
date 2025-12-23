# Day 5 Regression Report

Date: 2025-12-23 (local)

## Scope
- Run targeted regression smoke to validate core flows.

## Execution
- Command: `ECM_API=http://localhost:7700 ECM_TOKEN=<token> scripts/smoke.sh`
- Exit code: `0`

## MCP UI Regression (manual)
- Frontend: `http://localhost:5500`
- Search query: `J0924032`
- Result: clicking the search result navigated to folder `mcp-api-rerun-1766045274566`.
- View: opened Actions menu → View on `J0924032-02上罐体组件v2-模型.pdf`; PDF viewer loaded with page controls (1/1).
- Observation: clicking `e2e-preview-*` results previously navigated to a deleted node (Folder not found), likely stale index entries.
- Limitation: DevTools screenshot capture timed out once the PDF viewer was open; no screenshot artifact produced.

## Search Index Cleanup
- Code fix: `SearchIndexService.updateNodeChildren` now rehydrates descendant nodes from DB and deletes missing entries; `EcmEventListener` syncs descendants on folder delete.
- Operation: `POST /api/v1/search/index/rebuild` (admin token)
- Result: `documentsIndexed=453`

## Frontend Stale Result Guard
- Behavior: Search results verify the node before navigation; missing nodes are removed from the list with a user-facing toast.
- Code: `ecm-frontend/src/pages/SearchResults.tsx`
- Verification: MCP flow created `staleguardY1766466940.txt`, searched, deleted via API, clicked View → result removed, “Showing 0 of 1 results,” and toast displayed (“This item is no longer available...”).

## Playwright E2E
- Command: `npx playwright test e2e/pdf-preview.spec.ts e2e/search-view.spec.ts`
- Result: `4 passed` (14.8s)
- Covered: search results → preview
- Covered: PDF preview dialog and controls
- Covered: PDF preview fallback to server render
- Covered: file browser view action opens preview

## Coverage (from smoke log)
- Health + system status
- Upload + search indexing
- Saved searches
- Correspondent filters + facets
- Copy/Move between folders
- Share link creation
- Tag + category assignment
- Advanced + faceted search
- Workflow approval start/complete
- Trash move/restore

## Artifacts
- `tmp/day5-smoke.log`
- `tmp/day5-smoke.exit`

## Notes
- Smoke suite completed successfully with no errors reported.
- 2025-12-23 rerun: ClamAV reported enabled but not ready within 30s; smoke skipped EICAR and continued successfully.
- 2025-12-23 rerun #2: ClamAV still unavailable after 30s (EICAR skipped). Saved search execution warned about indexing delay once (document not returned), then continued OK.
- 2025-12-23 ClamAV restart + verify: `scripts/verify.sh --no-restart --smoke-only --skip-build` passed (clamav-health OK, smoke OK, verify-wopi OK).
- 2025-12-23 EICAR rerun: ClamAV healthy; EICAR upload correctly rejected (HTTP 400) during `scripts/smoke.sh`.

## Playwright E2E (full run)
- Command: `npm run e2e` (Playwright)
- Result (initial): 11 passed, 2 failed
- Failures (initial):
  - `e2e/ui-smoke.spec.ts:74` timed out waiting for `Edit Online` menu item.
  - `e2e/ui-smoke.spec.ts:502` timed out waiting for `Edit Online` menu item.
- Artifacts (initial):
  - `test-results/ui-smoke-UI-smoke-browse-u-0d494-py-move-facets-delete-rules-chromium/`
  - `test-results/ui-smoke-UI-smoke-PDF-uplo-1fde1-version-history-edit-online-chromium/`

## Playwright E2E (rerun after fix)
- Fix: allow WOPI “Edit Online” in list actions for PDF/TXT/CSV (frontend menu gating).
- Command: `npm run e2e`
- Result: `13 passed`
- Note: antivirus section still reports unavailable; EICAR skipped (as expected).

## Playwright E2E (post-editor guard)
- Change: WOPI health pre-check in editor route to surface clear error when discovery is unavailable.
- Command: `npm run e2e`
- Result: `13 passed`
- Note: ClamAV healthy; EICAR upload correctly rejected (HTTP 400).
