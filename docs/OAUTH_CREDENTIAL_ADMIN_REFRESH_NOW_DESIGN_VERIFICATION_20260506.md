# OAuth Credential Admin Refresh Now - Design and Verification

Date: 2026-05-06

## Context

The OAuth credential admin inventory now supports local `Require Reauth`, which clears stored tokens and forces the owner to reconnect. The next bounded operator control is `Refresh Now`: admins can force an OAuth refresh-token grant without exposing token values.

This remains intentionally narrower than provider-side revoke. `Refresh Now` contacts the configured token endpoint and updates Athena's local token state. It does not revoke tokens at Google, Microsoft, or a custom provider.

## Design

### Backend API

Added:

```http
POST /api/v1/admin/oauth-credentials/{credentialId}/refresh-now
```

Behavior:

- Requires `ROLE_ADMIN` through the existing OAuth credential admin controller gate.
- Resolves the credential row to `(ownerType, ownerId)`.
- Calls `OAuthCredentialService.refreshAccessTokenNow(ownerType, ownerId)`.
- Forces a refresh-token grant even if the in-memory OAuth session still has an unexpired access token.
- Saves returned tokens through the existing owner adapter, preserving mail-account mirroring behavior.
- Returns the refreshed `OAuthCredentialInventoryItem` projection.
- Never returns `accessToken` or `refreshToken`.

Provider failure semantics:

- `invalid_grant` keeps the existing core behavior: clear local tokens, evict session cache, and throw `OAuthReauthRequiredException`.
- `RestExceptionHandler` now maps `OAuthReauthRequiredException` to HTTP `409 Conflict` with an actionable `OAUTH_REAUTH_REQUIRED` message.
- Missing env/configuration still surfaces as the existing `IllegalStateException` path so operators see which env var is missing.

### Frontend UI

Updated `/admin/oauth-credentials`.

Operator behavior:

- Each row now has `Refresh Now` and `Require Reauth`.
- `Refresh Now` asks for confirmation because `invalid_grant` can clear local token state.
- `Refresh Now` is enabled when the row has either a stored refresh token or a credential key.
- After success, the row is replaced from the redacted backend response.
- After provider/config failure, the page shows the backend error message and reloads inventory so token-clearing side effects are visible.

## Verification

Backend targeted tests:

```bash
cd ecm-core
/tmp/apache-maven-3.9.9/bin/mvn -B -q -Dstyle.color=never \
  -Dmaven.repo.local=.m2-cache/repository \
  -Dspring.profiles.active=test \
  -Dtest=OAuthCredentialServiceTest,OAuthCredentialAdminServiceTest,OAuthCredentialAdminControllerSecurityTest test
```

Result: passed.

Coverage added:

- `OAuthCredentialService.refreshAccessTokenNow` forces a provider refresh-token grant and saves returned tokens.
- Admin service resolves credential owner references, calls the forced refresh method, and returns redacted inventory.
- Admin controller exposes `POST /refresh-now`, keeps non-admins at `403`, and returns no token fields.
- `OAuthReauthRequiredException` maps to `409 Conflict`.

Frontend targeted tests:

```bash
cd ecm-frontend
CI=true npm test -- --runTestsByPath \
  src/pages/OAuthCredentialAdminPage.test.tsx \
  src/components/layout/MainLayout.menu.test.tsx \
  --watchAll=false
```

Result: 2 suites passed, 10 tests passed.

Frontend static gates:

```bash
cd ecm-frontend
npm run lint
CI=true npm run build
```

Result: both passed. The production build emitted only the existing CRA bundle-size advisory and Node `fs.F_OK` deprecation warning.

Repository hygiene:

```bash
git diff --check
```

Result: passed before commit.

Remote CI:

```bash
gh run view 25469213928 --repo zensgit/Athena
```

Result: passed for commit `03bd0cd`.

Green jobs:

- Backend Verify
- Frontend Build & Test
- Phase C Security Verification
- Frontend E2E Core Gate
- Phase 5 Mocked Regression Gate
- Acceptance Smoke (3 admin pages)
- Property Encryption Closeout Gate

## Files Changed

- `ecm-core/src/main/java/com/ecm/core/controller/OAuthCredentialAdminController.java`
- `ecm-core/src/main/java/com/ecm/core/controller/RestExceptionHandler.java`
- `ecm-core/src/main/java/com/ecm/core/integration/oauth/OAuthCredentialAdminService.java`
- `ecm-core/src/main/java/com/ecm/core/integration/oauth/OAuthCredentialService.java`
- `ecm-core/src/test/java/com/ecm/core/controller/OAuthCredentialAdminControllerSecurityTest.java`
- `ecm-core/src/test/java/com/ecm/core/integration/oauth/OAuthCredentialAdminServiceTest.java`
- `ecm-core/src/test/java/com/ecm/core/integration/oauth/OAuthCredentialServiceTest.java`
- `ecm-frontend/src/services/oauthCredentialAdminService.ts`
- `ecm-frontend/src/pages/OAuthCredentialAdminPage.tsx`
- `ecm-frontend/src/pages/OAuthCredentialAdminPage.test.tsx`

## Remaining Work

- Provider-side revoke remains separate because each provider needs explicit revoke endpoint support and failure semantics.
- Broader OAuth owner coverage remains limited by available `OAuthCredentialOwnerAdapter` implementations.
