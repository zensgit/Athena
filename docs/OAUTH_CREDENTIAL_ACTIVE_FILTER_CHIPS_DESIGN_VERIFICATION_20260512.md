# OAuth Credential Active Filter Chips

Date: 2026-05-12

## Context

The OAuth Credential Store admin page now persists its applied filters in the
URL:

- `ownerType`
- `provider`
- `revokeCapability`

That made filtered views refreshable and shareable, but an operator opening a
shared link still had to infer the active filter set from the form controls and
URL. This slice adds an explicit active-filter summary and clear controls.

## Design

`/admin/oauth-credentials` now renders an `Active filters` section inside the
Filters card whenever at least one managed filter is applied.

Managed filters:

| Query parameter | Active chip |
|---|---|
| `ownerType=MAIL_ACCOUNT` | `Owner type: MAIL_ACCOUNT` |
| `provider=GOOGLE` | `Provider: GOOGLE` |
| `revokeCapability=ready` | `Revoke capability: Provider revoke ready` |
| `revokeCapability=blocked` | `Revoke capability: Provider revoke blocked` |
| `revokeCapability=custom-endpoint-gap` | `Revoke capability: CUSTOM revoke gaps` |

Each chip has an accessible delete icon that removes only its own query
parameter. `Clear all filters` removes the three OAuth-admin-managed filters
while preserving unrelated query parameters such as `view=ops`.

The clear behavior stays aligned with the prior URL-state design:

- clearing `ownerType` or `provider` triggers the backend inventory reload
  through the existing query-state effect;
- clearing only `revokeCapability` is local table filtering and does not call
  the backend;
- token values, backend DTOs, migrations, OAuth provider semantics, and
  endpoint behavior are unchanged.

## Verification

| Gate | Command | Result |
|---|---|---|
| Targeted Jest | `cd ecm-frontend && CI=true npm test -- --runTestsByPath src/pages/OAuthCredentialAdminPage.test.tsx --watchAll=false --runInBand` | 1 suite, 26 tests passed |
| OAuth admin preflight | `MAVEN_BIN=/tmp/codex-maven/apache-maven-3.9.11/bin/mvn bash scripts/oauth-credential-admin-preflight.sh` | passed: backend 47/47, frontend 40/40, lint clean, production build compiled successfully with the existing CRA bundle-size advisory |

Targeted coverage added:

- URL-hydrated `ownerType`, `provider`, and `revokeCapability` render active
  chips;
- `Clear all filters` removes only OAuth-admin-managed filter params and
  preserves unrelated params;
- deleting one active chip removes that filter without removing other query
  filters;
- clearing a server-backed chip reloads the inventory with the remaining
  backend filters.

Notes:

- The targeted Jest and preflight runs print the existing React Router v7
  future-flag warnings from the `MemoryRouter` test harness; the suites pass.
- The production build still prints the existing CRA bundle-size advisory.

## Files Changed

- `ecm-frontend/src/pages/OAuthCredentialAdminPage.tsx`
- `ecm-frontend/src/pages/OAuthCredentialAdminPage.test.tsx`
- `docs/OAUTH_CREDENTIAL_STORE_CLOSEOUT_TODO_20260507.md`
- `docs/OAUTH_CREDENTIAL_ACTIVE_FILTER_CHIPS_DESIGN_VERIFICATION_20260512.md`

## Remaining Work

- Microsoft provider-side revoke remains intentionally unsupported for the
  reasons captured in
  `docs/OAUTH_CREDENTIAL_PROVIDER_REVOKE_MICROSOFT_CUSTOM_DESIGN_FOLLOWUP_20260507.md`.
- The persisted CUSTOM revoke endpoint URL still is not read back through the
  inventory UI; only `revokeEndpointConfigured` and capability metadata are
  shown.
