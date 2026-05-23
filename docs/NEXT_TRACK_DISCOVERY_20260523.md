# Next Track Discovery — Read-Only Scoping

Date: 2026-05-23

## Correction (2026-05-23, post-Explore verification)

The original ranking below put **Protocol Endpoint Security Tests** as candidate #1 (default recommendation). A follow-up Explore re-verification before any design work began showed that #1 was sourced from a stale closeout doc and no longer reflects the codebase. Both of its load-bearing premises are wrong:

1. **"CMIS = HTTP Basic auth" is wrong.** Production `SecurityConfig` (`ecm-core/src/main/java/com/ecm/core/config/SecurityConfig.java:41-69`) configures OAuth2 Resource Server + JWT Bearer for everything under `/api/**`. CMIS paths (`/api/cmis/atom/**`, `/api/cmis/browser/**`, `/api/v1/cmis/**`) fall through the catch-all `.requestMatchers("/api/**").authenticated()` rule. There is **no `.httpBasic(...)` call in production config**. The `httpBasic()` calls that appear in existing `TestSecurityConfig` blocks are local test-config conveniences and do not reflect the production auth model.

2. **"Protocol endpoint security tests still need to be done" is wrong.** The following tests already exist and cover the protocol-endpoint surface:

   - `ecm-core/src/test/java/com/ecm/core/controller/CmisAtomPubControllerSecurityTest.java`
   - `ecm-core/src/test/java/com/ecm/core/controller/CmisBrowserControllerSecurityTest.java`
   - `ecm-core/src/test/java/com/ecm/core/integration/wopi/controller/WopiHostControllerSecurityTest.java`
   - `ecm-core/src/test/java/com/ecm/core/integration/wopi/controller/WopiIntegrationControllerSecurityTest.java`
   - `ecm-core/src/test/java/com/ecm/core/controller/TransferReceiverControllerSecurityTest.java`
   - `ecm-core/src/test/java/com/ecm/core/controller/TransferReplicationControllerSecurityTest.java`
   - `ecm-core/src/test/java/com/ecm/core/config/SecurityConfigProtocolSecurityTest.java` — covers the inverse drift guards (CMIS paths are NOT permitAll; Bearer header is intentionally ignored on `/wopi/**` and `/api/v1/transfer/receiver/**` via `BearerTokenResolver` returning `null`)

   The `SECURITY_TEST_LEGACY_FILL_ROUND6_AND_THREAD_CLOSEOUT_20260428.md` §4.2.3 description of these as "untested protocol endpoints requiring their own design effort" was accurate on 2026-04-28 but stale by 2026-05-23.

**Effect on this discovery doc:**

- Candidate **#1 is WITHDRAWN** (see the stub in its section below). Residual gaps in this area (e.g., a finer-grained `BearerTokenResolver` unit test, or polish patches when a protocol changes) are not large enough to be a track.
- **Logging Sensitive-Data Audit** (was #2) is now the chosen next track. It matches the closeout doc's "new operational risk" framing and was not affected by the stale-doc issue.
- **Microsoft OAuth Provider-Side Revoke** (was #3) remains a viable later track, contingent on a customer/market signal.
- Recommendation framework table at the bottom has been updated to reflect the new ranking.

**Banked lesson:** closeout-doc carve-outs about "X is deferred to its own design effort" can themselves go stale weeks later; before turning such a carve-out into a track, re-verify the carve-out is still true by grepping for the implementations that would have closed it.

## Purpose

The two test-defense tracks closed:

- Frontend service response-shape guard — closed `0741087`, doc `docs/FRONTEND_SERVICE_GUARD_TRACK_CLOSEOUT_20260521.md`.
- Backend response-contract test — closed `d266cc9`, doc `docs/BACKEND_RESPONSE_CONTRACT_TRACK_CLOSEOUT_20260523.md`.

The backend closeout (§137-147) explicitly tells the next track to come from product value or new operational risk, not generic contract-test expansion. This document is the read-only scoping pass that picks the next track. No code changes, no commits.

## Exclusion zone (do not propose these)

- additional frontend service guard slices, predicate widening, or response-shape tests;
- additional backend response-contract tests for Tier 2 / lower-traffic controllers;
- per-field nullability audits of Tier 2/3 controllers from the closed backend TODO (the gaps are intentional — see backend closeout §99-115);
- anything in the `CLAUDE.md` "Do NOT" list (XML manifest, Redis locks, AOP tenant interception, ACL mapping changes, delete propagation, alien node handling, storage routing without resolving ADR-001);
- `siteInvitationService` stylistic cleanup — already flagged as opportunistic micro-polish in the frontend closeout §170, not a track.

## Discovery method

- Read both closeout docs verbatim to lock the boundary.
- Surveyed `docs/` for outstanding TODO / DEFERRED / FOLLOWUP / GAP doc trails.
- Surveyed `CLAUDE.md` "Frozen Architecture Decisions", "Do NOT" list, "Roadmap Status", and "Current Handoff".
- Surveyed recent `git log` for recurring touch themes.
- Cross-referenced `MEMORY.md` banked lessons for recurring failure modes that warrant a dedicated track.

Per the user's preference for double-axis framing: candidates are scored on **user value** (product capability vs reliability defense) and **gate fitness** (clear "shipped" state, parallelizable, follows existing playbook).

## Top 3 candidates (ranked)

### 1. Protocol Endpoint Security Tests — `WITHDRAWN 2026-05-23`

**Status:** WITHDRAWN before any design work began. See the Correction block at the top of this document for the full reasoning. Summary:

- The "CMIS = HTTP Basic" premise was wrong; production auth is JWT Bearer for all `/api/**` paths including CMIS (`SecurityConfig.java:41-69`).
- The "protocol endpoints are untested" premise was wrong; six dedicated `*SecurityTest.java` files plus `SecurityConfigProtocolSecurityTest.java` already cover the surface, including the inverse drift guards for `BearerTokenResolver` opaque-token paths.
- Residual gaps (e.g., finer-grained `BearerTokenResolver` unit tests, polish patches when a protocol changes) are not large enough to be a track and should be folded opportunistically into any future PR that touches the relevant code.

The original pitch and parallel-slice plan below are preserved as the historical artifact of what the discovery doc proposed before re-verification. Do not act on them.

<details>
<summary>Original (withdrawn) pitch — historical artifact only</summary>

**Original one-line pitch:** Design and ship security tests for protocol-specific controllers (CMIS AtomPub/Browser, WOPI Host/Integration, Transfer Receiver/Replication) whose non-standard auth made them ineligible for the rounds-1-6 `@PreAuthorize`-pattern backfill.

Original evidence cited `SECURITY_TEST_LEGACY_FILL_ROUND6_AND_THREAD_CLOSEOUT_20260428.md` §4.2.3 — that doc was accurate when written 2026-04-28 but stale by 2026-05-23.

Original "Affected modules" claim that CMIS uses HTTP Basic was the principal error: production `SecurityConfig` has no `.httpBasic(...)` call; the only `httpBasic()` references in the repo are in test-config blocks.

Original three-slice parallel plan (CMIS Basic / WOPI access-token / Transfer per-job bearer) was correspondingly mis-framed and is dropped.

</details>

---

### 2. Sensitive-Data Logging Audit — `Reliability defense + compliance`

**One-line pitch:** Systematic audit of backend `logger.*` call sites for accidental plaintext leaks of OAuth tokens, encryption keys, passwords, and personal data, followed by narrowed remediation slices for any found leaks.

**Evidence of need:**

- `docs/PROPERTY_ENCRYPTION_CLOSEOUT_TODO_20260505.md` §449 lists as acceptance criterion: *"No protected payloads, key material, or admin-operation plaintext values are printed in logs, docs, API responses, or UI diagnostics."* — but the closeout did not include a systematic logger audit.
- `docs/OAUTH_CREDENTIAL_STORE_CLOSEOUT_TODO_20260507.md` similarly required token plaintext be kept out of responses, tests, and docs; logs were not audited.
- Both subsystems went to production in the last ~3 weeks; a single missed `log.info("payload {}", ...)` site would be a data-protection incident.
- `MEMORY.md` does not record a previous audit. This is genuinely unverified territory.

**Affected modules:**

- `ecm-core/src/main/java/.../**/*.java` — every SLF4J `Logger` call site
- High-risk subsystems: `com.ecm.core.integration.oauth.*`, `com.ecm.core.security.property.*`, `com.ecm.core.transfer.*`, `com.ecm.core.integration.mail.*`
- Audit output is a doc, not code

**Estimated work:**

- Audit phase: 1-2 person-days (grep + read + classify per subsystem)
- Remediation phase: scope-unknown until audit finishes — could be zero leaks (best case), or 3-5 narrow slices (1 per subsystem with leaks)
- Total: 1-7 person-days depending on findings

**Risk:**

- Audit phase is read-only, near-zero risk.
- Remediation risk depends on where leaks are: stack-trace masking (low risk), debug-log redaction helpers (low-medium), restructuring exception messages (medium — exception identity must remain testable).
- Reverse risk if audit is skipped: data-protection incident is irreversible and externally visible.

**Verification:**

- Audit doc enumerates every log site in scope, classified as `SAFE` / `LEAKS` / `NEEDS-MASK`. Doc itself is the deliverable for the audit phase.
- Remediation slices each follow the established per-slice verification doc + CI gate pattern.
- A regression mechanism (e.g., a `forbidden-log-pattern` static check or a logback redactor) could be a stretch deliverable but is not required for first acceptance.

**Parallelizable into sub-slices:** **Yes, after audit.** Slice plan:

| Sub-slice | Phase | Independent? |
|---|---|---|
| Slice A | Audit — produce classification doc | Sequential (one pass, one doc) |
| Slice B+ | Remediation per subsystem (OAuth, Encryption, Transfer, Mail, ...) | Parallel; one per identified leak source |

Slice A is the gating prerequisite. If Slice A finds zero leaks, the track closes after Slice A with one doc and the optional regression check as a separate ticket.

**Why now:** Property Encryption + OAuth Credential Store both finished within the last 3 weeks, both designed against the "no plaintext in logs" rule, but neither was independently audited. The combined surface area is now wide enough that the audit pays for itself.

---

### 3. Microsoft OAuth Provider-Side Revoke — `Product capability`

**One-line pitch:** Implement provider-side token revocation for Microsoft Entra ID OAuth accounts in the admin credential store, completing the v1 revoke matrix that already ships for Google and CUSTOM providers.

**Evidence of need:**

- `docs/OAUTH_CREDENTIAL_PROVIDER_REVOKE_MICROSOFT_CUSTOM_DESIGN_FOLLOWUP_20260507.md` documents the constraint (Microsoft has no canonical RFC-7009-shaped endpoint), enumerates two viable mechanisms (session logout vs Graph admin-consent revoke), and defines tenant-templating, failure semantics, and capability-metadata strategy ready to wire.
- `docs/OAUTH_CREDENTIAL_STORE_CLOSEOUT_TODO_20260507.md` §93-95 records Microsoft as deferred pending design closure — design is now closed, so the implementation gate is open.
- Frontend `oauthCredentialAdminService.ts` and `OAuthCredentialAdminPage.tsx` are already capability-flag wired; no frontend changes required.
- Google revoke shipped 2026-05-07; CUSTOM revoke shipped 2026-05-11; Microsoft is the last unsupported provider in v1.

**Affected modules:**

- `ecm-core/src/main/java/com/ecm/core/integration/oauth/OAuthCredentialService.java` — extend the revoke decision tree
- `ecm-core/src/main/java/com/ecm/core/integration/oauth/` — adjacent classes for capability metadata + failure-handling
- `ecm-core/src/test/java/com/ecm/core/integration/oauth/` — unit + integration tests including mocked logout success + mocked provider 5xx
- No `ecm-frontend/` changes — capability flag already controls UI visibility

**Estimated work:** 1-2 person-days. Single backend decision tree + capability flag + test. Google template is the working reference.

**Risk:**

- Moderate design tension: choose logout (cheap, session-level) vs Graph consent revoke (heavy, requires admin scopes Athena does not request). Design doc recommends logout with explicit "honest about limitations" disclosure in UI copy.
- Failure-semantic risk: provider 5xx must preserve local tokens (same as Google v1) so recovery is not lost. Already specified in design.
- Tenant-templating risk: reuse `resolveTokenEndpoint()` logic instead of duplicating tenant-ID handling. Already specified in design.

**Verification:**

- New unit/integration tests cover: logout success path, logout 5xx fallback, capability-flag-controlled visibility, tenant-template resolution.
- CI gate: 7/7 GitHub Actions run as for other slices.
- Manual smoke: admin user with Microsoft account in test tenant triggers revoke from `OAuthCredentialAdminPage`, observes logout success + local token preserved (recoverable).

**Parallelizable into sub-slices:** **No.** Single backend decision, narrow scope, ~1-2 commits. Splitting would add ceremony without benefit.

**Why now:** Last hole in the OAuth credential revoke matrix. Design is closed and waiting. If a Microsoft-tenant customer asks for token revocation, this is now the only blocker.

## Honest mentions (considered, not in Top 3)

### A. Legacy Controller Security Test Backfill (~42 controllers)

- `docs/SECURITY_TEST_LEGACY_FILL_ROUND6_AND_THREAD_CLOSEOUT_20260428.md` §4.2: backfill policy from rounds 1-6 leaves ~42 standard-auth controllers untested.
- Strong pattern reuse, parallelizable by subsystem.
- **Why not Top 3:** generic test-completeness expansion is exactly what the backend closeout §137-147 warns against — *"do not reopen this track just to search for more broad contract work"*. Even though authz tests are a different category from contract tests, the user's stated bias is against burning runway on backlog completion without a specific driver. Recommend folding into "opportunistic — add the test when any future PR touches that controller" rather than a standalone track.

### B. Property Encryption Final Acceptance Matrix

- `docs/PROPERTY_ENCRYPTION_CLOSEOUT_TODO_20260505.md` §433-455: final acceptance requires recording a green Property Encryption Closeout Gate CI run in the matrix.
- The gate has passed green on every backend-contract slice's final CI run for the last 3 weeks. The evidence already exists; only the matrix doc needs updating.
- **Why not Top 3:** ~30-minute clerical task, not a track. Recommend a single doc-only commit folded into any next PR.

### C. ADR-001 Storage Routing / Tenant Isolation Resolution

- `docs/adr/ADR-001-storage-routing-tenant-isolation.md` explicitly deferred per `CLAUDE.md`.
- Resolving this would unlock per-tenant storage isolation (compliance value).
- **Why not Top 3:** the resolution itself is an architectural design pass with a real dedup tradeoff. It is a track candidate but would consume more scoping effort than the 3 above before any code starts. Recommend opening as a separate scoping pass if storage-isolation pressure surfaces from a tenant requirement.

### D. Live Full-Stack Smoke Verification (`CLAUDE.md` "Most Recent Status")

- `CLAUDE.md` notes the current machine could not complete live full-stack smoke because Docker image pulls failed. The Playwright spec exists; only a runner with working image access is needed.
- **Why not Top 3:** environmental dependency on Docker availability rather than engineering work. Recommend running it once on a working CI runner and recording the green spec result; not a track.

### E. Tier 2 Backend Response-Contract Tests

- Explicitly excluded by user brief; mentioned only for completeness. The closeout doc §99-115 calls these out as intentionally out of scope.

## Recommendation framework (updated 2026-05-23)

Effective ranking after #1 was withdrawn:

| Effective rank | Track | When |
|---|---|---|
| **#1 (chosen next track)** | **Sensitive-Data Logging Audit** | Matches the backend-closeout "new operational risk" framing without re-opening a guard/contract surface. Read-only audit phase is cheap (1-2 person-days) and the audit doc is itself the gateable deliverable; remediation slices only fire if Phase 1 finds leaks. Brief: `docs/SENSITIVE_DATA_LOGGING_AUDIT_DESIGN_20260523.md` (to be authored). |
| **#2 (later candidate)** | **Microsoft OAuth Provider-Side Revoke** | Tightest-scope candidate (1-2 person-days). Triggered when a Microsoft Entra ID customer/market signal surfaces. Design already documented in `docs/OAUTH_CREDENTIAL_PROVIDER_REVOKE_MICROSOFT_CUSTOM_DESIGN_FOLLOWUP_20260507.md`. |
| ~~#3~~ | — | Original #1 was withdrawn; no replacement is being promoted out of the Honest Mentions list unless evidence appears that they are real tracks. |

If two tracks run in parallel later, the logging audit (defense) and Microsoft OAuth revoke (product capability) do not share a code surface and parallelize cleanly.

## What this discovery does not commit to

- No track is opened by this document.
- No commits made; only the doc itself sits in working tree.
- The chosen next track (Logging Audit) still requires the gate to confirm the design brief before any audit work begins. The closeout discipline established for the prior two tracks is preserved.

## Local verification

`git diff --check -- . ':!.env'` passed. Working tree carries only the pre-existing `M .env` plus this discovery doc and (when written) the logging-audit design brief.
