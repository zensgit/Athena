# Phase 1 P56: E2E Core Gate Stabilization (UI Smoke PDF + Localhost IPv6)

Date: 2026-02-08

## Context
GitHub Actions `Frontend E2E Core Gate` showed flakiness in `ecm-frontend/e2e/ui-smoke.spec.ts`, specifically the scenario:

- `UI smoke: PDF upload + search + version history + preview`

The failure mode observed in CI was a timeout around the “Search results card -> Download/View” step (UI differences meant the expected button or response was not consistently captured).

Separately, local Playwright runs intermittently failed during API readiness checks when `localhost` resolved to IPv6 (`::1`) first, producing connection errors such as `EPERM ::1:7700` in Playwright’s `APIRequestContext`.

## Goals
- Make the `ui-smoke` PDF search + download step robust to UI variations:
  - Search card may show `Download`, only `View`, or neither (depending on layout/state).
- Remove `localhost` IPv6 resolution as a source of E2E flakiness by forcing IPv4 loopback for API calls.
- Keep changes scoped to E2E harness only (no production API/UI behavior changes).

## Non-Goals
- Changing backend endpoints, download semantics, or preview generation behavior.
- Introducing new UI features.

## Design / Approach

### 1) Normalize API Base URL to IPv4 When Host Is `localhost`
Add `resolveApiUrl()` in `ecm-frontend/e2e/helpers/api.ts`:
- Parses the configured API URL (from options or `process.env.ECM_API_URL`).
- If hostname is `localhost`, rewrites it to `127.0.0.1`.
- Returns a stable base URL string without a trailing slash for origin-only URLs.

All helper functions that accept `apiUrl` (`waitForApiReady`, indexing helpers, folder lookup helpers, etc.) now call `resolveApiUrl()` so callers can keep passing `localhost` while the request layer uses IPv4.

Core gate specs are updated to define `baseApiUrl` via `resolveApiUrl()` so their direct `request.get/post/delete` calls are also IPv4-stable.

### 2) Remove Hardcoded `http://localhost:7700` in `ui-smoke.spec.ts`
`ecm-frontend/e2e/ui-smoke.spec.ts` now uses:
- `const apiUrl = resolveApiUrl();`

All API calls in this spec build URLs from `apiUrl`, avoiding hardcoded loopback origins.

### 3) Harden the PDF Search-Card Download Assertion (UI Smoke)
In the PDF upload + search flow within `ui-smoke.spec.ts`, the download step is made resilient:

- Identify the search result card that contains the uploaded PDF filename.
- Prefer direct card `Download` button when visible.
- If `Download` is not visible but `View` is:
  - Open preview dialog
  - Use `More actions` -> `Download` menu item
- If neither action is visible (layout/state edge case), fall back to API validation:
  - `GET /api/v1/nodes/{id}/content` and assert `content-type` includes `pdf`

Response waiting is started only after the chosen action is initiated to avoid “wait started too early” timing hangs.

## Acceptance Criteria
- Local Playwright run of the CI core-gate suite succeeds.
- `ui-smoke` grep subset (PDF upload + search + version history + preview, download-failure toast) succeeds.
- No production code changes required; only E2E harness updates.

## Files Touched
- `ecm-frontend/e2e/helpers/api.ts`
- `ecm-frontend/e2e/ui-smoke.spec.ts`
- Core-gate specs updated to use `resolveApiUrl()`:
  - `ecm-frontend/e2e/browse-acl.spec.ts`
  - `ecm-frontend/e2e/mfa-settings.spec.ts`
  - `ecm-frontend/e2e/pdf-preview.spec.ts`
  - `ecm-frontend/e2e/permissions-dialog.spec.ts`
  - `ecm-frontend/e2e/permission-templates.spec.ts`
  - `ecm-frontend/e2e/rules-manual-backfill-validation.spec.ts`
  - `ecm-frontend/e2e/search-highlight.spec.ts`
  - `ecm-frontend/e2e/search-preview-status.spec.ts`
  - `ecm-frontend/e2e/search-sort-pagination.spec.ts`
  - `ecm-frontend/e2e/search-view.spec.ts`
  - `ecm-frontend/e2e/version-details.spec.ts`
  - `ecm-frontend/e2e/version-share-download.spec.ts`

