# Microsoft OAuth Revoke — Day-1 Taskbook / Gap Audit

- Date: 2026-06-25
- Status: Day-1 **read-only gap audit + taskbook; RATIFIED for Day-2 build.**
- Scope: Athena OAuth credential **revoke** for the `MICROSOFT` provider.
- Ratified decision: **§3(A) LOCAL-CLEAR revoke**.

## 0. Ratified decision

Microsoft is a fully supported OAuth provider for **connect + token refresh** (mail Office365 IMAP), but
**revoke is explicitly unsupported — and that is not an oversight.** Microsoft/Entra has **no RFC 7009-style
per-token revoke endpoint** the way Google does. So "Microsoft OAuth revoke" is a **product-semantics decision**,
not a "wire the missing URL" task.

**Decision: choose §3(A) LOCAL-CLEAR.** Microsoft revoke in Athena means: clear locally stored tokens and evict the
cached session so Athena immediately stops using that credential. It must not call a Microsoft provider revoke URL
(none exists for per-token revoke), must not invoke Microsoft Graph `revokeSignInSessions`, and must label the action
honestly as **local token clear, not Entra-side session revocation**.

## 1. Current code facts (grounded)

- `OAuthProviderType { GOOGLE, MICROSOFT, CUSTOM }`. Microsoft has connect/token URLs + scope
  (`MICROSOFT_AUTH_URL_TEMPLATE`, `MICROSOFT_TOKEN_URL_TEMPLATE`, `MICROSOFT_SCOPE_DEFAULT` = `outlook.office.com` IMAP).
  Mail accounts can be Microsoft OAuth (`MailAccount.OAuthProvider`; `MailOAuthCredentialOwnerAdapter` maps `MICROSOFT`).
- Revoke is gated per-provider in `OAuthCredentialService.resolveProviderRevokeCapability` (`:270-299`):
  - `GOOGLE` → built-in `https://oauth2.googleapis.com/revoke`.
  - `CUSTOM` → a configured RFC 7009 revoke endpoint (else unsupported).
  - **`MICROSOFT` → `unsupported(MICROSOFT_REVOKE_UNSUPPORTED)` (`:278-280`): "Provider-side revoke is not yet supported for MICROSOFT".**
- The revoke flow (`revokeProviderTokens`) on success / already-invalid clears local tokens + evicts the session:
  `context.adapter().clearTokens(...)` + `evictSession(...)` (`:261-262`, `:236-237`).
- **A standalone LOCAL clear path already exists:** `clearTokens(ownerType, ownerId)` (`:145-148`) →
  `adapter.clearTokens` + `evictSession`, with **no provider call**.
- **No Microsoft Graph client** exists in the codebase (confirmed) — a Graph `revokeSignInSessions` integration would be net-new.
- The Microsoft-unsupported message is **duplicated**: `resolveProviderRevokeCapability` (live) **and**
  `OAuthCredentialAdminService.staticCapability` (`:112-132`). Any change must keep both in sync or unify them.

## 2. The semantics nuance (why this is a decision, not a wiring)

Google's revoke = POST the token to a revoke endpoint. **Entra has no equivalent per-token revoke endpoint.** Entra
revocation is fundamentally different and broader:

- **Microsoft Graph `POST /users/{id}/revokeSignInSessions`** — revokes **all** the user's refresh tokens across
  **all** apps (not just Athena's); needs a Graph app registration + the admin-level `User.RevokeSessions.All` permission.
- Removing the app's OAuth2 grant / consent — also Graph/admin, also app-wide.
- There is **no** "revoke just this one Athena mail credential's Microsoft token" provider call.

So "revoke a Microsoft credential" can only honestly mean one of §3.

External fact check (2026-06-25, official Microsoft surfaces):
- Microsoft Graph [`user: revokeSignInSessions`](https://learn.microsoft.com/en-us/graph/api/user-revokesigninsessions?view=graph-rest-1.0)
  invalidates **all** refresh tokens issued to applications for the user (plus browser session cookies) and requires
  `User.RevokeSessions.All`. This matches §3(B)'s broad-scope warning.
- Microsoft Entra [OpenID Connect discovery](https://login.microsoftonline.com/common/v2.0/.well-known/openid-configuration)
  exposes authorize/token/device/logout endpoints, but no `revocation_endpoint`. This is why §3(A)/(C) cannot be
  replaced by a simple Microsoft RFC-7009 URL.

## 3. Options — owner decides the SEMANTICS

- **(A) LOCAL-CLEAR revoke (RATIFIED small loop).** Microsoft revoke = clear the locally stored tokens + evict
  the session (reuse the existing `clearTokens`/`evict` path; **no provider call**). Athena immediately stops using
  the credential. The capability/message must be **honest**: "local token clear; the Entra session itself lives until
  expiry unless an admin runs Graph `revokeSignInSessions`." Small (reuse `clearTokens`; flip the MICROSOFT branch
  from unsupported → local-clear; unify the duplicated capability). Gives the admin the actionable outcome.
- **(B) Graph `revokeSignInSessions` (separate, bigger product/security decision — NOT this loop).** True Entra-side
  revocation. Net-new Graph client + app registration + admin `User.RevokeSessions.All` consent; and it is **broad**
  (all the user's apps). A real product decision — defer unless the owner wants Entra-side revocation and accepts the
  breadth + privileged permission.
- **(C) Keep unsupported, improve the message only.** Smallest. Leave revoke unsupported for Microsoft but make the
  reason accurate/actionable. No Athena revoke action for Microsoft.

**Decision: (A).** An admin revoking a Microsoft mail credential wants Athena to **stop using it** — local-clear
delivers exactly that, honestly, reusing existing code, without a privileged Graph integration. (B) is a separate
decision (breadth + admin perms); (C) leaves the admin without an action.

## 4. Ratified Day-2 slice

- `resolveProviderRevokeCapability`: `MICROSOFT` → a "supported, local-clear" capability. Extend the capability model
  with an explicit mode (`PROVIDER_REVOKE` vs `LOCAL_CLEAR` vs `UNSUPPORTED`). **Do not** make Microsoft
  `supported=true` with `providerRevokeUnsupportedReason=null` and leave the UI calling it plain "Provider Revoke";
  that would hide the most important semantics boundary.
- `revokeProviderTokens`: for `MICROSOFT` (local-clear), **skip** the provider HTTP call; do `clearTokens` + `evictSession`
  directly. `GOOGLE`/`CUSTOM` unchanged (provider call + clear).
- Unify the duplicated capability (`resolveProviderRevokeCapability` + `staticCapability`) so both report the same
  Microsoft local-clear semantics.
- The existing admin endpoint `POST /oauth-credentials/{id}/revoke` then succeeds for Microsoft (local-clear) instead
  of throwing unsupported; the capability metadata, OpenAPI text, button label/tooltip, and success/error copy clarify
  "local token clear (not Entra-side revoke)".

## 5. Boundaries / Out of scope

- **No** Microsoft Graph integration / `revokeSignInSessions` (that is option B — a separate taskbook).
- **No** provider HTTP call for Microsoft (Entra has no per-token revoke endpoint).
- **No** change to `GOOGLE`/`CUSTOM` revoke.
- **No** change to connect / token-refresh.
- The capability message **must be honest** (local-clear ≠ Entra-side revocation).

## 6. Tests (Day-2)

- `OAuthCredentialServiceTest`: `MICROSOFT` revoke → `clearTokens` + `evictSession` called, **no** provider HTTP call
  (mock `restTemplate`, assert never invoked); capability resolves to supported/local-clear.
- Capability consistency: `resolveProviderRevokeCapability` and `staticCapability` agree for `MICROSOFT`.
- `OAuthCredentialAdminControllerTest`: `revoke` for a Microsoft credential → 200 (local-clear), not 400-unsupported.
- `OAuthCredentialAdminControllerSecurityTest`: admin-gate unchanged (CLAUDE.md convention).
- `OAuthCredentialAdminPage.test.tsx` / `oauthCredentialAdminService.test.ts`: inventory shape accepts the new
  capability mode; Microsoft rows render/action as local-clear, not as ordinary provider revoke.

## 7. Ratification checklist

- [x] Microsoft revoke **semantics** = **(A) local-clear**.
- [x] Local-clear reuses `clearTokens`/`evict`, **no** provider call, **honest** capability message.
- [x] Add an explicit capability mode/kind; do not encode Microsoft local-clear as ordinary provider revoke.
- [x] No Graph integration in this line (B is a separate taskbook).
- [x] No change to GOOGLE/CUSTOM revoke or connect/token-refresh.

## 8. Recommendation

Build **(A) local-clear** as the Microsoft revoke semantics + the Day-2 slice. It is the honest, small, closeable
loop — Athena stops using a revoked Microsoft credential, reusing existing code, without a privileged Graph
integration (which (B) would be, and which remains a separate product/security decision).
