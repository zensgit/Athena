# Microsoft OAuth Revoke — Development & Verification

- Date: 2026-06-25
- Line: Athena OAuth credential **revoke** for the `MICROSOFT` provider — **LOCAL-CLEAR** semantics.
- State: **COMPLETE on `main`.** taskbook → implementation → review → CI fully closed.
- Repos: Athena only. No Graph integration; no provider call for Microsoft; no change to GOOGLE/CUSTOM revoke.

## 1. State at a glance

Microsoft OAuth credentials can now be **"revoked" in Athena via LOCAL-CLEAR**: clear the locally stored tokens +
evict the cached session (no provider call), surfaced with an **explicit capability mode** and **honest UI copy**.
Because Microsoft/Entra has **no RFC 7009 per-token revoke endpoint**, local-clear is the honest actionable
semantics — Athena immediately stops using the credential — and true Entra-side session revocation
(Graph `revokeSignInSessions`) is explicitly out of scope.

| PR | What | Merge SHA |
|---|---|---|
| #37 | Day-1 taskbook — gap audit + ratified §3(A) local-clear | `131bcc4` |
| #38 | Implementation — mode enum + local-clear + unified capability + UI + tests | `7332308` |

## 2. The gap + the decision (recap)

- Microsoft was a supported **connect / token-refresh** provider (mail Office365), but **revoke was unsupported —
  and not by oversight**: Entra has no RFC 7009 per-token revoke endpoint. (Externally confirmed: Entra OpenID
  discovery exposes `authorize`/`token`/`logout` but **no `revocation_endpoint`**.) Google has a revoke URL; CUSTOM
  uses a configured one; Microsoft has none.
- **Ratified §3(A) LOCAL-CLEAR.** Microsoft revoke = clear local tokens + evict session (reuse the existing
  `clearTokens`/`evict`), labeled honestly as **local clear, not Entra-side**. **NOT** Graph `revokeSignInSessions`
  (broad — all the user's apps — + admin `User.RevokeSessions.All`; a separate decision).

## 3. What was built (code-grounded)

- **`OAuthRevokeCapabilityMode { PROVIDER_REVOKE, LOCAL_CLEAR, UNSUPPORTED }`** — an explicit mode so the inventory /
  UI cannot mistake a local-clear for an ordinary provider revoke.
- **`OAuthProviderRevokeCapability`** — now `(supported, unsupportedReason, mode)`; the 2-arg convenience constructor
  defaults `mode` to `PROVIDER_REVOKE` (when supported) / `UNSUPPORTED` (when not), so legacy call sites stay correct.
- **`OAuthCredentialService.resolveProviderRevokeCapability`** — `MICROSOFT` → `LOCAL_CLEAR`; `GOOGLE`/`CUSTOM` →
  `PROVIDER_REVOKE`; otherwise `UNSUPPORTED`. **`revokeProviderTokens`**: for `LOCAL_CLEAR`, do `clearTokens` +
  `evictSession` with **no provider HTTP call**; `GOOGLE`/`CUSTOM` unchanged (provider call + clear). Honest javadoc.
- **`OAuthCredentialAdminService.staticCapability`** — unified with the live resolver (`MICROSOFT` → `LOCAL_CLEAR`),
  so the inventory list and the live revoke action report the same mode.
- **`OAuthCredentialInventoryItem`** — carries the revoke mode.
- **Frontend `OAuthCredentialAdminPage`** — label "**Local Clear**" vs "Provider Revoke"; tooltip: *"Clears
  Athena-local Microsoft OAuth tokens only. Entra sessions remain until expiry or a separate Graph
  revokeSignInSessions action."* + the `oauthCredentialAdminService` client/type.
- The existing admin endpoint `POST /oauth-credentials/{id}/revoke` now **succeeds** for Microsoft (local-clear)
  instead of 400-unsupported.

## 4. Boundaries held

- **No** Microsoft Graph integration / `revokeSignInSessions` (separate taskbook).
- **No** provider HTTP call for Microsoft (Entra has no per-token revoke endpoint).
- `GOOGLE`/`CUSTOM` revoke **unchanged** (still `PROVIDER_REVOKE`, provider call).
- **No** change to connect / token-refresh.
- Capability + UI copy are **honest** (local-clear ≠ Entra-side revocation).

## 5. Verification

**CI — #38 7/7 green** (run `28172413995`): Acceptance Smoke, Backend Verify, Frontend Build & Test, Frontend E2E
Core Gate, Phase 5 Mocked Regression Gate, Phase C Security Verification, Property Encryption Closeout Gate.

**Tests:**
- `OAuthCredentialServiceTest` — `revokeProviderTokensLocalClearsMicrosoftWithoutProviderCall` (`verify(adapter).clearTokens`
  + `MockRestServiceServer.verify()` asserts **no** HTTP call); `providerRevokeCapabilityReportsMicrosoftLocalClearMode`.
- `OAuthCredentialAdminServiceTest` — static capability reports the Microsoft local-clear mode.
- `OAuthCredentialAdminControllerSecurityTest` — admin-gate (CLAUDE.md `*SecurityTest` convention).
- `OAuthCredentialAdminPage.test.tsx` + `oauthCredentialAdminService.test.ts` — Microsoft rows render/action as
  local-clear, not as an ordinary provider revoke.

**Local targeted run:**
```bash
cd ecm-core
./mvnw -q -Dtest=OAuthCredentialServiceTest,OAuthCredentialAdminServiceTest,OAuthCredentialAdminControllerSecurityTest test
cd ../ecm-frontend
npm test -- --watchAll=false OAuthCredentialAdminPage oauthCredentialAdminService
```

## 6. Semantics (the point)

- **Microsoft "revoke" in Athena = LOCAL CLEAR:** the tokens Athena stores are removed + the session evicted →
  Athena immediately stops using the credential. No provider call.
- **It is NOT Entra-side session revocation:** the Microsoft refresh tokens / sign-in sessions live until expiry,
  unless an admin runs Microsoft Graph `revokeSignInSessions` (broad — all the user's apps — + admin
  `User.RevokeSessions.All`). The UI says exactly this.
- `GOOGLE`/`CUSTOM` remain true provider revoke (POST to the revoke endpoint + clear).

## 7. Out of scope / re-entry

- Microsoft Graph `revokeSignInSessions` (true Entra-side revocation) — a separate taskbook (broad + privileged).
- No change to the storage/quota line, the logging line, `GOOGLE`/`CUSTOM` revoke, or connect/token-refresh.

## 8. Conclusion

Microsoft OAuth revoke is complete and verified on `main` (taskbook `131bcc4` → implementation `7332308`). An admin
revoking a Microsoft mail credential now gets the honest, actionable outcome — Athena stops using it (local clear) —
surfaced with an explicit capability mode and honest UI copy, without a privileged Graph integration. True Entra-side
session revocation remains a separate, deliberate decision.
