# OAuth Credential Provider Revoke Filters - Design and Verification

Date: 2026-05-11

## Context

The OAuth Credential Store now exposes provider-revoke capability metadata and a
CUSTOM revoke endpoint gap filter. The remaining operator friction was that
administrators still had to scan every inventory row to separate credentials
where Provider Revoke is immediately actionable from credentials where the
backend has intentionally blocked the action.

This slice adds local capability filters on top of the existing server-side
owner/provider filters. It does not add backend endpoints, change provider
semantics, expose token values, or expand Microsoft provider-side revoke.

## Design

### Capability Filter Model

`/admin/oauth-credentials` now has a single mutually exclusive local filter:

- `All`
- `Provider revoke ready`
- `Provider revoke blocked`
- `CUSTOM revoke gaps`

The filter is driven by existing backend-owned fields:

- `providerRevokeSupported`
- `providerRevokeUnsupportedReason`
- `provider`

The CUSTOM gap view remains a narrower blocked-state view:

- `provider === "CUSTOM"`
- `providerRevokeSupported === false`
- unsupported reason includes `revoke endpoint`

### Admin Page Behavior

The filter buttons show counts for the currently loaded inventory response:

- `All (N)`
- `Provider revoke ready (N)`
- `Provider revoke blocked (N)`
- `CUSTOM revoke gaps (N)`

Changing the local capability filter does not re-query the backend. It narrows
the current server-filtered list, so operators can combine owner/provider
server filters with immediate local triage.

Each non-`All` filter displays a short caption describing what is currently
shown. Empty states are specific to the selected filter, so operators can tell
whether the current server-filtered result has no actionable revoke rows, no
blocked rows, or no CUSTOM endpoint gaps.

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
Tests: 20 passed, 20 total
```

New coverage:

- Provider Revoke ready filter includes only rows with
  `providerRevokeSupported === true`;
- Provider Revoke blocked filter includes only rows with
  `providerRevokeSupported === false`;
- the `All` filter returns to the full current inventory response;
- the existing CUSTOM revoke gap filter still narrows blocked rows to the
  missing-endpoint case.

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
  `OAuthCredentialAdminPage.test.tsx`, `MainLayout.menu.test.tsx`: 23 tests
  passed.
- Frontend lint passed.
- Frontend production build compiled successfully; existing CRA bundle-size
  advisory only.

## Remaining Work

- Microsoft provider-side revoke remains intentionally unsupported for the
  reasons captured in
  `docs/OAUTH_CREDENTIAL_PROVIDER_REVOKE_MICROSOFT_CUSTOM_DESIGN_FOLLOWUP_20260507.md`.
- The admin UI still does not read back or display persisted CUSTOM revoke
  endpoint URLs; it surfaces only boolean configuration and capability state.
