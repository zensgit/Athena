# OAuth Credential Admin Reauth Control - Design and Verification

Date: 2026-05-06

## Context

The OAuth Credential admin inventory added a safe read-only operator view, but it deliberately left token lifecycle writes out of scope. The next smallest useful control is a local reauthorization reset: admins can clear stored OAuth tokens when a credential is stale, revoked, or must be reconnected.

This is intentionally not a remote provider revoke. It clears Athena's locally stored access and refresh tokens and forces the owner to reconnect through its integration-specific OAuth flow.

## Design

### Backend API

Added:

```http
POST /api/v1/admin/oauth-credentials/{credentialId}/require-reauth
```

Behavior:

- Requires `ROLE_ADMIN` through the existing OAuth credential admin controller gate.
- Looks up only the credential owner reference by `credentialId`.
- Calls `OAuthCredentialService.clearTokens(ownerType, ownerId)` instead of mutating the generic credential row directly.
- Returns the refreshed `OAuthCredentialInventoryItem` projection with token status flags set by current persisted state.
- Does not return `accessToken` or `refreshToken` fields.

The service uses the existing owner adapter path so mail automation remains consistent: `MailOAuthCredentialOwnerAdapter.clearTokens(...)` clears both the mail account token fields and the generic OAuth credential row, then the OAuth session cache is evicted by `OAuthCredentialService`.

### Frontend UI

Updated `/admin/oauth-credentials`.

Operator behavior:

- Each credential row now has a `Require Reauth` action.
- The action asks for browser confirmation before clearing local tokens.
- After success, the row is replaced from the redacted backend response.
- The action disables itself once the row has no stored access token, no stored refresh token, and is not connected.

The page still keeps token disclosure out of the UI. It only displays connection and configuration flags.

## Verification

Backend targeted tests:

```bash
cd ecm-core
/tmp/apache-maven-3.9.9/bin/mvn -B -q -Dstyle.color=never -Dmaven.repo.local=.m2-cache/repository -Dspring.profiles.active=test -Dtest=OAuthCredentialAdminControllerSecurityTest,OAuthCredentialAdminServiceTest,OAuthCredentialPersistenceTest test
```

Result: passed.

Coverage added:

- Controller: admin can call `require-reauth`, response omits token fields.
- Controller: non-admin users receive `403` for the write action.
- Service: unknown credential ids produce `ResourceNotFoundException`.
- Service: known credential ids call `OAuthCredentialService.clearTokens(ownerType, ownerId)`.
- Repository: owner-reference and by-id inventory projections execute under JPA/H2.

Frontend targeted tests:

```bash
cd ecm-frontend
CI=true npm test -- --runTestsByPath src/pages/OAuthCredentialAdminPage.test.tsx src/components/layout/MainLayout.menu.test.tsx --watchAll=false
```

Result: 2 suites passed, 8 tests passed.

Frontend static gates:

```bash
cd ecm-frontend
npm run lint
CI=true npm run build
```

Result: both passed. The production build emitted only the existing CRA bundle-size advisory.

Repository hygiene:

```bash
git diff --check
```

Result: passed.

## Files Changed

- `ecm-core/src/main/java/com/ecm/core/controller/OAuthCredentialAdminController.java`
- `ecm-core/src/main/java/com/ecm/core/integration/oauth/OAuthCredentialAdminService.java`
- `ecm-core/src/main/java/com/ecm/core/integration/oauth/OAuthCredentialOwnerReference.java`
- `ecm-core/src/main/java/com/ecm/core/integration/oauth/repository/OAuthCredentialRepository.java`
- `ecm-core/src/test/java/com/ecm/core/controller/OAuthCredentialAdminControllerSecurityTest.java`
- `ecm-core/src/test/java/com/ecm/core/integration/oauth/OAuthCredentialAdminServiceTest.java`
- `ecm-core/src/test/java/com/ecm/core/integration/oauth/repository/OAuthCredentialPersistenceTest.java`
- `ecm-frontend/src/services/oauthCredentialAdminService.ts`
- `ecm-frontend/src/pages/OAuthCredentialAdminPage.tsx`
- `ecm-frontend/src/pages/OAuthCredentialAdminPage.test.tsx`

## Remaining Work

- Add a `Refresh Now` action only if the UX can handle provider/env failures clearly.
- Add a true provider-side revoke only if each provider integration defines revoke endpoint support and error handling.
- Add more non-mail OAuth owner adapters before expanding the page into a broader integration control center.
