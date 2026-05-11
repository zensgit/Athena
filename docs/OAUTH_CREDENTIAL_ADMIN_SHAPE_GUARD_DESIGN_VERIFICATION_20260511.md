# OAuth Credential Admin Shape Guard Design and Verification

Date: 2026-05-11

## Context

The OAuth Credential Store admin page consumes four frontend service methods:

- `GET /api/v1/admin/oauth-credentials`
- `POST /api/v1/admin/oauth-credentials/{id}/require-reauth`
- `POST /api/v1/admin/oauth-credentials/{id}/refresh-now`
- `POST /api/v1/admin/oauth-credentials/{id}/revoke`

The backend and UI already support inventory, reauth, refresh, and provider
revoke. The remaining defensive gap was frontend contract validation. In
static or mocked browser runs, an unmocked route can return the SPA HTML shell
with HTTP 200. Without a shape guard, that response can be treated as a valid
DTO and fail later in a less actionable UI path.

This slice adds the same style of defensive guard already used by the SMTP
test-smtp service path, scoped to OAuth Credential Admin.

## Design

`oauthCredentialAdminService` now validates all returned inventory DTOs before
returning them to the page.

The list endpoint must return an array. Each item must include the required
redacted contract fields:

- identity fields: `id`, `ownerType`, `ownerId`;
- configuration booleans: `tokenEndpointConfigured`,
  `tenantIdConfigured`, `scopeConfigured`, `credentialKeyConfigured`;
- storage booleans: `accessTokenStored`, `refreshTokenStored`, `connected`;
- required timestamps / optional timestamp strings;
- provider-revoke metadata: `providerRevokeSupported`,
  `providerRevokeUnsupportedReason`.

The three mutation methods (`requireReauth`, `refreshNow`, `revoke`) validate
that the returned value is one inventory item. If a route returns HTML, a
string, an array, or a malformed object, the service throws:

```text
OAuth credential admin endpoint returned an unexpected response. Mocked CI gate may not cover it; backend route may be missing.
```

The page already renders service errors through its existing error alert path,
so no page-level behavior change was needed.

## Verification

| Gate | Command | Result |
|---|---|---|
| Targeted Jest | `cd ecm-frontend && CI=true npm test -- --runTestsByPath src/services/oauthCredentialAdminService.test.ts src/pages/OAuthCredentialAdminPage.test.tsx --watchAll=false --runInBand` | 2 suites, 23 tests passed |
| Frontend lint | `cd ecm-frontend && npm run lint` | passed |
| Frontend production build | `cd ecm-frontend && CI=true npm run build` | compiled successfully; existing CRA bundle-size advisory only |
| Whitespace | `git diff --check` | clean |

## Files Changed

- `ecm-frontend/src/services/oauthCredentialAdminService.ts`
- `ecm-frontend/src/services/oauthCredentialAdminService.test.ts`
- `docs/OAUTH_CREDENTIAL_ADMIN_SHAPE_GUARD_DESIGN_VERIFICATION_20260511.md`

## Remaining Work

- This is a defensive frontend contract guard only. It does not expand OAuth
  provider revoke capability.
- Microsoft and CUSTOM provider-side revoke support remain separate backend
  capability work. The existing design follow-up is
  `OAUTH_CREDENTIAL_PROVIDER_REVOKE_MICROSOFT_CUSTOM_DESIGN_FOLLOWUP_20260507.md`.
