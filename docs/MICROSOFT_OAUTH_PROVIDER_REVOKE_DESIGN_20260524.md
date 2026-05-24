# Microsoft OAuth Provider-Side Revoke — Discovery & Implementation Brief

Date: 2026-05-24

## Headline

**The existing codebase already implements the position recommended by the design doc** `docs/OAUTH_CREDENTIAL_PROVIDER_REVOKE_MICROSOFT_CUSTOM_DESIGN_FOLLOWUP_20260507.md` §52-57 — Microsoft is intentionally unsupported and the limitation is surfaced honestly through capability metadata at both the dynamic service-layer decision and the static inventory-projection decision. No code change is required to satisfy the design doc.

The remaining question is therefore: **does the gate want to ship something despite the design doc's "stay honest" recommendation?** Three viable options, each evaluated below:

- **Option N (no-op, recommended)** — keep the current honest unsupported state. Optional documentation tightening only.
- **Option L (advisory logout)** — ship a Microsoft-specific code path that calls the v2 `logout` endpoint as a best-effort signal, with UI/copy that **does not** claim refresh-token invalidation. Explicitly cautioned against by §188-189 of the design doc.
- **Option G (Graph consent revoke)** — requires new admin scopes Athena does not currently request. Out of scope for v1; would re-open the OAuth setup design.

The rest of this brief answers the 6 mandatory questions, with Option N as the default and Option L sketched as the fallback if a customer/market signal demands action.

## 1. Current Google / CUSTOM revoke code paths and failure semantics

### Entry point: `OAuthCredentialService.revokeProviderTokens(ownerType, ownerId)` (`OAuthCredentialService.java:161`)

The single public revoke API. Loads owner via `loadContext(...)`, calls `resolveProviderRevokeCapability(context)`, and short-circuits on unsupported (`IllegalArgumentException`). On supported providers it issues an RFC 7009-style `POST` to `capability.revokeEndpoint()` with form-encoded `token=<refreshOrAccess>` and `Content-Type: application/x-www-form-urlencoded`.

### Capability decision tree (dynamic): `resolveProviderRevokeCapability` (`OAuthCredentialService.java:270-303`)

Returns `ResolvedProviderRevokeCapability(supported, unsupportedReason, revokeEndpoint)`. Order:

1. Null provider → unsupported.
2. **`MICROSOFT` → unsupported with `MICROSOFT_REVOKE_UNSUPPORTED`** (`:278-280`).
3. Non-Google, non-CUSTOM provider → unsupported.
4. Env-managed credential-key only (no local token) → unsupported (`ENV_MANAGED_REVOKE_UNSUPPORTED`).
5. No local token at all → unsupported (`NO_LOCAL_TOKEN_REVOKE_UNSUPPORTED`).
6. `GOOGLE` → supported with `GOOGLE_REVOKE_URL = "https://oauth2.googleapis.com/revoke"`.
7. `CUSTOM` with persisted `revokeEndpoint` field OR `ECM_MAIL_OAUTH_<KEY>_REVOKE_ENDPOINT` env fallback → supported with that resolved endpoint.
8. `CUSTOM` with neither configured → unsupported (`CUSTOM_REVOKE_ENDPOINT_UNCONFIGURED`).

### Capability decision tree (static, for inventory projection): `OAuthCredentialAdminService.staticCapability` (`:112-133`)

Lighter version. Used when projecting a credential row into the admin inventory DTO. Mirrors the same reasons. **`MICROSOFT` → unsupported with `"Provider-side revoke is not yet supported for MICROSOFT"`** (`:118-120`). The exact wording matches the service-layer constant — single source of truth at the documentation level, two read sites.

### Token-revoke HTTP call (revoke success path)

`OAuthCredentialService.java:181-185` issues the POST. The token sent is refresh-token if present, else access-token (`:172`).

### Failure semantics (Google and CUSTOM, identical)

`OAuthCredentialService.java:186-224`:

- **HTTP 200** (caught after the `restTemplate.postForEntity(...)` returns normally, `:226-227`): clear local tokens via `context.adapter().clearTokens(owner.ownerId())` and `evictSession(...)`.
- **HTTP 4xx with parsed `error` in `{invalid_token, invalid_grant, unsupported_token_type}`** (`:202-207`): same as success — provider already considers the token invalid, so clearing locally reflects truth.
- **HTTP 4xx with other parsed `error`** (`:208-217`): throw `IllegalStateException(message, sanitizedHttpCause(ex))`. Message includes the OAuth standard `parsed.error()` code but **not** `parsed.errorDescription()` (per Phase 2 logging audit follow-up `41bd641`). The sanitized cause carries class name + status code, **not** the response body.
- **HTTP 5xx** (`:186-198`): same `IllegalStateException(..., sanitizedHttpCause(ex))` pattern; local tokens preserved.
- **HTTP 4xx with no parseable OAuth error body** (`:214-216` fallback): same as 5xx pattern.
- **`ResourceAccessException` (network failure)** (`:218-224`): `IllegalStateException(..., ex)` — body-safe to preserve `ex` as-is (no response body to leak).

### Capability flag projected to UI

`OAuthCredentialInventoryItem.providerRevokeSupported: boolean` + `providerRevokeUnsupportedReason: String?`. UI consumes these directly and does NOT branch on `provider == 'GOOGLE'`; the metadata is the single source of truth.

## 2. Should Microsoft use logout endpoint or Graph consent revoke?

**Recommended: neither.** The design doc §52-57 is explicit:

> The recommendation when this slice is picked up is to surface the limitation honestly through capability metadata rather than ship a button that reports success while the refresh token remains valid at Microsoft. That decision should be revisited if Microsoft publishes an RFC 7009-shaped revoke endpoint; the present plan is to document the constraint, not to paper over it.

And §188-189:

> Microsoft remains separate because it lacks a canonical revoke endpoint. Do not reintroduce client-side provider branching or a best-effort logout action that implies refresh-token invalidation.

The reasoning, restated:

- **`https://login.microsoftonline.com/{tenant}/oauth2/v2.0/logout` ends a browser session, NOT the refresh token.** Athena's per-credential model holds refresh tokens server-side; logout does not invalidate them on Microsoft's side. A revoke button that called logout and reported success would lie to the operator: the refresh token would remain valid and harvestable.
- **Microsoft Graph delegated-permission revoke requires admin scopes Athena does not currently request.** Adding those scopes is a much bigger surface (re-opens the OAuth setup design, requires admin consent flows, and operates at the consent-grant level rather than the per-credential-token level Athena models).
- **No RFC 7009-shaped endpoint exists.** Until Microsoft publishes one, there is no per-token endpoint analogous to Google's `/revoke`.

The capability metadata UI already does the right thing: the Provider Revoke control is disabled for MICROSOFT rows with the deterministic reason "Provider-side revoke is not yet supported for MICROSOFT" surfaced as the disabled-control tooltip. Operators see the limitation explicitly.

If the gate decides to ship something anyway, **Option L (advisory logout)** is the only practical path that does not require new admin scopes:

- Add a `MICROSOFT` branch in `resolveProviderRevokeCapability` that returns supported with a sentinel "logout" endpoint URL.
- Add a Microsoft-specific branch in `revokeProviderTokens` (or a separate `advisoryLogoutMicrosoftSession` method) that calls the v2 logout endpoint with the resolved tenant.
- **Crucial UI requirement**: the Microsoft Provider Revoke control must be relabeled — e.g. "End browser session (advisory)" — and its tooltip must explicitly state "this does NOT invalidate Athena's refresh token; the token remains valid at Microsoft until expiry". A new capability metadata field (e.g. `providerRevokeSemantics: "FULL" | "ADVISORY_LOGOUT_ONLY"`) would be required so the UI knows to relabel.
- Failure semantics: keep the Google v1 decision tree shape (success / network error / 4xx / 5xx) but the success path does NOT clear local tokens, because the tokens are still valid. This is the largest behavioral divergence from Google/CUSTOM and the strongest argument for Option N.

Option L is feasible. It is also the path the design doc most explicitly warns against. The brief recommends Option N unless a customer / market signal explicitly requires shipping something.

## 3. Backend / test file changes required

### Option N (recommended) — no production change

| File | Change |
|---|---|
| `docs/OAUTH_CREDENTIAL_PROVIDER_REVOKE_MICROSOFT_CUSTOM_DESIGN_FOLLOWUP_20260507.md` | Optional: add a short "Current state verified 2026-05-24" line confirming the unsupported branches at `OAuthCredentialService:278-280` and `OAuthCredentialAdminService:118-120` match the recommendation. Pure doc, no code. |
| This doc (`MICROSOFT_OAUTH_PROVIDER_REVOKE_DESIGN_20260524.md`) | Closes the discovery; can be the final track artefact if the gate accepts Option N. |

Optional tightening (still no behavioral change):

- The two unsupported-reason strings live in two places (`OAuthCredentialService.MICROSOFT_REVOKE_UNSUPPORTED` constant; literal string in `OAuthCredentialAdminService.staticCapability`). They are currently identical text. A defensive-cleanup slice could extract the literal to a shared constant; this is style-only and is not required for the design doc's intent.

### Option L (advisory logout) — backend changes

| File | Change |
|---|---|
| `OAuthCredentialService.java` | Add `MICROSOFT_LOGOUT_URL_TEMPLATE` constant. Branch the MICROSOFT case in `resolveProviderRevokeCapability` to return supported when local token is present, with the resolved logout URL (tenant-templated via the existing `resolveTokenEndpoint` tenant-defaulting logic). Branch `revokeProviderTokens` to call the logout endpoint with **no** token clear on success (advisory semantics). Add a `providerRevokeSemantics` field to `ResolvedProviderRevokeCapability` and propagate. |
| `OAuthProviderRevokeCapability.java` | Add `semantics: ProviderRevokeSemantics` field (`FULL` / `ADVISORY_LOGOUT_ONLY`). Update the constructor / record canonical methods. |
| `OAuthCredentialInventoryItem.java` | Add `providerRevokeSemantics` to the DTO so the UI can relabel. |
| `OAuthCredentialAdminService.java` | Mirror the MICROSOFT branch in `staticCapability` to return supported with `ADVISORY_LOGOUT_ONLY` semantics when `accessTokenStored || refreshTokenStored`. |
| `OAuthCredentialServiceTest.java` | New tests for: MICROSOFT capability returns supported + ADVISORY semantics when token present; MICROSOFT logout success preserves local tokens (the key behavioral lock); MICROSOFT logout 5xx / 4xx / network error preserves tokens and surfaces `IllegalStateException` via the existing sanitizer pattern. |
| `OAuthCredentialAdminServiceTest.java` | New tests for the static capability mirror plus the inventory projection emitting `providerRevokeSemantics`. |
| `scripts/oauth-credential-admin-preflight.sh` | No change — already targets these test classes per the design doc §172-174. |

### Option G (Graph) — out of scope

Would require new Microsoft Graph admin scopes (and re-opening the OAuth setup design to request them at authorization time). Not viable in v1; deferred.

## 4. Frontend changes

### Option N — none

The frontend already consumes `providerRevokeSupported` + `providerRevokeUnsupportedReason` from the inventory DTO. MICROSOFT rows already display the disabled Provider Revoke control with the deterministic reason. The hard-coded `provider === 'GOOGLE'` guard was removed in the round 2 capability-metadata refactor (per `OAUTH_CREDENTIAL_REVOKE_CAPABILITY_INTEGRATION_VERIFICATION_20260507.md` and noted at design doc §17). **No frontend change is required.**

### Option L — required

If advisory logout ships, the frontend must:

- Read the new `providerRevokeSemantics` field.
- For `ADVISORY_LOGOUT_ONLY` rows, relabel the Provider Revoke control to "End browser session (advisory)" (or similar copy that does NOT imply token invalidation).
- Tooltip on the control must explicitly state "This does NOT invalidate Athena's refresh token; the token remains valid at Microsoft until expiry."
- Confirmation dialog copy must match: do not use "Revoke" in the confirmation prompt for ADVISORY semantics.

The frontend service-shape guard layer (closed 2026-05-21) will need a small predicate extension to accept the new field; the response-contract test for the OAuth admin inventory endpoint may need a new field assertion.

### Option L scope risk

Option L's "feasibility" is offset by frontend-side copy + UX work: the relabeling needs i18n attention, the confirmation dialog needs new strings, and operators will need a brief release note explaining the difference between Google "Revoke" and Microsoft "End session". This is real product work that has historically motivated the design doc's "stay honest" recommendation.

## 5. Minimum implementation slice, tests, CI

### Option N slice (recommended; ~30 minutes)

1. (Optional) Append a "Current state verification" note to `OAUTH_CREDENTIAL_PROVIDER_REVOKE_MICROSOFT_CUSTOM_DESIGN_FOLLOWUP_20260507.md` confirming today's code matches the doc.
2. Commit the discovery doc (this file) with `[skip ci]` and close the discovery track.

No tests, no CI gate beyond the standard 7 jobs (which will all pass because nothing changed).

### Option L slice (if chosen; ~1.5 person-days)

| Step | Content |
|---|---|
| Slice L.1 (backend) | New constant + MICROSOFT branch in `resolveProviderRevokeCapability` + `revokeProviderTokens`; `providerRevokeSemantics` field on capability + inventory item; mirror in `staticCapability`. Tests in `OAuthCredentialServiceTest` and `OAuthCredentialAdminServiceTest` covering capability metadata, logout success / 5xx / 4xx / network error, and the key "no token clear on success" lock. |
| Slice L.2 (frontend) | Predicate extension for `providerRevokeSemantics`; relabel + tooltip + confirmation copy for ADVISORY semantics; new contract test asserting the field is locked in the inventory response shape. |
| Verification | Local `scripts/oauth-credential-admin-preflight.sh` before push (per `OAUTH_CREDENTIAL_STORE_CLOSEOUT_TODO_20260507.md` §105). |
| CI | Standard 7-job gate. `Backend Verify` for the new tests; `Frontend Build & Test` + `Frontend E2E Core Gate` for the UI relabel and predicate. The slice should split into the existing slice-pattern: backend commit → frontend commit → docs verification commit → CI record `[skip ci]`. |

### Test discipline

Key behavioral lock for Option L's MICROSOFT branch: a test that calls `revokeProviderTokens` (or the new `advisoryLogoutMicrosoftSession` method, depending on naming) on a Microsoft row with `accessToken="ms-access"` and `refreshToken="ms-refresh"`, mocks the v2 logout endpoint to return 200, and **asserts the local tokens are still present afterwards** (i.e. `verify(adapter, never()).clearTokens(...)`). This is the test that prevents a future contributor from copy-pasting Google's clear-on-success behavior into the Microsoft branch and silently losing recovery state.

Failure-path tests should follow the Phase 2 logging audit follow-up pattern (`Throwable.printStackTrace(PrintWriter)` capture, `assertFalse(stackEmission.contains(...))` against any provider response body fragment) so the cause-chain leak class does not re-open for this new branch.

## 6. Out of scope (explicit)

This slice does **not**:

- Modify OAuth login or refresh flows (`OAuthCredentialService.resolveAccessToken`, `.refreshAccessTokenNow`, `.exchangeAuthorizationCode`).
- Add a Microsoft OAuth setup UI (account creation, scope selection, tenant configuration). These already exist for the IMAP/SMTP use case and are not in scope for the revoke surface.
- Introduce Microsoft Graph admin scopes (Option G). These would re-open the OAuth setup design and require admin-consent flows; deferred.
- Touch the v1 GOOGLE or CUSTOM revoke decision trees.
- Modify the OAuth provider exception sanitizer (`sanitizedHttpCause`) shipped in Phase 2 follow-up `41bd641`.
- Add new env vars or env-key conventions.
- Modify `.env`, `application*.yml`, or Logback configuration.
- Implement `providerRevokeSemantics` if Option N is chosen.
- Modify the inventory or admin controller HTTP contract beyond adding one optional capability field (Option L only).
- Re-open the `siteInvitationService` stylistic cleanup or any other unrelated frontend service guard work.

## Verification (this discovery doc)

```bash
git status --short                           # M .env + this discovery doc only
git diff --check -- . ':!.env'               # passes
git diff --stat -- 'ecm-core/src/main/java/' # empty (no production change)
git diff --stat -- 'ecm-frontend/'           # empty
git diff --stat -- 'ecm-core/src/test/'      # empty
```

Confirmed at time of writing.

## Recommendation summary

| Question | Answer |
|---|---|
| Should this track ship code? | **No, by default.** The design doc's "honest unsupported" position is already implemented in code and metadata. |
| If a customer signal requires action? | Option L (advisory logout) is the only viable v1 path. Requires backend (~1 person-day) + frontend (~0.5 person-day) work. Behavioral divergence from Google/CUSTOM (no clear-on-success) is the largest UX risk. |
| Frontend change with Option N? | **None.** Capability metadata is already the single source of truth. |
| Frontend change with Option L? | Relabel + tooltip + confirmation copy + predicate extension. Real product work. |
| Track closure? | This brief is the final discovery artefact under Option N. If Option L is chosen, this brief becomes the design doc for that slice. |
