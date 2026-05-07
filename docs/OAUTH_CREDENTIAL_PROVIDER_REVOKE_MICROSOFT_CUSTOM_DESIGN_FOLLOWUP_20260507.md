# OAuth Provider Revoke - Microsoft and CUSTOM Design Follow-up

Date: 2026-05-07

## Context

v1 Google provider-side revoke shipped on 2026-05-07; the integration record is
`docs/OAUTH_CREDENTIAL_PROVIDER_REVOKE_INTEGRATION_VERIFICATION_20260507.md`.
The round 2 capability-metadata refactor is now shipped; server-supplied
capability flags (`providerRevokeSupported`,
`providerRevokeUnsupportedReason`) on `OAuthCredentialInventoryItem` become the
single source of truth for whether a credential row supports Provider Revoke.
The UI consumes those flags directly and no longer hard-codes
`provider === 'GOOGLE'`.

This document captures the design needed to extend Provider Revoke to
`MICROSOFT` and `CUSTOM` credentials without changing the API shape or the
frontend control surface. The goal is to keep the v1 Google decision tree
intact and have new provider branches plug into it through capability metadata
plus a per-provider revoke implementation.

## Microsoft revoke

### Endpoint

Microsoft Entra ID (formerly Azure AD) does not expose a single canonical
RFC 7009 OAuth2 revocation endpoint analogous to Google's
`https://oauth2.googleapis.com/revoke`. There is no `/oauth2/v2.0/revoke`
on the v2 authority. The two practical controls are:

- `https://login.microsoftonline.com/{tenant}/oauth2/v2.0/logout` ends the
  user's sign-in session in the browser. It does not invalidate refresh tokens
  Athena is holding server-side, so it is not a substitute for revoke.
- Microsoft Graph delegated-permission revoke (revoking specific OAuth2
  permission grants for a service principal) requires Graph admin scopes that
  Athena does not currently request and operates at the consent-grant level
  rather than at the per-credential token level.

There is no straightforward equivalent to Google's `/revoke`. The closest
practical semantics for Athena's per-credential model are:

- Best-effort: call the v2 logout endpoint with the resolved tenant and treat
  it as an advisory signal. This does not guarantee refresh-token invalidation
  on the server side.
- Honest: surface the limitation to the operator. If we cannot guarantee
  provider-side invalidation, we must not claim we did.

The recommendation when this slice is picked up is to surface the limitation
honestly through capability metadata rather than ship a button that reports
success while the refresh token remains valid at Microsoft. That decision
should be revisited if Microsoft publishes an RFC 7009-shaped revoke
endpoint; the present plan is to document the constraint, not to paper over
it.

### Tenant templating

When Microsoft revoke is implemented, the per-credential `tenantId` must be
resolved with the same fallback the existing token endpoint uses. Today
`OAuthCredentialService.resolveTokenEndpoint` already templates the Microsoft
token endpoint with `tenantId` defaulting to `common` when the field is blank;
any Microsoft revoke implementation must reuse that same resolution to avoid a
second source of truth for tenant defaulting.

### Failure semantics

The v1 Google decision tree must be preserved verbatim:

- Provider success or already-invalid response (case-insensitive
  `invalid_token`, `invalid_grant`, `unsupported_token_type`): clear local
  tokens and evict the OAuth session cache.
- Provider 5xx, network failure, or any other 4xx body: preserve local tokens
  and surface a diagnostic so the operator can retry.

Operators have learned this mental model from the Google branch; the Microsoft
branch must use it without variation.

### Capability metadata

When MICROSOFT becomes supported, the backend capability decision tree must be
updated to return `providerRevokeSupported = true` for MICROSOFT rows that
have a stored access or refresh token, with
`providerRevokeUnsupportedReason = null`. UI does not need any change because
it consumes the capability flag.

Until MICROSOFT is supported, the capability decision tree returns
`providerRevokeSupported = false` with
`providerRevokeUnsupportedReason = "Provider-side revoke is not yet supported
for MICROSOFT"`. The exact reason string is owned by the round 2 backend
slice; this doc only requires the wording to be deterministic and localized
in one place.

## CUSTOM revoke

### Per-credential revoke endpoint

CUSTOM providers need a deterministic revoke URL. Two complementary sources:

- A new optional `revokeEndpoint` field on `OAuthCredentialOwner` (mirroring
  the existing `tokenEndpoint`), persisted via a new schema column.
- An env-key fallback `ECM_OAUTH_<KEY>_REVOKE_ENDPOINT` for env-managed
  CUSTOM owners that already use `ECM_OAUTH_<KEY>_TOKEN_ENDPOINT`.

Resolution order should mirror token-endpoint resolution: per-credential
field first, env-key fallback second.

### Behavior when configured

When a CUSTOM credential resolves to a non-blank revoke endpoint, treat that
endpoint as a Google-style RFC 7009 `/revoke` endpoint:

- `Content-Type: application/x-www-form-urlencoded`, body `token=<value>`.
- Refresh-token preferred, access-token fallback (same as v1 Google).
- Provider success or already-invalid 4xx body clears local tokens.
- Provider 5xx, network failure, or other 4xx preserves local tokens.

### Behavior when not configured

When the field and the env-key fallback are both blank, CUSTOM stays
unsupported and `providerRevokeUnsupportedReason` becomes
`"Provider-side revoke endpoint is not configured for this CUSTOM credential"`.
The UI surfaces that string verbatim through the capability tooltip; there is
no client-side branching on provider type.

### Capability metadata

The backend capability decision tree for CUSTOM is therefore conditional on
endpoint resolution at inventory-projection time. It must be evaluated under
the same projection that builds the inventory item so the capability flag and
the resolved endpoint stay consistent for a given response.

## Out of scope

Env-managed credential-key-only rows remain out of scope for revoke regardless
of provider. Athena cannot safely clear an external env secret on the
operator's behalf, and a button that reported success while the secret
remained valid in the operator's secret manager would be actively misleading.
For MICROSOFT and CUSTOM this means: even if the provider gains revoke
support, env-managed credential-key-only rows continue to return
`providerRevokeSupported = false` with the existing env-managed reason.

## Migration / data shape

- MICROSOFT: NO new migration is needed. Tenant resolution reuses the
  existing `tenantId` column already used by token endpoints.
- CUSTOM: needs a NEW optional `revoke_endpoint` column on the OAuth
  credential owner table (alongside the existing token endpoint column). The
  env-key fallback adds no schema change; it is read at runtime.

The next available migration number under
`ecm-core/src/main/resources/db/changelog/changes` is `092` (last is
`091-create-property-encryption-rewrap-jobs.xml`). A CUSTOM revoke slice
should reserve `092-add-oauth-credential-revoke-endpoint.xml` (or equivalent)
when it is picked up; the present doc only fixes the next-available index, it
does not write the migration.

## Test plan placeholder

When the work is picked up, extend the existing test classes rather than
introducing parallel ones:

- `OAuthCredentialServiceTest`: per-provider success / already-invalid 4xx /
  5xx / network-failure cases for MICROSOFT (when implemented) and CUSTOM with
  configured and unconfigured endpoints.
- `OAuthCredentialAdminServiceTest`: capability metadata projection for
  MICROSOFT and CUSTOM rows, including the env-managed-only branch and the
  CUSTOM-without-endpoint branch.
- `OAuthCredentialAdminControllerSecurityTest`: controller pass-through is
  expected to remain unchanged because new provider support is added at the
  service layer; the controller continues to surface the existing 200 / 400 /
  500 mapping.

The preflight script
(`scripts/oauth-credential-admin-preflight.sh`) targets these same test
classes and does not need to change when the new cases are added inside them.

## Sequencing

- The capability-metadata refactor (round 2 of this thread:
  `claude/oauth-revoke-meta-backend`, `claude/oauth-revoke-meta-ui`,
  `claude/oauth-revoke-meta-docs`) is now merged. The UI is metadata-driven
  before any broader provider ships, so adding MICROSOFT or CUSTOM support
  requires only a backend capability-tree update plus a per-provider service
  implementation.
- MICROSOFT and CUSTOM should ship as separate slices, not bundled. Their
  failure semantics differ enough - MICROSOFT lacks a canonical revoke
  endpoint, while CUSTOM is RFC 7009-shaped behind a configured endpoint -
  that bundling them would force a single review to weigh two different risk
  profiles in one pass.
- Each slice must reuse the v1 Google decision tree for failure handling and
  must not reintroduce client-side provider branching in the UI.
