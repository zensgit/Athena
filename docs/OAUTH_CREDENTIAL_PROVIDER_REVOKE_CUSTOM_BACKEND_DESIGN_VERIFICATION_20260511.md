# OAuth Provider Revoke - CUSTOM Backend Design and Verification

Date: 2026-05-11

## Context

Google provider-side revoke already existed, and the OAuth admin UI already
consumes backend-supplied `providerRevokeSupported` /
`providerRevokeUnsupportedReason` metadata. The remaining safe provider
extension was `CUSTOM`: unlike Microsoft, a CUSTOM provider can be treated as
RFC 7009-shaped when an operator supplies an explicit revoke endpoint.

This slice keeps the public admin API and frontend DTO shape unchanged. The
frontend still reads the existing capability fields; the backend decides
whether a CUSTOM row is actionable.

## Design

### Data Shape

Added optional `oauth_credentials.revoke_endpoint` through Liquibase change
`093-add-oauth-credential-revoke-endpoint.xml`.

`OAuthCredential` and `OAuthCredentialOwner` now carry the optional
`revokeEndpoint`. The owner record keeps an overload matching the previous
constructor shape so existing tests and adapters do not need mechanical churn.

`MailOAuthCredentialOwnerAdapter` deliberately does not map a mail-account
field into `revokeEndpoint`: the mail-account table has no such column. The
adapter preserves any generic OAuth credential row `revokeEndpoint` when loading
or clearing tokens, so operators can configure CUSTOM revoke through the
generic `oauth_credentials` row or through env fallback.

### CUSTOM Revoke Resolution

`OAuthCredentialService.revokeProviderTokens(...)` now supports:

- `GOOGLE`: unchanged, calls `https://oauth2.googleapis.com/revoke`.
- `CUSTOM`: calls a configured RFC 7009-style revoke endpoint.
- `MICROSOFT`: remains unsupported because Microsoft does not expose a
  Google-style per-token revoke endpoint for this model.

CUSTOM endpoint resolution:

1. Use `OAuthCredentialOwner.revokeEndpoint` when present.
2. Otherwise use `ECM_OAUTH_<KEY>_REVOKE_ENDPOINT` through the existing owner
   adapter env-key builder, e.g. mail accounts resolve
   `ECM_MAIL_OAUTH_<KEY>_REVOKE_ENDPOINT`.
3. If neither exists, report
   `Provider-side revoke endpoint is not configured for this CUSTOM credential`.

The actual revoke call reuses the Google branch semantics:

- refresh token preferred, access token fallback;
- form body `token=<value>`;
- success or already-invalid provider errors clear local tokens and evict cache;
- provider 5xx, network errors, and non-already-invalid 4xx preserve local
  tokens and surface an operator-retryable error.

Env-managed credential-key-only rows remain unsupported. If a row has no local
access or refresh token, Athena cannot truthfully revoke a token it does not
own locally.

### Capability Metadata

`OAuthCredentialAdminService` still returns the same `OAuthCredentialInventoryItem`
shape. For CUSTOM rows, it asks `OAuthCredentialService.providerRevokeCapability`
for runtime capability so env fallback and per-row `revokeEndpoint` stay aligned
with `POST /revoke`.

For non-CUSTOM rows, capability remains computed from redacted projection
booleans only.

## Verification

### Local Backend Targeted Suite

Command:

```bash
cd ecm-core
/tmp/codex-maven/apache-maven-3.9.11/bin/mvn -B -Dstyle.color=never \
  -Dmaven.repo.local=.m2-cache/repository \
  -Dspring.profiles.active=test \
  -Dtest=OAuthCredentialServiceTest,OAuthCredentialAdminServiceTest,OAuthCredentialPersistenceTest,MailOAuthCredentialOwnerAdapterTest,OAuthCredentialAdminControllerSecurityTest \
  test
```

Result:

```text
Tests run: 46, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

New coverage:

- CUSTOM revoke uses per-row `revokeEndpoint`;
- CUSTOM revoke uses credential-key env fallback;
- CUSTOM without configured revoke endpoint returns deterministic unsupported
  reason;
- OAuth admin capability metadata uses runtime service metadata for CUSTOM rows;
- generic OAuth persistence stores and reloads `revokeEndpoint`;
- mail owner adapter preserves `revokeEndpoint` while clearing tokens;
- controller security still maps unsupported provider to HTTP 400.

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

- Backend targeted tests in the preflight script:
  `OAuthCredentialServiceTest`, `OAuthCredentialAdminServiceTest`,
  `OAuthCredentialAdminControllerSecurityTest`: 40 tests passed.
- Frontend targeted tests:
  `OAuthCredentialAdminPage.test.tsx`, `MainLayout.menu.test.tsx`: 17 tests
  passed.
- Frontend lint passed.
- Frontend production build compiled successfully; existing CRA bundle-size
  advisory only.

### GitHub Actions

Run: <https://github.com/zensgit/Athena/actions/runs/25676504311>

Head commit: `e3bb281d20d1de52cc1d219ce3b94c3e9525f222`

Result: success.

| Job | Result | Duration |
| --- | --- | --- |
| Backend Verify | success | 2m9s |
| Frontend Build & Test | success | 10m20s |
| Phase C Security Verification | success | 5m29s |
| Property Encryption Closeout Gate | success | 4m43s |
| Acceptance Smoke (3 admin pages) | success | 6m46s |
| Phase 5 Mocked Regression Gate | success | 5m36s |
| Frontend E2E Core Gate | success | 12m5s |

### Local Wrapper Note

The repository `ecm-core/./mvnw` is Docker-backed and failed before Maven
startup on this host:

```text
failed to connect to the docker API at unix:///Users/chouhua/.docker/run/docker.sock; check if the path is correct and if the daemon is running: dial unix /Users/chouhua/.docker/run/docker.sock: connect: no such file or directory
```

The successful run used a temporary standalone Maven 3.9.11 binary under
`/tmp/codex-maven` with the repo-local `.m2-cache/repository`.

## Files Changed

- `ecm-core/src/main/java/com/ecm/core/integration/oauth/OAuthCredentialService.java`
- `ecm-core/src/main/java/com/ecm/core/integration/oauth/OAuthCredentialAdminService.java`
- `ecm-core/src/main/java/com/ecm/core/integration/oauth/OAuthCredentialOwner.java`
- `ecm-core/src/main/java/com/ecm/core/integration/oauth/OAuthProviderRevokeCapability.java`
- `ecm-core/src/main/java/com/ecm/core/integration/oauth/model/OAuthCredential.java`
- `ecm-core/src/main/java/com/ecm/core/integration/mail/service/MailOAuthCredentialOwnerAdapter.java`
- `ecm-core/src/main/resources/db/changelog/changes/093-add-oauth-credential-revoke-endpoint.xml`
- `ecm-core/src/main/resources/db/changelog/db.changelog-master.xml`
- Targeted backend tests listed in the verification command above.

## Remaining Work

- Microsoft provider-side revoke remains intentionally unsupported until there
  is a truthful per-token provider endpoint or a product decision to model a
  different Microsoft-specific revocation flow.
- No frontend changes were required because the admin page already consumes
  backend capability metadata.
- Admin UI for editing `revokeEndpoint` shipped later on 2026-05-11 in
  `docs/OAUTH_CREDENTIAL_CUSTOM_REVOKE_ENDPOINT_ADMIN_UI_DESIGN_VERIFICATION_20260511.md`.
