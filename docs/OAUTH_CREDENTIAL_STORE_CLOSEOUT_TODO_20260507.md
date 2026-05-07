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

Design points for a future round once v1 Google revoke has soaked:

- Microsoft uses tenant-scoped revoke endpoints; verify per-tenant URL templating (single-tenant vs multi-tenant `common`/`organizations` authorities) and confirm the success / already-invalid response shapes before adding a Microsoft branch.
- CUSTOM providers need either a per-credential `revokeEndpoint` field on `OAuthCredentialOwner` or an env-key fallback (`ECM_OAUTH_<KEY>_REVOKE_ENDPOINT`) so Athena can resolve a revoke URL deterministically for an arbitrary provider.
- Failure semantics for both providers must distinguish "already invalid" (clear-local) from "5xx / unreachable" (preserve-local), mirroring the v1 Google decision tree exactly so operators learn one mental model.
- The UI should expose the same per-row Provider Revoke control once backend coverage extends; the disable-state must be gated purely on stored-token presence plus supported-provider lookup, not on a hard-coded `provider === 'GOOGLE'` check.

## Operational guidance

Operators should run `scripts/oauth-credential-admin-preflight.sh` before declaring a slice done locally. The script runs OAuth admin backend targeted tests, OAuth admin frontend targeted tests, and frontend lint plus production build, in that order, and exits at the first failing step. Once local preflight passes, the user's existing CI gates remain authoritative: Backend Verify, Frontend Build & Test, Phase C Security Verification, Frontend E2E Core Gate, Phase 5 Mocked Regression Gate, and Acceptance Smoke. Local preflight is a fast filter; CI gates are the merge bar.
