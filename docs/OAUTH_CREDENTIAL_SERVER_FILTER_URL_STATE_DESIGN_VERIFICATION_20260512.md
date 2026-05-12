# OAuth Credential Server Filter URL State

Date: 2026-05-12

## Context

The OAuth Credential Store admin page had two server-backed filters:

- `ownerType`
- `provider`

Those filters controlled the backend inventory request, but they were local
component state. Refreshing the page or sharing a link lost the selected owner
type / provider view. The previous slice made the local Provider Revoke
capability filter URL-addressable through `revokeCapability`; this slice closes
the adjacent server-filter gap.

## Design

`/admin/oauth-credentials` now persists server-backed filters in the query
string:

| Query string | Backend inventory filter |
|---|---|
| no `ownerType` parameter | no owner-type filter |
| `?ownerType=MAIL_ACCOUNT` | `ownerType=MAIL_ACCOUNT` |
| no `provider` parameter | no provider filter |
| `?provider=GOOGLE` | `provider=GOOGLE` |

The page keeps the query string as the source of truth for applied filters:

- initial render hydrates the Owner type and Provider controls from
  `ownerType` and `provider`;
- applying filters writes trimmed `ownerType` and selected `provider` to the
  URL;
- clearing either control removes only that query parameter;
- changing server filters preserves unrelated query parameters, including
  `revokeCapability`;
- invalid provider query values fall back to the unfiltered provider view;
- the refresh button reloads the currently URL-applied server filters.

No backend endpoint, DTO, migration, security rule, or token/revoke behavior
changed.

## Verification

| Gate | Command | Result |
|---|---|---|
| Targeted Jest | `cd ecm-frontend && CI=true npm test -- --runTestsByPath src/pages/OAuthCredentialAdminPage.test.tsx --watchAll=false --runInBand` | 1 suite, 24 tests passed |
| OAuth admin preflight | `MAVEN_BIN=/tmp/codex-maven/apache-maven-3.9.11/bin/mvn bash scripts/oauth-credential-admin-preflight.sh` | passed: backend 47/47, frontend 38/38, lint clean, production build compiled successfully with the existing CRA bundle-size advisory |

## GitHub Actions

Run: `25724928039`

Commit: `dd6a08df58c531cc3eb441efd961b7aa0795b1bc`

Result: 7/7 jobs passed.

| Job | Result |
|---|---|
| Backend Verify | passed in 2m16s |
| Frontend Build & Test | passed in 10m21s |
| Phase C Security Verification | passed in 5m9s |
| Property Encryption Closeout Gate | passed in 4m41s |
| Frontend E2E Core Gate | passed in 14m38s |
| Acceptance Smoke (3 admin pages) | passed in 7m2s |
| Phase 5 Mocked Regression Gate | passed in 6m41s |

Targeted coverage added:

- applying Owner type and Provider writes `ownerType` and `provider` to the URL;
- Owner type values are trimmed before being applied to URL state and backend
  filter calls;
- loading `/admin/oauth-credentials?ownerType=MAIL_ACCOUNT&provider=CUSTOM`
  hydrates the controls and calls the backend inventory request with those
  filters;
- clearing both server filters removes only `ownerType` and `provider` while
  preserving `revokeCapability`.

Notes:

- The targeted Jest and preflight runs print the existing React Router v7
  future-flag warnings from the `MemoryRouter` test harness; the suites pass.
- The production build still prints the existing CRA bundle-size advisory.

## Files Changed

- `ecm-frontend/src/pages/OAuthCredentialAdminPage.tsx`
- `ecm-frontend/src/pages/OAuthCredentialAdminPage.test.tsx`
- `docs/OAUTH_CREDENTIAL_STORE_CLOSEOUT_TODO_20260507.md`
- `docs/OAUTH_CREDENTIAL_SERVER_FILTER_URL_STATE_DESIGN_VERIFICATION_20260512.md`

## Remaining Work

- Microsoft provider-side revoke remains intentionally unsupported for the
  reasons captured in
  `docs/OAUTH_CREDENTIAL_PROVIDER_REVOKE_MICROSOFT_CUSTOM_DESIGN_FOLLOWUP_20260507.md`.
- This slice does not add new server-side filter dimensions; it only makes the
  existing owner/provider filters refreshable and shareable.
