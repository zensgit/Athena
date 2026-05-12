# OAuth Credential CUSTOM Revoke Endpoint Clear Action - Design and Verification

Date: 2026-05-12

## Context

The OAuth Credential Store already supports configuring a persisted CUSTOM
provider revoke endpoint without disclosing the stored URL back through the
inventory API. The remaining operator friction was that clearing the endpoint
required saving a blank value in the configure dialog, which made a destructive
configuration change too implicit.

This slice keeps the existing security boundary: token values and persisted
CUSTOM revoke endpoint URLs are still not returned by the admin inventory UI.

## Design

### Frontend Behavior

`/admin/oauth-credentials` now shows an explicit `Clear Endpoint` action inside
the CUSTOM revoke endpoint dialog when the selected credential already has a
persisted endpoint.

The dialog now also states the redacted configuration state:

- configured: the URL is hidden, and the operator can replace it or clear it;
- not configured: no persisted revoke endpoint exists for that CUSTOM
  credential.

`Clear Endpoint` uses the existing endpoint-update contract:

```text
PUT /api/v1/admin/oauth-credentials/{credentialId}/revoke-endpoint
{ "revokeEndpoint": "" }
```

The backend already treats blank input as a clear operation and returns the
redacted `OAuthCredentialInventoryItem`. The page replaces the local row from
that response, so `revokeEndpointConfigured` and `providerRevokeSupported`
immediately reflect the cleared state.

### Non-Goals

- No backend endpoint or schema change.
- No read-back of the persisted CUSTOM revoke endpoint URL.
- No Microsoft provider-side revoke.

## Verification

### Targeted Frontend Suite

Command:

```bash
cd ecm-frontend
CI=true npm test -- --runInBand --watchAll=false \
  src/pages/OAuthCredentialAdminPage.test.tsx
```

Result:

```text
Test Suites: 1 passed, 1 total
Tests: 21 passed, 21 total
```

New coverage:

- a configured CUSTOM row shows the redacted configured-state message;
- `Clear Endpoint` calls `updateRevokeEndpoint(id, '')`;
- the returned redacted row updates the table so Provider Revoke becomes
  disabled when backend metadata reports the missing endpoint;
- the backend-owned unsupported reason remains visible on the disabled Provider
  Revoke control.

### OAuth Admin Preflight

Command:

```bash
MAVEN_BIN=/tmp/codex-maven/apache-maven-3.9.11/bin/mvn \
  bash scripts/oauth-credential-admin-preflight.sh
```

Result:

```text
oauth_credential_admin_preflight: ok
```

Breakdown:

- Backend targeted tests:
  `OAuthCredentialServiceTest`, `OAuthCredentialAdminServiceTest`,
  `OAuthCredentialAdminControllerSecurityTest`: 47 tests passed.
- Frontend targeted tests:
  `OAuthCredentialAdminPage.test.tsx`, `MainLayout.menu.test.tsx`: 24 tests
  passed.
- Frontend lint passed.
- Frontend production build compiled successfully; existing CRA bundle-size
  advisory only.
