# OAuth Credential Admin Inventory - Design and Verification

Date: 2026-05-06

## Context

The generic OAuth Credential Store backend aggregate existed before this slice, but the only operator-facing surface was still integration-specific mail automation UI. That left admins without a cross-integration inventory view for stored OAuth owners, providers, and configuration health.

This slice adds the first generic admin surface as a read-only inventory. It intentionally does not add refresh, revoke, or token lifecycle write actions.

## Design

### Backend API

Added `GET /api/v1/admin/oauth-credentials`.

Security model:

- Requires authenticated admin role through `@PreAuthorize("hasRole('ADMIN')")`.
- Anonymous requests return `401`.
- Authenticated non-admin requests return `403`.

Response model:

- `OAuthCredentialInventoryItem` exposes owner/provider metadata and status booleans.
- Access token and refresh token values are never present in the DTO.
- The repository uses a JPQL constructor projection so the read path returns status flags instead of loading full credential entities into the response contract.

Supported filters:

- `ownerType`: optional exact-match operational owner type, for example `MAIL_ACCOUNT`.
- `provider`: optional `OAuthProviderType`, currently `GOOGLE`, `MICROSOFT`, or `CUSTOM`.

### Frontend UI

Added `/admin/oauth-credentials`.

Operator workflow:

- Summary cards show total credentials, connected credentials, refresh-token presence, and credential-key presence.
- Filters support exact-match owner type and provider selection.
- The inventory table displays owner, provider, connection flags, configuration flags, expiry, and update time.
- A visible info banner states that token values are never returned by this admin surface.

Menu placement:

- Admin account menu now includes `OAuth Credentials` near `Property Encryption`.
- Viewer/non-admin menu remains unchanged.

## Verification

Backend targeted tests:

```bash
cd ecm-core
/tmp/apache-maven-3.9.9/bin/mvn -B -q -Dstyle.color=never -Dmaven.repo.local=.m2-cache/repository -Dspring.profiles.active=test -Dtest=OAuthCredentialAdminControllerSecurityTest,OAuthCredentialAdminServiceTest,OAuthCredentialPersistenceTest test
```

Result: passed.

Coverage added:

- Controller security: anonymous `401`, user `403`, admin `200`.
- Controller response redaction: `accessToken` and `refreshToken` JSON fields do not exist.
- Service filter normalization: blank owner type becomes `null`, non-blank owner type is trimmed.
- Repository projection: JPQL inventory query executes and returns only status flags.

Frontend targeted tests:

```bash
cd ecm-frontend
CI=true npm test -- --runTestsByPath src/pages/OAuthCredentialAdminPage.test.tsx src/components/layout/MainLayout.menu.test.tsx --watchAll=false
```

Result: 2 suites passed, 6 tests passed.

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
- `ecm-core/src/main/java/com/ecm/core/integration/oauth/OAuthCredentialInventoryItem.java`
- `ecm-core/src/main/java/com/ecm/core/integration/oauth/repository/OAuthCredentialRepository.java`
- `ecm-core/src/test/java/com/ecm/core/controller/OAuthCredentialAdminControllerSecurityTest.java`
- `ecm-core/src/test/java/com/ecm/core/integration/oauth/OAuthCredentialAdminServiceTest.java`
- `ecm-core/src/test/java/com/ecm/core/integration/oauth/repository/OAuthCredentialPersistenceTest.java`
- `ecm-frontend/src/services/oauthCredentialAdminService.ts`
- `ecm-frontend/src/pages/OAuthCredentialAdminPage.tsx`
- `ecm-frontend/src/pages/OAuthCredentialAdminPage.test.tsx`
- `ecm-frontend/src/App.tsx`
- `ecm-frontend/src/components/layout/MainLayout.tsx`
- `ecm-frontend/src/components/layout/MainLayout.menu.test.tsx`

## Remaining Work

- Add controlled write actions only after product semantics are defined: refresh token now, revoke credential, and force reauth.
- Add non-mail owner adapters to make the generic inventory cover more integrations.
- Consider a backend count endpoint if the table grows beyond the current read-only page shape.
