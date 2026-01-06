# Release Notes - 2026-01-06

## Summary
- Hardened audit export validation and improved admin UX feedback.
- Expanded search ACL coverage and added ES pagination/deleted filtering checks.
- Added share-link access E2E coverage and version history action tests.
- Improved PDF preview empty-state handling and cleaned duplicate JSON dependencies.
- Added CI backend tests and verification dashboard updates.

## Changes
- Backend: audit export range validation + export count header; max range exposed via retention info.
- Backend: search ACL edge-case tests and ES pagination/deleted filtering coverage.
- Backend: removed duplicate org.json test dependency.
- Frontend: audit export feedback + max-range guidance; PDF preview empty-state messaging.
- E2E: new version history action + share link access specs; full suite run recorded.
- CI: backend job now runs `mvn test`.
- Docs: verification dashboard + expanded verification index.

## Testing
- ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 npx playwright test (17 passed)
- cd ecm-core && mvn test (53 tests)
- cd ecm-core && mvn verify (53 tests)
- cd ecm-frontend && npm run lint
- cd ecm-frontend && CI=true npm test -- --watchAll=false (3 suites, 5 tests)

## Notes
- React Router future-flag warnings appear during frontend tests (non-blocking).
