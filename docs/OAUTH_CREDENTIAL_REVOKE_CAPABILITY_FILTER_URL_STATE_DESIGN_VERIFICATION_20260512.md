# OAuth Credential Revoke Capability Filter URL State

Date: 2026-05-12

## Context

The OAuth Credential Store admin page already has local Provider Revoke
capability filters:

- `All`
- `Provider revoke ready`
- `Provider revoke blocked`
- `CUSTOM revoke gaps`

Those filters are useful for operator triage, but they were component-local
state. Refreshing the page or sharing a link returned the view to `All`, so a
blocked-provider or CUSTOM endpoint-gap investigation could not be bookmarked.

## Design

`/admin/oauth-credentials` now persists the local revoke capability filter in
the query string:

| Query string | View |
|---|---|
| no `revokeCapability` parameter | all credentials |
| `?revokeCapability=ready` | credentials where `providerRevokeSupported === true` |
| `?revokeCapability=blocked` | credentials where `providerRevokeSupported === false` |
| `?revokeCapability=custom-endpoint-gap` | CUSTOM credentials blocked by missing revoke endpoint |
| any other `revokeCapability` value | all credentials |

The implementation keeps one source of truth:

- `useSearchParams()` owns the selected local filter.
- Filter button clicks update only `revokeCapability`.
- Existing query parameters are preserved when changing or clearing the local
  filter.
- The update uses `replace: true` to avoid polluting browser history with local
  table-filter toggles.

No backend endpoint, DTO, migration, provider capability decision, or token
semantics changed.

## Verification

| Gate | Command | Result |
|---|---|---|
| Targeted Jest | `cd ecm-frontend && CI=true npm test -- --runTestsByPath src/pages/OAuthCredentialAdminPage.test.tsx --watchAll=false --runInBand` | 1 suite, 22 tests passed |
| OAuth admin preflight | `MAVEN_BIN=/tmp/codex-maven/apache-maven-3.9.11/bin/mvn bash scripts/oauth-credential-admin-preflight.sh` | passed: backend 47/47, frontend 36/36, lint clean, production build compiled successfully with the existing CRA bundle-size advisory |
| Whitespace | `git diff --check` | clean |

## GitHub Actions

Run: `25722871407`

Commit: `f14cb0428e2f388cc8c0369ef4d94d87eb042e7d`

Result: 7/7 jobs passed.

| Job | Result |
|---|---|
| Backend Verify | passed in 2m12s |
| Frontend Build & Test | passed in 10m13s |
| Phase C Security Verification | passed in 5m10s |
| Phase 5 Mocked Regression Gate | passed in 9m07s |
| Frontend E2E Core Gate | passed in 12m34s |
| Acceptance Smoke (3 admin pages) | passed in 6m50s |
| Property Encryption Closeout Gate | passed in 4m48s |

Targeted coverage added:

- clicking `Provider revoke ready` writes `?revokeCapability=ready`;
- clicking `Provider revoke blocked` writes `?revokeCapability=blocked`;
- clicking `All` clears only `revokeCapability`;
- loading `/admin/oauth-credentials?revokeCapability=blocked` starts in the
  blocked view while preserving unrelated query parameters.

Notes:

- The targeted Jest and preflight runs print the existing React Router v7
  future-flag warnings after wrapping this page in `MemoryRouter`; the suites
  still pass.
- The production build still prints the existing CRA bundle-size advisory.

## Files Changed

- `ecm-frontend/src/pages/OAuthCredentialAdminPage.tsx`
- `ecm-frontend/src/pages/OAuthCredentialAdminPage.test.tsx`
- `docs/OAUTH_CREDENTIAL_STORE_CLOSEOUT_TODO_20260507.md`
- `docs/OAUTH_CREDENTIAL_REVOKE_CAPABILITY_FILTER_URL_STATE_DESIGN_VERIFICATION_20260512.md`

## Remaining Work

- Microsoft provider-side revoke remains intentionally unsupported for the
  reasons captured in
  `docs/OAUTH_CREDENTIAL_PROVIDER_REVOKE_MICROSOFT_CUSTOM_DESIGN_FOLLOWUP_20260507.md`.
- This slice does not make owner/provider server filters URL-addressable; it
  only persists the local Provider Revoke capability triage filter.
