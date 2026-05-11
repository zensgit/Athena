# OAuth Credential CUSTOM Revoke Endpoint Admin UI - Design and Verification

Date: 2026-05-11

## Context

CUSTOM provider-side revoke shipped with backend support for a persisted
`oauth_credentials.revoke_endpoint` plus env fallback. The remaining operator
gap was configuration: administrators could see capability metadata and invoke
Provider Revoke, but could not set or clear the persisted CUSTOM revoke endpoint
from the OAuth Credential Store UI.

This slice closes that gap without exposing token values and without changing
the provider-revoke execution endpoint.

## Design

### Backend

Added:

- `PUT /api/v1/admin/oauth-credentials/{credentialId}/revoke-endpoint`
- request body: `{ "revokeEndpoint": "https://provider.example/oauth/revoke" }`
- response: existing redacted `OAuthCredentialInventoryItem`

The action is admin-only through the existing controller-level
`@PreAuthorize("hasRole('ADMIN')")`.

Service rules:

- only `CUSTOM` credentials can store a revoke endpoint;
- blank or null input clears the persisted endpoint;
- non-blank input is trimmed, capped at 512 characters, and must be an absolute
  HTTPS URL;
- the returned inventory row is re-enriched with provider-revoke capability
  metadata, so a configured CUSTOM row can immediately enable Provider Revoke.

`OAuthCredentialInventoryItem` now includes `revokeEndpointConfigured`. The
field is a boolean only; the inventory endpoint still does not return token
values, and it does not disclose the stored endpoint URL.

### Frontend

`/admin/oauth-credentials` now:

- shows a `Revoke endpoint` configuration chip;
- renders `Configure Revoke Endpoint` only on `CUSTOM` rows;
- opens a dialog for replacing the stored HTTPS endpoint or clearing it by
  saving a blank value;
- updates the row from the backend response so `providerRevokeSupported` flips
  immediately after configuration;
- guards the new service response shape against HTML fallback / missing route
  responses.

## Verification

### Targeted Backend Suite

Command:

```bash
cd ecm-core
/tmp/codex-maven/apache-maven-3.9.11/bin/mvn -B -Dstyle.color=never \
  -Dmaven.repo.local=.m2-cache/repository \
  -Dspring.profiles.active=test \
  -Dtest=OAuthCredentialAdminServiceTest,OAuthCredentialPersistenceTest,OAuthCredentialAdminControllerSecurityTest \
  test
```

Result:

```text
Tests run: 35, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

New backend coverage:

- endpoint stores a trimmed HTTPS revoke endpoint for CUSTOM credentials;
- blank input clears the stored endpoint;
- non-CUSTOM provider returns HTTP 400;
- non-HTTPS endpoint validation returns a deterministic error;
- anonymous and non-admin callers cannot use the new action;
- repository projections return `revokeEndpointConfigured`.

### Targeted Frontend Suite

Command:

```bash
cd ecm-frontend
CI=true npm test -- --runInBand --watchAll=false \
  src/services/oauthCredentialAdminService.test.ts \
  src/pages/OAuthCredentialAdminPage.test.tsx
```

Result:

```text
Test Suites: 2 passed, 2 total
Tests: 27 passed, 27 total
```

New frontend coverage:

- service calls `PUT /admin/oauth-credentials/{id}/revoke-endpoint`;
- service rejects HTML fallback for the update response;
- CUSTOM rows expose the configure dialog;
- successful save replaces the row and re-enables Provider Revoke when backend
  capability metadata says it is supported;
- validation errors stay visible without closing the dialog.

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
  `OAuthCredentialAdminPage.test.tsx`, `MainLayout.menu.test.tsx`: 19 tests
  passed.
- Frontend lint passed.
- Frontend production build compiled successfully; existing CRA bundle-size
  advisory only.

### GitHub Actions

Run: <https://github.com/zensgit/Athena/actions/runs/25678554009>

Head commit: `70909a40ab29a98a642e3638ec7e256f5ce886a0`

Result: success.

| Job | Result | Duration |
| --- | --- | --- |
| Backend Verify | success | 2m24s |
| Frontend Build & Test | success | 10m0s |
| Phase C Security Verification | success | 5m12s |
| Property Encryption Closeout Gate | success | 4m43s |
| Acceptance Smoke (3 admin pages) | success | 6m39s |
| Phase 5 Mocked Regression Gate | success | 6m20s |
| Frontend E2E Core Gate | success | 11m26s |

## Remaining Work

- Microsoft provider-side revoke remains intentionally unsupported until there
  is a truthful per-token provider endpoint or a product decision to model a
  Microsoft-specific consent-grant revocation flow.
- The admin dialog does not display the currently stored endpoint URL. This is
  deliberate for the v1 operator surface: it supports replace-or-clear and only
  returns a boolean configuration flag.
