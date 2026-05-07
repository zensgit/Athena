# OAuth Credential Provider Revoke UI - Design and Verification

Date: 2026-05-07

## Context

The OAuth credential admin inventory at `/admin/oauth-credentials` already exposes
`Refresh Now` (force a refresh-token grant) and `Require Reauth` (clear Athena's
local token copy and require the owner to reconnect). Provider-side revoke has
been intentionally separate because each provider needs explicit revoke endpoint
support and failure semantics.

This change adds the per-row `Provider Revoke` action to the admin UI. The
backend contract is fixed and is being implemented in parallel by Package A; the
frontend consumes the agreed API shape only.

Backend contract consumed:

- `POST /api/v1/admin/oauth-credentials/{credentialId}/revoke`
- Empty request body.
- Success (HTTP 200): `OAuthCredentialInventoryItem` JSON matching the inventory
  list / refresh-now / require-reauth shape. After revoke,
  `accessTokenStored`, `refreshTokenStored`, and `connected` are false and
  `tokenExpiresAt` may be null.
- Failure: surface `response.data.message` from the backend; do not introspect
  status codes. The 400 (unsupported provider, env-managed-only, no local token)
  and 500 (provider 5xx / network) shapes both arrive with a backend-supplied
  message.
- Token values are never returned. The existing redaction guarantees of this
  admin surface continue to apply.

## Design

### Frontend UI behavior

- Each row in the credential inventory now exposes three actions, in order:
  1. `Refresh Now`
  2. `Require Reauth`
  3. `Provider Revoke` (new, anchored last)
- `Provider Revoke` is enabled only when `provider === 'GOOGLE'` AND
  (`accessTokenStored || refreshTokenStored`). Otherwise it is disabled.
- While any per-row action is in flight on a given row (revoke, refresh, or
  reauth), all three buttons on that row are disabled. This is consistent with
  the existing pattern (`Refresh Now` already disables when reauth is in flight,
  and vice versa) and prevents an operator from stacking concurrent state
  transitions on the same credential. The `Refresh Now` and `Require Reauth`
  semantics are unchanged; the change here is only the addition of an in-flight
  guard against the new revoke transition.
- The button reads `Provider Revoke` when idle and `Revoking...` while the call
  is in flight. Color is `error` to match the destructive nature.

### Confirm dialog rationale

The confirm dialog text is intentionally explicit that this contacts the
provider, not just Athena's local store, to avoid an operator confusing it with
`Require Reauth`:

```
Revoke OAuth token at the provider for ${ownerType} ${ownerId}? This contacts
Google to invalidate the token. The owner must reconnect afterwards. This is
different from Require Reauth, which only clears Athena's local copy.
```

The reference to Google is intentional: provider revoke is gated to GOOGLE rows
in this slice, matching the backend's initial provider coverage.

### Failure handling

- On success, the row is replaced from the redacted backend response (same
  pattern as `Refresh Now`'s success branch). The chips for Connected, Access
  token, and Refresh token flip to the unfilled `default` color, reflecting that
  the local token state has been cleared.
- On failure, the page surfaces the backend-supplied `response.data.message` in
  the existing error Alert and reloads the inventory with `preserveError: true`
  so any token-clearing side effects observed by the backend remain visible.
  This is the same shape as `Refresh Now`'s error branch.
- The frontend never branches on HTTP status codes. The backend owns the wording
  for unsupported-provider (400), env-managed-only (400), no-local-token (400),
  and provider 5xx / network (500) cases.

## Verification

### Targeted frontend tests

```bash
cd ecm-frontend
CI=true npm test -- --runTestsByPath \
  src/pages/OAuthCredentialAdminPage.test.tsx \
  src/components/layout/MainLayout.menu.test.tsx \
  --watchAll=false
```

Result: 2 suites passed, 16 tests passed (10 pre-existing + 6 new for revoke).

New cases added in `OAuthCredentialAdminPage.test.tsx`:

- `Provider Revoke is enabled for GOOGLE rows with a stored token`.
- `Provider Revoke is disabled for non-GOOGLE rows` (provider override to
  `MICROSOFT`).
- `Provider Revoke is disabled for GOOGLE rows with no stored token`
  (`accessTokenStored=false`, `refreshTokenStored=false`).
- `does not revoke OAuth token when confirmation is cancelled` (cancel path).
- `revokes OAuth token at the provider and replaces the row from the redacted
  response` — asserts that after the redacted success response the row's
  stored-token guard re-disables the Provider Revoke button (an observable
  effect of the row having been replaced from the redacted response). This
  observable check avoids direct DOM-class probing, which `testing-library`
  lint rules prohibit.
- `surfaces revoke provider errors and reloads inventory` — asserts the backend
  message is rendered and `listCredentials` is called twice (mount + reload).

### Static gates

```bash
cd ecm-frontend
npm run lint
CI=true npm run build
```

Result: both pass. The CRA build emits only the existing bundle-size advisory
and the Node `fs.F_OK` deprecation warning, both pre-existing.

### Repository hygiene

```bash
git diff --check
```

Result: passes.

## Files Changed

- `ecm-frontend/src/services/oauthCredentialAdminService.ts`
- `ecm-frontend/src/pages/OAuthCredentialAdminPage.tsx`
- `ecm-frontend/src/pages/OAuthCredentialAdminPage.test.tsx`
- `docs/OAUTH_CREDENTIAL_PROVIDER_REVOKE_UI_DESIGN_VERIFICATION_20260507.md`

## Remaining Work

- Backend implementation of `POST /api/v1/admin/oauth-credentials/{id}/revoke`
  is owned by Package A in this parallel slice. A live full-stack smoke is
  deferred until the backend lands; the targeted UI tests stub the service so
  the frontend gates are independent.
- Provider-revoke coverage is currently scoped to GOOGLE. Extending to Microsoft
  or custom providers requires additional backend revoke-endpoint support and
  per-provider failure semantics, and is intentionally out of scope here.
