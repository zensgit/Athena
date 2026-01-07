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
- Audit export blank parameters: `docs/VERIFICATION_AUDIT_EXPORT_BLANK_PARAMS_20260106.md`
- Audit export response headers: `docs/VERIFICATION_AUDIT_EXPORT_RESPONSE_HEADERS_20260106.md`
- Audit retention info payload: `docs/VERIFICATION_AUDIT_RETENTION_INFO_TEST_20260106.md`
- Audit cleanup responses: `docs/VERIFICATION_AUDIT_CLEANUP_RESPONSE_TESTS_20260106.md`
- Audit recent default limit: `docs/VERIFICATION_AUDIT_RECENT_DEFAULT_LIMIT_20260106.md`
- Audit recent limit parameter: `docs/VERIFICATION_AUDIT_RECENT_LIMIT_PARAM_20260106.md`
- Rules recent default limit: `docs/VERIFICATION_RULES_RECENT_DEFAULT_LIMIT_20260106.md`
- Rules recent limit parameter: `docs/VERIFICATION_RULES_RECENT_LIMIT_PARAM_20260106.md`
- Rules summary days parameter: `docs/VERIFICATION_RULES_SUMMARY_DAYS_PARAM_20260106.md`
- Daily activity days parameter: `docs/VERIFICATION_DAILY_ACTIVITY_DEFAULT_DAYS_20260106.md`
- Top users limit parameter: `docs/VERIFICATION_TOP_USERS_LIMIT_PARAM_20260106.md`
- Dashboard aggregation: `docs/VERIFICATION_DASHBOARD_AGGREGATION_20260106.md`
- Analytics summary endpoint: `docs/VERIFICATION_ANALYTICS_SUMMARY_ENDPOINT_20260106.md`
- Analytics storage-by-MIME endpoint: `docs/VERIFICATION_ANALYTICS_STORAGE_MIMETYPE_ENDPOINT_20260106.md`
- Backend test run: `docs/VERIFICATION_BACKEND_MVN_TEST_20260106.md`
- Backend verify run: `docs/VERIFICATION_BACKEND_MVN_VERIFY_20260106.md`
- Share link validation: `docs/VERIFICATION_SHARE_LINK_ALLOWED_IP_VALIDATION_20260106.md`
- Share link update validation: `docs/VERIFICATION_SHARE_LINK_UPDATE_ALLOWED_IP_VALIDATION_20260106.md`
- Share link CIDR parsing: `docs/VERIFICATION_SHARE_LINK_CIDR_20260105.md`
- Search ACL edge cases: `docs/VERIFICATION_SEARCH_ACL_EDGE_CASE_TESTS_20260106.md`
- Search pagination/deleted filter: `docs/VERIFICATION_SEARCH_PAGINATION_DELETED_TESTS_20260106.md`
- Search disabled guards: `docs/VERIFICATION_SEARCH_DISABLED_GUARD_20260106.md`
- Search available facets disabled: `docs/VERIFICATION_SEARCH_AVAILABLE_FACETS_DISABLED_20260106.md`
- Suggested filters disabled: `docs/VERIFICATION_SEARCH_SUGGESTED_FILTERS_DISABLED_20260106.md`
- Advanced search include-deleted: `docs/VERIFICATION_SEARCH_ADVANCED_INCLUDE_DELETED_20260106.md`
- Advanced search createdBy precedence: `docs/VERIFICATION_SEARCH_ADVANCED_CREATEDBY_PRECEDENCE_20260106.md`
- Rule notifications: `docs/VERIFICATION_RULE_NOTIFICATION_INTEGRATION_20260106.md`
- Rule action notification type: `docs/VERIFICATION_RULE_ACTION_NOTIFICATION_TYPE_20260106.md`
- Dependency hygiene: `docs/VERIFICATION_JSON_DEPENDENCY_CLEANUP_20260106.md`

## UI / UX
- Audit export UI feedback: `docs/VERIFICATION_AUDIT_EXPORT_FEEDBACK_20260106.md`
- Audit export max-range UI: `docs/VERIFICATION_AUDIT_EXPORT_MAX_RANGE_UI_20260106.md`
- Frontend lint + unit tests: `docs/VERIFICATION_FRONTEND_LINT_TEST_20260106.md`
- Frontend build: `docs/VERIFICATION_FRONTEND_BUILD_20260106.md`
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
- Full verify.sh report: `docs/VERIFICATION_VERIFY_20260106.md`
- Full verify.sh report (2026-01-07 run): `docs/VERIFICATION_VERIFY_20260107.md`
- Full verify.sh report (2026-01-07 wopi flags): `docs/VERIFICATION_VERIFY_20260107_FULL_WOPI_FLAGS.md`
- verify.sh reporting artifacts: `docs/VERIFICATION_VERIFY_SCRIPT_REPORTING_20260106.md`
- verify.sh WOPI flags: `docs/VERIFICATION_VERIFY_WOPI_FLAGS_20260107.md`
- verify.sh WOPI flags (space form): `docs/VERIFICATION_VERIFY_WOPI_FLAGS_SPACE_20260107.md`
- verify.sh skip WOPI: `docs/VERIFICATION_VERIFY_SKIP_WOPI_20260107.md`
- verify.sh skip WOPI (full): `docs/VERIFICATION_VERIFY_SKIP_WOPI_FULL_20260107.md`
- verify.sh WOPI query missing: `docs/VERIFICATION_VERIFY_WOPI_QUERY_MISSING_20260107.md`
- verify.sh skip WOPI summary: `docs/VERIFICATION_VERIFY_SKIP_WOPI_SUMMARY_20260107.md`
- verify.sh WOPI status in report: `docs/VERIFICATION_VERIFY_WOPI_STATUS_REPORT_20260107.md`
- verify.sh WOPI reason in report: `docs/VERIFICATION_VERIFY_WOPI_REASON_REPORT_20260107.md`
- verify.sh step summary: `docs/VERIFICATION_VERIFY_STEP_SUMMARY_20260107.md`
- verify.sh step summary reasons: `docs/VERIFICATION_VERIFY_STEP_SUMMARY_REASON_20260107.md`
- verify.sh help output: `docs/VERIFICATION_VERIFY_HELP_20260107.md`
- WOPI auto-upload fallback: `docs/VERIFICATION_WOPI_AUTO_UPLOAD_20260106.md`
- WOPI sample cleanup: `docs/VERIFICATION_WOPI_SAMPLE_CLEANUP_20260106.md`
- UI E2E (historical): `docs/VERIFICATION_UI_E2E_20251228.md`

## Full Index
- For the complete list: `docs/VERIFICATION_INDEX_20251228.md` and `docs/VERIFICATION_*.md`.
