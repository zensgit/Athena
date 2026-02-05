# Athena ECM Rollup Changelog (2026-02-05)

## Summary
This rollup consolidates recent feature phases across mail automation, search, permission templates, preview retry handling, and version compare UX. The changes add visibility and control for admins (reporting, diagnostics, version history/compare, export), improve search transparency (highlights/explainability), and harden preview recovery flows.

## Backend
- Added permission template versioning and version detail endpoints.
- Added mail reporting service and controller/test support.
- Added search highlight helper and explainability integration while retaining ACL filtering.

## Frontend
- Permission templates: history dialog, compare dialog, change summary, diff table, CSV export.
- Search results: preview retry status (attempts/next retry), bulk retry for failed previews.
- Version history: compare summary block.
- Mail automation: reporting panel, diagnostics enhancements, connection status summary.

## Tests
- Backend: `cd ecm-core && mvn test` (138 tests, BUILD SUCCESS).
- Frontend: `ECM_E2E_SKIP_LOGIN=1 ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 npx playwright test` (36 passed).

## Docs
- Phase docs (47–53) for design and verification.
- Rollup design/dev/verification docs.
