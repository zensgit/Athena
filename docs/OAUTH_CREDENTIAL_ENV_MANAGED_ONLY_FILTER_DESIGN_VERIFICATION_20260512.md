# OAuth Credential Env-Managed Only Filter

Date: 2026-05-12

## Context

The OAuth Credential Store explicitly treats credential-key-only rows as an
operational boundary: Athena can refresh through an external secret reference,
but it cannot revoke or clear a token value that is not stored locally.

Before this slice, those rows were mixed into the broader `Provider revoke
blocked` view. Operators could see the backend unsupported reason row-by-row,
but they could not isolate the external-secret-only population quickly.

## Design

`/admin/oauth-credentials` now adds a local `Env-managed only` view.

The view matches credentials where:

- `credentialKeyConfigured === true`;
- `accessTokenStored === false`;
- `refreshTokenStored === false`.

This intentionally uses existing redacted inventory booleans. No backend DTO,
token handling, provider-revoke semantics, migration, or OAuth owner adapter
behavior changed.

UI changes:

- summary card: `Env-managed Only`;
- filter button: `Env-managed only (N)`;
- URL state: `?revokeCapability=env-managed-only`;
- active filter chip: `Revoke capability: Env-managed only`;
- empty state: `No OAuth credentials currently depend only on env-managed
  secrets.`

The new view is separate from `CUSTOM revoke gaps`:

- `CUSTOM revoke gaps` answers "which CUSTOM rows need a revoke endpoint";
- `Env-managed only` answers "which rows rely only on external secret material
  and therefore have no local token for Athena to revoke."

## Verification

| Gate | Command | Result |
|---|---|---|
| Targeted Jest | `cd ecm-frontend && CI=true npm test -- --runInBand --watchAll=false src/pages/OAuthCredentialAdminPage.test.tsx` | 1 suite, 30 tests passed |
| OAuth admin preflight | `MAVEN_BIN=/tmp/codex-maven/apache-maven-3.9.11/bin/mvn bash scripts/oauth-credential-admin-preflight.sh` | passed: backend 53/53, frontend 47/47, lint clean, production build compiled successfully with the existing CRA bundle-size advisory |

Targeted coverage added:

- env-managed credential-key-only rows are included;
- locally stored token rows are excluded;
- filter click writes `?revokeCapability=env-managed-only`;
- active filter chips render the new filter label;
- direct URL hydration shows the env-managed empty state when no rows match.

Notes:

- The targeted Jest run prints the existing React Router v7 future-flag
  warnings from the `MemoryRouter` test harness; the suite passes.
- The preflight frontend test step prints the same React Router warnings; all
  suites pass.
- The production build still prints the existing CRA bundle-size advisory.

## GitHub Actions

Run: `25735952694`

Commit: `580b90574499a41d9d9ecef3a4467153182d25ca`

Result: 7/7 jobs passed.

| Job | Result |
|---|---|
| Backend Verify | passed in 3m5s |
| Frontend Build & Test | passed in 10m8s |
| Phase C Security Verification | passed in 5m20s |
| Phase 5 Mocked Regression Gate | passed in 6m26s |
| Property Encryption Closeout Gate | passed in 4m36s |
| Frontend E2E Core Gate | passed in 12m20s |
| Acceptance Smoke (3 admin pages) | passed in 6m38s |

## Files Changed

- `ecm-frontend/src/pages/OAuthCredentialAdminPage.tsx`
- `ecm-frontend/src/pages/OAuthCredentialAdminPage.test.tsx`
- `docs/OAUTH_CREDENTIAL_STORE_CLOSEOUT_TODO_20260507.md`
- `docs/OAUTH_CREDENTIAL_ENV_MANAGED_ONLY_FILTER_DESIGN_VERIFICATION_20260512.md`

## Remaining Work

- Microsoft provider-side revoke remains intentionally unsupported for the
  reasons captured in
  `docs/OAUTH_CREDENTIAL_PROVIDER_REVOKE_MICROSOFT_CUSTOM_DESIGN_FOLLOWUP_20260507.md`.
- Env-managed credential-key-only rows remain non-revokable inside Athena; this
  slice only makes them visible and shareable as an operator triage view.
