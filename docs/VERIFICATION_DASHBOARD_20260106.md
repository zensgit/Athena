# Verification Dashboard (2026-01-06)

## Latest Highlights
- Audit export: range validation + UI feedback + max-range guidance.
- Search ACL: edge-case coverage and ES pagination/deleted filtering.
- Share links: allowed IP validation + access control flows.
- Rules: notification integration and notification type parameter.
- Backend: `mvn test` + `mvn verify` pass.
- Frontend: lint + unit tests pass.
- E2E: full suite + version history/share link flows.

## Backend / API
- Audit export validation: `docs/VERIFICATION_AUDIT_EXPORT_RANGE_VALIDATION_20260106.md`
- Audit export max-range boundary: `docs/VERIFICATION_AUDIT_EXPORT_MAX_RANGE_BOUNDARY_20260106.md`
- Backend test run: `docs/VERIFICATION_BACKEND_MVN_TEST_20260106.md`
- Backend verify run: `docs/VERIFICATION_BACKEND_MVN_VERIFY_20260106.md`
- Share link validation: `docs/VERIFICATION_SHARE_LINK_ALLOWED_IP_VALIDATION_20260106.md`
- Share link update validation: `docs/VERIFICATION_SHARE_LINK_UPDATE_ALLOWED_IP_VALIDATION_20260106.md`
- Share link CIDR parsing: `docs/VERIFICATION_SHARE_LINK_CIDR_20260105.md`
- Search ACL edge cases: `docs/VERIFICATION_SEARCH_ACL_EDGE_CASE_TESTS_20260106.md`
- Search pagination/deleted filter: `docs/VERIFICATION_SEARCH_PAGINATION_DELETED_TESTS_20260106.md`
- Search disabled guards: `docs/VERIFICATION_SEARCH_DISABLED_GUARD_20260106.md`
- Search available facets disabled: `docs/VERIFICATION_SEARCH_AVAILABLE_FACETS_DISABLED_20260106.md`
- Rule notifications: `docs/VERIFICATION_RULE_NOTIFICATION_INTEGRATION_20260106.md`
- Rule action notification type: `docs/VERIFICATION_RULE_ACTION_NOTIFICATION_TYPE_20260106.md`
- Dependency hygiene: `docs/VERIFICATION_JSON_DEPENDENCY_CLEANUP_20260106.md`

## UI / UX
- Audit export UI feedback: `docs/VERIFICATION_AUDIT_EXPORT_FEEDBACK_20260106.md`
- Audit export max-range UI: `docs/VERIFICATION_AUDIT_EXPORT_MAX_RANGE_UI_20260106.md`
- Frontend lint + unit tests: `docs/VERIFICATION_FRONTEND_LINT_TEST_20260106.md`
- Router future flags: `docs/VERIFICATION_ROUTER_FUTURE_FLAGS_20260106.md`
- App test Suspense act warning: `docs/VERIFICATION_APP_TEST_SUSPENSE_ACT_20260106.md`
- MainLayout menu tests: `docs/VERIFICATION_MAINLAYOUT_MENU_TEST_STRENGTHENING_20260106.md`
- PrivateRoute tests: `docs/VERIFICATION_PRIVATE_ROUTE_TESTS_20260106.md`
- PrivateRoute callback spinner: `docs/VERIFICATION_PRIVATE_ROUTE_CALLBACK_SPINNER_20260106.md`
- PrivateRoute login-in-progress spinner: `docs/VERIFICATION_PRIVATE_ROUTE_LOGIN_IN_PROGRESS_20260106.md`
- Unauthorized route render: `docs/VERIFICATION_UNAUTHORIZED_ROUTE_TEST_20260106.md`
- PDF preview empty-state: `docs/VERIFICATION_PDF_PREVIEW_EMPTY_STATE_20260106.md`
- PDF preview layout/fit mode checks: `docs/VERIFICATION_PDF_PREVIEW_LAYOUT_20251225.md`, `docs/VERIFICATION_PDF_PREVIEW_FITMODE_DEFAULT_20251225.md`, `docs/VERIFICATION_PDF_PREVIEW_FITMODE_OVERRIDE_20251225.md`, `docs/VERIFICATION_PDF_PREVIEW_FITMODE_SHORTCUTS_20251225.md`

## E2E / Automation
- E2E version history actions: `docs/VERIFICATION_E2E_VERSION_HISTORY_ACTIONS_20260106.md`
- E2E share link access: `docs/VERIFICATION_E2E_SHARE_LINK_ACCESS_20260106.md`
- Full E2E run: `docs/VERIFICATION_E2E_FULL_RUN_20260106.md`
- UI E2E (historical): `docs/VERIFICATION_UI_E2E_20251228.md`

## Full Index
- For the complete list: `docs/VERIFICATION_INDEX_20251228.md` and `docs/VERIFICATION_*.md`.
