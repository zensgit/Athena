# OAuth Credential Provider-Revoke Capability Metadata - Backend Design and Verification

Date: 2026-05-07

## Context

`POST /api/v1/admin/oauth-credentials/{id}/revoke` (shipped earlier today) calls
the provider revoke endpoint and clears local tokens, but only for credentials
that pass a strict decision tree:

1. Provider must be `GOOGLE`.
2. The row must hold either an access token or a refresh token locally.
3. Env-managed credential-key-only rows are explicitly rejected.

The frontend currently approximates that decision with a hard-coded
`credential.provider === 'GOOGLE'` check before showing the Provider Revoke
button. That under-approximates the rule (it shows the button for env-managed
GOOGLE rows that will then 400) and bakes one provider's name into the UI.

Package A makes the server the single source of truth for revoke
supportability so that:

- The frontend can drop the hard-coded provider check and key off two
  metadata fields.
- Adding Microsoft or CUSTOM provider revoke support later becomes a single
  backend flip.
- The button-disabled tooltip and the `POST /revoke` 400 response surface the
  same operator-readable text.

This change does NOT alter the `POST /revoke` behavior or any exception
message. The existing `revokeProviderTokens` decision tree is the byte-exact
reference; this work only adds a parallel projection-time computation.

## Design

### Record extension

`OAuthCredentialInventoryItem` grows from 14 to 16 fields. The two new fields
are appended at the end so existing constructor call ordering is preserved
except for the additions:

```java
public record OAuthCredentialInventoryItem(
    UUID id,
    String ownerType,
    UUID ownerId,
    OAuthProviderType provider,
    boolean tokenEndpointConfigured,
    boolean tenantIdConfigured,
    boolean scopeConfigured,
    boolean credentialKeyConfigured,
    boolean accessTokenStored,
    boolean refreshTokenStored,
    boolean connected,
    LocalDateTime tokenExpiresAt,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    boolean providerRevokeSupported,
    String providerRevokeUnsupportedReason
) {}
```

Invariants:

- `providerRevokeSupported == true` implies `providerRevokeUnsupportedReason == null`.
- `providerRevokeSupported == false` implies a non-null, non-blank reason.
- The fields are derived purely from the redacted projection booleans
  (`accessTokenStored`, `refreshTokenStored`, `credentialKeyConfigured`) and
  the `provider` enum. They are never computed from raw token contents.

### Decision tree

| Condition | `providerRevokeSupported` | `providerRevokeUnsupportedReason` |
| --- | --- | --- |
| `provider != GOOGLE` (including `null`) | `false` | `"Provider-side revoke is only supported for GOOGLE; this credential is " + provider` |
| `provider == GOOGLE` AND (`accessTokenStored` OR `refreshTokenStored`) | `true` | `null` |
| `provider == GOOGLE` AND no stored token AND `credentialKeyConfigured` | `false` | `"Provider-side revoke requires a locally stored OAuth token; this credential row only references env-managed secrets"` |
| `provider == GOOGLE` AND no stored token AND NOT `credentialKeyConfigured` | `false` | `"No locally stored OAuth token to revoke"` |

The provider-mismatch branch is evaluated first, mirroring
`OAuthCredentialService.revokeProviderTokens` order. A `null` provider falls
into the first branch and Java auto-concatenation renders `null` literally,
producing `"...is null"` — identical to the `IllegalArgumentException` text
the service would throw.

### Enrichment hook points

Capability enrichment lives in the admin service so the repository projection
stays a pure SQL query. JPQL emits placeholder defaults (`false`,
`CAST(NULL AS string)`); the service overlays the decision tree before
returning to the controller.

| Method | Enrichment |
| --- | --- |
| `OAuthCredentialAdminService.listCredentials` | `.stream().map(withCapability).toList()` |
| `OAuthCredentialAdminService.requireReauth` | `withCapability(item)` post token-clear |
| `OAuthCredentialAdminService.refreshNow` | `withCapability(item)` post refresh |
| `OAuthCredentialAdminService.revokeProvider` | `withCapability(item)` post revoke |

The controller and `OAuthCredentialService.revokeProviderTokens` are not
modified. The `POST /revoke` decision tree, exception messages, and HTTP
status mapping remain byte-identical.

### JPQL placeholder choice

The repository projection always returns `(false, null)` for the two new
fields. We use `CAST(NULL AS string)` (Hibernate 6 portable syntax) rather
than the empty-string fallback so the persistence-layer assertion `assertNull`
holds at the repo boundary. This keeps the contract explicit: capability is a
service-layer concern; the repository never lies about it being computed.

## Verification

### Targeted tests

```bash
cd ecm-core
/tmp/apache-maven-3.9.9/bin/mvn -B -q -Dstyle.color=never \
  -Dmaven.repo.local=.m2-cache/repository \
  -Dspring.profiles.active=test \
  -Dtest=OAuthCredentialServiceTest,OAuthCredentialAdminServiceTest,OAuthCredentialAdminControllerSecurityTest,OAuthCredentialPersistenceTest test
```

Result:

| Suite | Tests | Failures | Errors |
| --- | --- | --- | --- |
| `OAuthCredentialServiceTest` | 11 | 0 | 0 |
| `OAuthCredentialAdminServiceTest` | 16 | 0 | 0 |
| `OAuthCredentialAdminControllerSecurityTest` | 9 | 0 | 0 |
| `OAuthCredentialPersistenceTest` | 2 | 0 | 0 |

Coverage added:

- Decision tree: GOOGLE + access-only token (supported), GOOGLE + refresh-only
  token (supported), GOOGLE + no token + credentialKey (env-managed reason),
  GOOGLE + no token + no credentialKey (no-token reason), MICROSOFT
  (provider-mismatch reason), CUSTOM (provider-mismatch reason), `null`
  provider (provider-mismatch reason rendered as `"...is null"`).
- `listCredentials` enriches every row in the returned list, not just the
  first one.
- `requireReauth`, `refreshNow`, `revokeProvider` each return a 16-field
  inventory item with capability metadata applied after the side-effecting
  call.
- Repository test asserts both `findInventoryItems` and `findInventoryItemById`
  return `(false, null)` defaults at the repo boundary.
- Controller security test asserts JSON shape exposes
  `providerRevokeSupported` and `providerRevokeUnsupportedReason` on the
  three relevant endpoints, that token fields remain absent, and that the
  reason text matches the same string the `POST /revoke` 400 path produces.

### Repository hygiene

```bash
git diff --check
```

Result: passed.

## Files Changed

- `ecm-core/src/main/java/com/ecm/core/integration/oauth/OAuthCredentialInventoryItem.java`
- `ecm-core/src/main/java/com/ecm/core/integration/oauth/OAuthCredentialAdminService.java`
- `ecm-core/src/main/java/com/ecm/core/integration/oauth/repository/OAuthCredentialRepository.java`
- `ecm-core/src/test/java/com/ecm/core/integration/oauth/OAuthCredentialAdminServiceTest.java`
- `ecm-core/src/test/java/com/ecm/core/integration/oauth/repository/OAuthCredentialPersistenceTest.java`
- `ecm-core/src/test/java/com/ecm/core/controller/OAuthCredentialAdminControllerSecurityTest.java`
- `docs/OAUTH_CREDENTIAL_REVOKE_CAPABILITY_BACKEND_DESIGN_VERIFICATION_20260507.md`

## Remaining Work

- Frontend (Package B) drops the `provider === 'GOOGLE'` hard-coded check and
  keys the Provider Revoke button off `providerRevokeSupported`, with the
  disabled-state tooltip drawn from `providerRevokeUnsupportedReason`.
- E2E (Package C) covers the supported / unsupported tooltip paths against
  the redacted inventory contract.
- When Microsoft revoke ships, only the GOOGLE branch in `withCapability`
  changes; the JPQL stays as-is and no record reshape is needed.
- CUSTOM providers will need a per-credential `revokeEndpoint` config before
  the GOOGLE check can be relaxed; until then the provider-mismatch branch
  correctly disables the button.
