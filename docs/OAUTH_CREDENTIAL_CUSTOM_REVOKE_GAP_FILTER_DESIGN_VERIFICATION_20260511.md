# OAuth Credential CUSTOM Revoke Gap Filter - Design and Verification

Date: 2026-05-11

## Context

The CUSTOM revoke endpoint admin UI lets operators set or clear a persisted
RFC 7009-style revoke endpoint. After that shipped, the remaining operational
gap was triage: administrators still had to scan the whole credential inventory
to find CUSTOM rows where Provider Revoke is blocked specifically because no
revoke endpoint is configured.

This slice adds that visibility without changing backend contracts, exposing
stored endpoint URLs, or expanding provider-side revoke support to Microsoft.

## Design

### Scope

Frontend-only. The existing inventory DTO already carries the fields needed for
the decision:

- `provider`
- `providerRevokeSupported`
- `providerRevokeUnsupportedReason`
- `revokeEndpointConfigured`

The new UI treats a row as a CUSTOM revoke endpoint gap when:

- `provider === "CUSTOM"`;
- `providerRevokeSupported === false`;
- `providerRevokeUnsupportedReason` contains `revoke endpoint`.

This deliberately keys off backend capability metadata instead of a frontend
provider-only decision tree. That avoids misclassifying:

- Microsoft rows, which remain intentionally unsupported;
- CUSTOM rows already covered by env fallback or a persisted revoke endpoint;
- CUSTOM rows blocked for another reason, such as no local token.

### Admin Page

`/admin/oauth-credentials` now includes:

- a `CUSTOM Revoke Gaps` summary card;
- a `CUSTOM revoke gaps (N)` local filter button in the Filters card;
- a filtered table view showing only CUSTOM credentials whose Provider Revoke
  action is blocked by a missing revoke endpoint;
- a dedicated empty state: `No CUSTOM credentials currently need a revoke
  endpoint.`

The filter is local to the currently loaded inventory response. Server-side
owner/provider filters still work as before; this button narrows whatever the
server returned.

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
Tests: 18 passed, 18 total
```

New coverage:

- the local gap filter includes a CUSTOM row blocked by missing revoke endpoint;
- the same filter excludes configured CUSTOM rows and Microsoft unsupported
  rows;
- the filter empty state is shown when no CUSTOM row currently needs endpoint
  configuration.

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
  `OAuthCredentialAdminPage.test.tsx`, `MainLayout.menu.test.tsx`: 21 tests
  passed.
- Frontend lint passed.
- Frontend production build compiled successfully; existing CRA bundle-size
  advisory only.

## Remaining Work

- Microsoft provider-side revoke remains intentionally unsupported for the
  reasons captured in
  `docs/OAUTH_CREDENTIAL_PROVIDER_REVOKE_MICROSOFT_CUSTOM_DESIGN_FOLLOWUP_20260507.md`.
- The admin UI still does not read back or display persisted CUSTOM revoke
  endpoint URLs; it surfaces only the boolean configuration/capability state.
