# OAuth Credential Store - Closeout TODO

Date: 2026-05-07

## Context

The OAuth Credential Store admin surface is the cross-integration `/admin/oauth-credentials` view that lets administrators inspect and operate on stored OAuth credential rows aggregated by the generic `OAuthCredentialService` and per-owner `OAuthCredentialOwnerAdapter` implementations. It deliberately keeps token values out of every response and exposes a small, bounded set of write actions. This document records what has shipped and what remains as design follow-up so the next round of work can pick up cleanly without re-deriving the prior decisions.

## Done (as of 2026-05-07)

- Inventory (read-only): `docs/OAUTH_CREDENTIAL_ADMIN_INVENTORY_DESIGN_VERIFICATION_20260506.md`
  - `GET /api/v1/admin/oauth-credentials` with admin role gating, JPQL projection, and token-redacted DTOs.
  - `/admin/oauth-credentials` page with summary cards, owner-type / provider filters, and admin menu placement.
- Require Reauth (local clear): `docs/OAUTH_CREDENTIAL_ADMIN_REAUTH_DESIGN_VERIFICATION_20260506.md`
  - `POST /api/v1/admin/oauth-credentials/{id}/require-reauth` clears Athena's stored access and refresh tokens through the owner adapter, evicts the OAuth session cache, and returns the redacted projection.
  - Per-row Require Reauth action with confirmation; disables once tokens are cleared.
- Refresh Now (forced refresh-token grant): `docs/OAUTH_CREDENTIAL_ADMIN_REFRESH_NOW_DESIGN_VERIFICATION_20260506.md`
  - `POST /api/v1/admin/oauth-credentials/{id}/refresh-now` forces a refresh-token grant via `OAuthCredentialService.refreshAccessTokenNow`, maps `OAuthReauthRequiredException` to HTTP 409, and never returns token values.
  - Per-row Refresh Now action with confirmation, error surfacing, and inventory reload after provider/config failure.
- Provider Revoke (Google v1): `docs/OAUTH_CREDENTIAL_PROVIDER_REVOKE_BACKEND_DESIGN_VERIFICATION_20260507.md` and `docs/OAUTH_CREDENTIAL_PROVIDER_REVOKE_UI_DESIGN_VERIFICATION_20260507.md`
  - `POST /api/v1/admin/oauth-credentials/{id}/revoke` calls Google's revoke endpoint for stored local tokens, then returns the redacted projection.
  - Per-row Provider Revoke action is enabled only for Google credentials with a stored access or refresh token.
- Provider Revoke (Google v1) integration shipped end-to-end on 2026-05-07: `docs/OAUTH_CREDENTIAL_PROVIDER_REVOKE_INTEGRATION_VERIFICATION_20260507.md`
  - Commit range: `06be7fb` (backend), `9ea6ce7` (frontend), `a114f1d` (preflight + closeout TODO), `a7275da` (integration reconciliation), `1c2603c` (integration verification doc).
  - Local preflight and remote CI both green; backend, frontend, and acceptance smoke gates passed for `a7275da`.

## In-flight (round 2: capability-metadata refactor)

Round 2 replaces the client-side `provider === 'GOOGLE'` check with server-side capability metadata so the UI is provider-agnostic and ready for MICROSOFT / CUSTOM extension without UI churn.

- Package A (`claude/oauth-revoke-meta-backend`): adds `providerRevokeSupported` and `providerRevokeUnsupportedReason` fields to `OAuthCredentialInventoryItem`. The capability decision tree is the single source of truth for whether a row supports Provider Revoke and why a row does not.
- Package B (`claude/oauth-revoke-meta-ui`): drops the hard-coded `provider === 'GOOGLE'` guard, gates the Provider Revoke control on `providerRevokeSupported`, and surfaces `providerRevokeUnsupportedReason` through a tooltip when the control is disabled.
- Package C (`claude/oauth-revoke-meta-docs`, this branch): updates this closeout TODO and adds the Microsoft / CUSTOM revoke design follow-up doc.

Integration order remains A then B then C, matching round 1. The targeted-test set is unchanged; capability-metadata coverage is added inside the existing backend test classes (`OAuthCredentialServiceTest`, `OAuthCredentialAdminServiceTest`, `OAuthCredentialAdminControllerSecurityTest`) and existing frontend test files (`OAuthCredentialAdminPage.test.tsx`, `MainLayout.menu.test.tsx`), so `scripts/oauth-credential-admin-preflight.sh` does not need to change in this round.

## v1 Revoke invariants

v1 Provider Revoke scope is bounded to the following invariants:

- Only `GOOGLE` is supported in v1. `MICROSOFT`, `CUSTOM`, and env-managed-only rows must return an explicit unsupported response and the UI must reflect that bound.
- Refresh token is preferred when available; access token is the fallback when no refresh token is stored.
- On provider success or already-invalid response, Athena clears its locally stored tokens through the existing owner adapter path.
- On provider 5xx or network failure, Athena preserves its locally stored tokens so the operator can retry without losing recovery state.
- The unsupported-provider branch returns HTTP 400 with a deterministic message. The current UI disables unsupported providers from row metadata instead of inferring from failed calls.

## v1 Revoke limitations / known gaps

- No Microsoft revoke. Microsoft uses tenant-scoped revoke endpoints with different confirmation semantics than Google's `oauth2/revoke`, and v1 does not attempt that.
- No CUSTOM provider revoke. Athena does not yet model a per-credential revoke endpoint contract for CUSTOM providers, so v1 cannot safely call an arbitrary URL.
- Env-managed credential-key-only rows cannot be revoked. The credential key references an external secret that Athena does not own; clearing or revoking that secret has to happen in the operator's secret manager.
- Provider-side revoke does not affect refresh tokens issued to other clients sharing the same Google OAuth client_id. It only invalidates the specific token Athena holds, so other applications using the same client_id retain their own grants.

## Remaining OAuth owner adapters

The only `OAuthCredentialOwnerAdapter` currently in tree is the mail-account adapter (`MailOAuthCredentialOwnerAdapter`). New adapter implementations - for example for additional integration owners - are out of scope for this closeout. The extension point is the `OAuthCredentialOwnerAdapter` interface; any new adapter is responsible for owner-side token mirroring (including clear-on-reauth and post-refresh save) so the existing admin actions continue to behave consistently without controller changes.

## Microsoft / CUSTOM revoke design follow-up

See `docs/OAUTH_CREDENTIAL_PROVIDER_REVOKE_MICROSOFT_CUSTOM_DESIGN_FOLLOWUP_20260507.md` for the Microsoft and CUSTOM revoke design follow-up.

## Operational guidance

Operators should run `scripts/oauth-credential-admin-preflight.sh` before declaring a slice done locally. The script runs OAuth admin backend targeted tests, OAuth admin frontend targeted tests, and frontend lint plus production build, in that order, and exits at the first failing step. Once local preflight passes, the user's existing CI gates remain authoritative: Backend Verify, Frontend Build & Test, Phase C Security Verification, Frontend E2E Core Gate, Phase 5 Mocked Regression Gate, and Acceptance Smoke. Local preflight is a fast filter; CI gates are the merge bar.
