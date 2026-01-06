# Verification Index (2025-12-28)

## Current Dashboard (2026-01-06)
- `docs/VERIFICATION_DASHBOARD_20260106.md`
  - Consolidated links to recent backend, UI, and E2E verification

## UI Verification
- `docs/VERIFICATION_UI_PDF_ANNOTATION_20251228.md`
  - PDF context menu includes View + Annotate
  - Viewer shows read-only banner and annotation mode

## UI E2E
- `docs/VERIFICATION_UI_E2E_20251228.md`
  - Playwright `npm run e2e`: 15/15 passed
- `docs/VERIFICATION_E2E_FULL_RUN_20260106.md`
  - Playwright full suite: 17/17 passed
- `docs/VERIFICATION_E2E_VERSION_HISTORY_ACTIONS_20260106.md`
  - Version history download + restore flow (latest)
- `docs/VERIFICATION_E2E_SHARE_LINK_ACCESS_20260106.md`
  - Share link password/deactivate/access-limit validation (latest)

## Frontend Tests
- `docs/VERIFICATION_FRONTEND_LINT_TEST_20260106.md`
  - ESLint + React test suite pass
- `docs/VERIFICATION_ROUTER_FUTURE_FLAGS_20260106.md`
  - React Router future flags enabled for tests
- `docs/VERIFICATION_APP_TEST_SUSPENSE_ACT_20260106.md`
  - App smoke test Suspense act warning cleared
- `docs/VERIFICATION_MAINLAYOUT_MENU_TEST_STRENGTHENING_20260106.md`
  - MainLayout menu assertions for admin/viewer roles
- `docs/VERIFICATION_PRIVATE_ROUTE_TESTS_20260106.md`
  - PrivateRoute redirects and role gating
- `docs/VERIFICATION_PRIVATE_ROUTE_CALLBACK_SPINNER_20260106.md`
  - PrivateRoute spinner on Keycloak callback params
- `docs/VERIFICATION_PRIVATE_ROUTE_LOGIN_IN_PROGRESS_20260106.md`
  - PrivateRoute spinner on login-in-progress flag
- `docs/VERIFICATION_UNAUTHORIZED_ROUTE_TEST_20260106.md`
  - Unauthorized route renders expected copy

## Backend Tests
- `docs/VERIFICATION_BACKEND_MVN_TEST_20260106.md`
  - `mvn test`: 53 tests passed
- `docs/VERIFICATION_BACKEND_MVN_VERIFY_20260106.md`
  - `mvn verify`: 53 tests passed

## Integration Health + Smoke
- `docs/VERIFICATION_YUANTUS_INTEGRATIONS_20251228.md`
  - Yuantus integrations health OK (Athena/CAD-ML/Dedup)
  - Post-restart validation OK
  - Athena API smoke OK
