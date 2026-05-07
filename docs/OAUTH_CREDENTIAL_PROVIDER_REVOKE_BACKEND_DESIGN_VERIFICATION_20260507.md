# OAuth Credential Provider Revoke - Backend Design and Verification

Date: 2026-05-07

## Context

The OAuth credential admin surface already supports `Require Reauth` (clears local
tokens only) and `Refresh Now` (forces a refresh-token grant). The next bounded
operator control is `Provider Revoke`: an admin asks the provider to invalidate
the token Athena currently holds, then locally clears the token state to reflect
the provider's view.

This v1 covers Google only. Microsoft and CUSTOM providers, and env-managed
credential-key-only rows, return an explicit unsupported response so operators
are not given a button that quietly does nothing.

## Design

### Backend API

Added:

```http
POST /api/v1/admin/oauth-credentials/{credentialId}/revoke
```

Behavior:

- Requires `ROLE_ADMIN` through the existing OAuth credential admin controller gate.
- Resolves the credential row to `(ownerType, ownerId)`.
- Calls `OAuthCredentialService.revokeProviderTokens(ownerType, ownerId)`.
- Returns the redacted `OAuthCredentialInventoryItem` projection.
- Never returns `accessToken` or `refreshToken`.

### Unsupported decision tree

Applied in the service before any HTTP call. Each branch surfaces an
`IllegalArgumentException`, which the existing `RestExceptionHandler` maps to
`400 Bad Request` with the exception message in the response body.

1. `provider != GOOGLE` → "Provider-side revoke is only supported for GOOGLE; this credential is `<provider>`".
2. Owner has a `credentialKey` AND no stored access/refresh token → "Provider-side revoke requires a locally stored OAuth token; this credential row only references env-managed secrets".
3. Owner has neither access nor refresh token → "No locally stored OAuth token to revoke".

### Token selection

- Prefer the stored `refreshToken`: revoking a refresh token at Google also
  invalidates the access tokens issued from it, so a single call covers both.
- Fall back to the stored `accessToken` when no refresh token is stored.

### Provider failure semantics

Calls `https://oauth2.googleapis.com/revoke` with `Content-Type:
application/x-www-form-urlencoded` and a single `token=<value>` field.

| Provider response | Local effect | Operator response |
| --- | --- | --- |
| `200 OK` | Clear local tokens, evict session cache. | `200 OK` with redacted inventory row. |
| `4xx` with parsed `error` in {`invalid_token`, `invalid_grant`, `unsupported_token_type`} (case-insensitive) | Clear local tokens, evict session cache. Provider already considers the token revoked. | `200 OK` with redacted inventory row. |
| `4xx` with any other parsed error or unparseable body | **Preserve local tokens.** Throw `IllegalStateException` with diagnostic message. | `500 Internal Server Error` with diagnostic body. |
| `5xx` (`HttpServerErrorException` or any 5xx variant) | **Preserve local tokens.** Throw `IllegalStateException`. | `500` with diagnostic. |
| Network failure (`ResourceAccessException`) | **Preserve local tokens.** Throw `IllegalStateException`. | `500` with diagnostic. |

Preserve-on-failure is the safer default: an operator can retry without
silently driving Athena's local state out of sync with the provider's.

## Verification

Backend targeted tests:

```bash
cd ecm-core
/tmp/apache-maven-3.9.9/bin/mvn -B -q -Dstyle.color=never \
  -Dmaven.repo.local=.m2-cache/repository \
  -Dspring.profiles.active=test \
  -Dtest=OAuthCredentialServiceTest,OAuthCredentialAdminServiceTest,OAuthCredentialAdminControllerSecurityTest test
```

Result:

| Suite | Tests | Failures | Errors |
| --- | --- | --- | --- |
| `OAuthCredentialServiceTest` | 11 | 0 | 0 |
| `OAuthCredentialAdminServiceTest` | 8 | 0 | 0 |
| `OAuthCredentialAdminControllerSecurityTest` | 9 | 0 | 0 |

Coverage added:

- `OAuthCredentialService.revokeProviderTokens` Google success with refresh token preferred.
- Same with access token fallback when no refresh token is stored.
- `invalid_token` provider response treated as success-equivalent (clear local).
- Provider 5xx preserves local tokens.
- Non-Google provider rejected as unsupported.
- Env-managed credential-key-only row rejected.
- Row with no stored tokens rejected.
- Admin service `revokeProvider` resolves owner reference, calls the service, returns redacted projection.
- Admin service `revokeProvider` rejects unknown credential id.
- Controller exposes `POST /revoke`, keeps anonymous at 401, non-admin at 403, returns no token fields, surfaces unsupported-provider as 400 and provider-failure as 500.

Repository hygiene:

```bash
git diff --check
```

Result: passed before commit.

## Files Changed

- `ecm-core/src/main/java/com/ecm/core/controller/OAuthCredentialAdminController.java`
- `ecm-core/src/main/java/com/ecm/core/integration/oauth/OAuthCredentialAdminService.java`
- `ecm-core/src/main/java/com/ecm/core/integration/oauth/OAuthCredentialService.java`
- `ecm-core/src/test/java/com/ecm/core/controller/OAuthCredentialAdminControllerSecurityTest.java`
- `ecm-core/src/test/java/com/ecm/core/integration/oauth/OAuthCredentialAdminServiceTest.java`
- `ecm-core/src/test/java/com/ecm/core/integration/oauth/OAuthCredentialServiceTest.java`

## Remaining Work

- Microsoft revoke uses tenant-scoped endpoints; needs separate design for per-tenant URL templating and confirmation semantics.
- CUSTOM providers need a per-credential `revokeEndpoint` field (or env-key fallback) before a Provider Revoke control can be wired for them.
- Env-managed credential-key-only rows remain out of scope because Athena cannot safely clear external env secrets.
- Provider-side revoke does not affect refresh tokens issued to other clients sharing the same Google OAuth `client_id`; it only invalidates the specific token Athena holds.
