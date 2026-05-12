# OAuth Credential CUSTOM Revoke Endpoint Detail Readback

Date: 2026-05-12

## Context

The OAuth Credential Store inventory deliberately returns only
`revokeEndpointConfigured` for CUSTOM credentials. That keeps the table redacted,
but it left an operator unable to review the persisted per-credential revoke URL
before replacing it.

This slice adds an explicit admin detail read path without changing the
inventory DTO or exposing OAuth token values.

## Design

### Backend

Added:

- `GET /api/v1/admin/oauth-credentials/{credentialId}/revoke-endpoint`
- response:

```json
{
  "id": "11111111-2222-3333-4444-555555555555",
  "ownerType": "MAIL_ACCOUNT",
  "ownerId": "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
  "provider": "CUSTOM",
  "revokeEndpointConfigured": true,
  "revokeEndpoint": "https://custom.example/revoke"
}
```

Rules:

- the endpoint remains admin-only through the existing controller-level
  `@PreAuthorize("hasRole('ADMIN')")`;
- only `CUSTOM` credentials can read persisted revoke endpoint details;
- unknown credentials return the existing not-found path;
- non-CUSTOM credentials return HTTP 400 with a deterministic provider-mismatch
  message;
- access and refresh token values are never returned;
- env fallback revoke endpoint values are not displayed by this endpoint.

The existing inventory endpoint is unchanged and continues to return only the
boolean `revokeEndpointConfigured` flag.

### Frontend

`/admin/oauth-credentials` now loads revoke endpoint details only when an
administrator opens the CUSTOM revoke endpoint dialog.

Behavior:

- the dialog shows a loading state while the detail endpoint is queried;
- configured CUSTOM rows prefill the persisted URL for review or replacement;
- unconfigured CUSTOM rows keep the field blank and show the existing warning;
- detail-load failures stay inside the dialog and do not close it;
- save and clear actions are disabled while the detail request is in flight;
- service response shape is guarded against HTML fallback / missing route
  responses.

## Verification

| Gate | Command | Result |
|---|---|---|
| Backend targeted | `cd ecm-core && /tmp/codex-maven/apache-maven-3.9.11/bin/mvn -B -Dstyle.color=never -Dmaven.repo.local=.m2-cache/repository -Dspring.profiles.active=test -Dtest=OAuthCredentialAdminServiceTest,OAuthCredentialAdminControllerSecurityTest test` | 39/39 tests passed |
| Frontend targeted | `cd ecm-frontend && CI=true npm test -- --runInBand --watchAll=false src/services/oauthCredentialAdminService.test.ts src/pages/OAuthCredentialAdminPage.test.tsx` | 42/42 tests passed |
| OAuth admin preflight | `MAVEN_BIN=/tmp/codex-maven/apache-maven-3.9.11/bin/mvn bash scripts/oauth-credential-admin-preflight.sh` | passed: backend 53/53, frontend 45/45, lint clean, production build compiled successfully with the existing CRA bundle-size advisory |

Targeted coverage added:

- backend service returns persisted CUSTOM revoke endpoint details;
- backend service returns null detail state for unconfigured CUSTOM rows;
- backend service rejects non-CUSTOM and unknown credential IDs;
- controller security covers anonymous, non-admin, success, and non-CUSTOM
  provider-mismatch paths;
- frontend service guards detail responses and rejects HTML fallback;
- CUSTOM dialog preloads the persisted URL before replacement;
- CUSTOM dialog surfaces detail-load errors without closing.

Notes:

- The targeted Jest and preflight runs print the existing React Router v7
  future-flag warnings from the `MemoryRouter` test harness; the suites pass.
- The production build still prints the existing CRA bundle-size advisory.

## GitHub Actions

Run: `25728989137`

Commit: `eca4d2a7d2482f3144842f37ec44bfb369c432d7`

Result: 7/7 jobs passed.

| Job | Result |
|---|---|
| Backend Verify | passed in 2m26s |
| Frontend Build & Test | passed in 10m1s |
| Phase C Security Verification | passed in 5m9s |
| Acceptance Smoke (3 admin pages) | passed in 8m22s |
| Property Encryption Closeout Gate | passed in 5m2s |
| Phase 5 Mocked Regression Gate | passed in 8m56s |
| Frontend E2E Core Gate | passed in 14m57s |

## Files Changed

- `ecm-core/src/main/java/com/ecm/core/controller/OAuthCredentialAdminController.java`
- `ecm-core/src/main/java/com/ecm/core/integration/oauth/OAuthCredentialAdminService.java`
- `ecm-core/src/main/java/com/ecm/core/integration/oauth/OAuthCredentialRevokeEndpointDetails.java`
- `ecm-core/src/test/java/com/ecm/core/controller/OAuthCredentialAdminControllerSecurityTest.java`
- `ecm-core/src/test/java/com/ecm/core/integration/oauth/OAuthCredentialAdminServiceTest.java`
- `ecm-frontend/src/services/oauthCredentialAdminService.ts`
- `ecm-frontend/src/services/oauthCredentialAdminService.test.ts`
- `ecm-frontend/src/pages/OAuthCredentialAdminPage.tsx`
- `ecm-frontend/src/pages/OAuthCredentialAdminPage.test.tsx`
- `docs/OAUTH_CREDENTIAL_STORE_CLOSEOUT_TODO_20260507.md`
- `docs/OAUTH_CREDENTIAL_CUSTOM_REVOKE_ENDPOINT_DETAIL_READBACK_DESIGN_VERIFICATION_20260512.md`

## Remaining Work

- Microsoft provider-side revoke remains intentionally unsupported for the
  reasons captured in
  `docs/OAUTH_CREDENTIAL_PROVIDER_REVOKE_MICROSOFT_CUSTOM_DESIGN_FOLLOWUP_20260507.md`.
- Inventory-wide CUSTOM revoke endpoint URL readback remains intentionally out
  of scope. Operators must open a single CUSTOM row's configure dialog to read
  the persisted per-credential URL.
