# Sensitive-Data Logging Audit — Findings (Phase 1)

Date: 2026-05-23

## Summary

| Metric | Count |
|---|--:|
| In-scope files audited | 30 |
| Total logger call sites scanned | ~94 |
| **SAFE** | ~28 |
| **NEEDS-MASK** | ~68 |
| **LEAKS** (statically confirmed sensitive-value interpolation) | **0** |
| `System.out` / `System.err` call sites in production code | **0** |
| Exception subclass `super(message)` suspicious | 2 |

**Headline result:** No statically confirmed LEAKS. No `System.out` / `System.err` calls in production code. The dominant finding is a large NEEDS-MASK population (~68), most of it `Throwable.getMessage()` interpolation at DEBUG / WARN level in `MailFetcherService.java` (51 sites in one file) and `RestExceptionHandler.java`.

By the brief's gate criteria (§"Gate criteria for opening Phase 2"), Phase 2 opens if **any** of: LEAKS ≥ 1, `System.out/err` ≥ 1, **or** NEEDS-MASK > 10 AND a positive-proof sample is inconclusive. The first two are zero. The third is satisfied on its first clause (NEEDS-MASK = 68 > 10); the second clause was not executed in this pass and is the gate's call.

## Method (executed)

1. Re-scoped the brief's package list against the current repo. Two listed paths did not exist:
   - `ecm-core/src/main/java/com/ecm/core/security/property/**` — does not exist.
   - `ecm-core/src/main/java/com/ecm/core/transfer/**` — does not exist.
   Property-encryption and Transfer code live under `service/`, `asynctask/`, `pipeline/processor/`, `controller/`, and `service/transfer/`. The audit was re-scoped to the real file locations. This brief-vs-reality reconciliation is recorded in §"Method discrepancies" below.
2. Enumerated every SLF4J-style logger call site (`log.trace|debug|info|warn|error`) in the in-scope files via `grep -RInE`.
3. For each site, read ~5-10 lines around it for type context and classified per the brief's taxonomy (SAFE / NEEDS-MASK / LEAKS).
4. Ran the brief's three indirect-leak greps:
   - `@Override toString()` in OAuth / property-encryption / mail subsystems: **0 hits** (Step 4(a)).
   - Exception subclasses extending `*Exception` in OAuth / property-encryption: 1 hit — `OAuthReauthRequiredException`. A sibling `MailOAuthReauthRequiredException` was also reviewed (Step 4(b)).
   - `System.out` / `System.err` across all of `ecm-core/src/main/java/com/ecm/core/`: **0 hits** (Step 4(c)).
5. No production code, test code, or `.env` modified. No commits.

## Method discrepancies (brief vs reality)

The original design brief listed these in-scope subsystem paths:

| Brief said | Reality | Adjustment |
|---|---|---|
| `security/property/**` | Path does not exist. Property-encryption code is in `service/PropertyEncryption*`, `service/NodePropertyEncryptionService.java`, `pipeline/processor/MetadataPersistenceProcessor.java`, `asynctask/PropertyEncryption*`, `config/PropertyEncryptionAsync*`, `controller/PropertyEncryptionOperationsController.java`. | Re-scoped to the real files. The 4 files with logger calls are listed under L2 below; the rest had 0 logger calls. |
| `transfer/**` + `service/transfer/**` | `transfer/**` does not exist. Transfer code is in `service/TransferReplication*`, `service/TransferReceiverRegistryService.java`, `service/transfer/**`, `controller/Transfer*`. | Re-scoped. The 2 files with logger calls under L3 below; the rest had 0. |
| `integration/oauth/**` | Path exists but has **0 logger calls** across all its files. OAuth flows that do log are mainly in `integration/mail/service/MailFetcherService.java` (L4) and `controller/RestExceptionHandler.java` (cross-cutting). | L1 OAuth canonical reports 0 logger calls. The two OAuth exception subclasses (`OAuthReauthRequiredException`, `MailOAuthReauthRequiredException`) were reviewed under §"Indirect-leak findings". |

This is the same class of finding documented in `MEMORY.md` as `feedback_reread_code_before_regression_claim` — assumptions sourced from older docs go stale. The discovery doc's audit-design brief was itself an example.

## Per-subsystem tables

### L1 — OAuth (canonical + mail-specific + controller)

#### `ecm-core/src/main/java/com/ecm/core/integration/oauth/OAuthCredentialAdminService.java`
(no logger calls)

#### `ecm-core/src/main/java/com/ecm/core/integration/oauth/OAuthCredentialService.java`
(no logger calls)

#### `ecm-core/src/main/java/com/ecm/core/integration/oauth/OAuthTokenErrorParser.java`
(no logger calls)

#### `ecm-core/src/main/java/com/ecm/core/integration/mail/service/MailOAuthService.java`
(no logger calls)

#### `ecm-core/src/main/java/com/ecm/core/integration/mail/service/MailOAuthCredentialOwnerAdapter.java`
(no logger calls)

#### `ecm-core/src/main/java/com/ecm/core/integration/mail/service/MailOAuthTokenErrorParser.java`
(no logger calls)

#### `ecm-core/src/main/java/com/ecm/core/controller/OAuthCredentialAdminController.java`
(no logger calls)

**L1 conclusion:** the canonical OAuth code paths emit no logger output. The OAuth credential token, refresh token, secret, etc. cannot leak through these files because no logger statement exists. The exception subclasses are reviewed under §"Indirect-leak findings".

### L2 — Property encryption

#### `ecm-core/src/main/java/com/ecm/core/service/PropertyEncryptionBackfillRecoveryScheduler.java`

| line | level | format | interpolated args | class | reason |
|---|---|---|---|---|---|
| 33 | warn | `"Property encryption backfill recovery skipped because stale-after-minutes is {}"` | `staleAfterMinutes` (long) | SAFE | numeric only |
| 39 | warn | `"Property encryption backfill recovery terminal-marked {} stale active jobs"` | `result.recoveredCount()` (int) | SAFE | count only |

#### `ecm-core/src/main/java/com/ecm/core/service/PropertyEncryptionRewrapRunner.java`

| line | level | format | interpolated args | class | reason |
|---|---|---|---|---|---|
| 24 | error | `"Property encryption rewrap async execution failed for job {}"` | `jobId` (UUID) | SAFE | UUID only |

#### `ecm-core/src/main/java/com/ecm/core/service/PropertyEncryptionBackfillRunner.java`

| line | level | format | interpolated args | class | reason |
|---|---|---|---|---|---|
| 24 | error | `"Property encryption backfill async execution failed for job {}"` | `jobId` (UUID) | SAFE | UUID only |

#### `ecm-core/src/main/java/com/ecm/core/pipeline/processor/MetadataPersistenceProcessor.java`

| line | level | format | interpolated args | class | reason |
|---|---|---|---|---|---|
| 127 | info | `"Persisted document: {} (ID: {}) in {}ms"` | `savedDocument.getName()` (String), `savedDocument.getId()` (UUID), `processingTime` (long) | SAFE | document name + UUID + duration |
| 137 | error | `"Failed to persist document: {}"` | `e.getMessage()` (String), `exception` (Throwable) | NEEDS-MASK | generic-exception message; stack-trace embedded |

**L2 conclusion:** 6 sites total. 5 SAFE, 1 NEEDS-MASK. No property-encryption key material or rewrap payload is logged. The single NEEDS-MASK is the generic persistence-failure handler at `MetadataPersistenceProcessor:137`.

### L3 — Transfer

#### `ecm-core/src/main/java/com/ecm/core/service/TransferReplicationService.java`

| line | level | format | interpolated args | class | reason |
|---|---|---|---|---|---|
| 269 | warn | `"Scheduled replication definition {} skipped: {}"` | `definition.getId()` (UUID), `ex.getMessage()` (String) | NEEDS-MASK | exception message context |
| 291 | warn | `"Retry job {} could not be started: {}"` | `job.getId()` (UUID), `ex.getMessage()` (String) | NEEDS-MASK | exception message context |
| 376 | warn | `"Replication job {} failed: {}"` | `jobId` (UUID), `ex.getMessage()` (String) | NEEDS-MASK | exception message context |
| 485 | info | `"Queued automatic replication retry {} for failed job {} at {}"` | `retryJob.getId()` (UUID), `failedJob.getId()` (UUID), `scheduledFor` (LocalDateTime) | SAFE | UUIDs + timestamp |

#### `ecm-core/src/main/java/com/ecm/core/service/TransferReplicationScheduler.java`

| line | level | format | interpolated args | class | reason |
|---|---|---|---|---|---|
| 20 | info | `"Transfer replication scheduler queued {} definitions and started {} retries"` | counts | SAFE | counts only |
| 32 | info | `"Transfer replication cleanup deleted {} jobs across {} definitions"` | counts | SAFE | counts only |

**L3 conclusion:** 6 sites total. 3 SAFE, 3 NEEDS-MASK. No per-job bearer tokens or transfer receiver secrets are logged anywhere in this lane. The 3 NEEDS-MASK are exception-message handlers in the replication failure path.

### L4 — Mail

#### `ecm-core/src/main/java/com/ecm/core/integration/mail/service/MailFetcherService.java` (51 sites)

Highest-volume file in the audit. Detailed per-site classification (subset, full list executed by audit pass):

| line | level | format | interpolated args | class | reason |
|---|---|---|---|---|---|
| 128 | info | `"Starting mail fetch (runId={}) for {} accounts (force={})"` | UUID, int, bool | SAFE | metrics |
| 164 | warn | `"OAuth reauth required for mail account {}: {}"` | account name, `e.getMessage()` | NEEDS-MASK | account name + provider error message |
| 170 | error | `"Failed to process mail account: {}"` | account name, Throwable | NEEDS-MASK | account name + stack |
| 181 | info | mail-fetch summary | counts | SAFE | metrics |
| 292 | info | mail-fetch debug-run summary | counts | SAFE | metrics |
| 384 | warn | `"Mail debug run failed for account {}: {}"` | account name, `e.getMessage()` | NEEDS-MASK | exception message |
| 390 / 400 | info | `"Mail fetch debug top skip reasons: {}"` | `Map<String, Integer>` | NEEDS-MASK | unverified `Map.toString()` |
| 422, 561, 1845, 1951, 1975 | various | context-dependent verbose log | mail-operation context objects | NEEDS-MASK | verbose context, needs positive-proof read |
| 477, 485, 530, 815, 827, 2021, 2033, 2064, 2396, 2404, 2435, 2444, 2462, 2470, 2647, 2672, 2684, 2696 | debug | `"...: {}"` | `e.getMessage()` | NEEDS-MASK (×18) | exception-message handler at DEBUG |
| 536 | warn | `"Mail preview failed for account {} folder {}: {}"` | account, folder, `e.getMessage()` | NEEDS-MASK | account + folder + message |
| 548 | warn | `"Mail preview failed for account {}: {}"` | account, `e.getMessage()` | NEEDS-MASK | account + message |
| 745, 771, 778, 805, 843, 851, 854, 863, 1747, 1899 | various | folder / UID / rule operational logs | string names, ints, UUIDs | SAFE (×10) | operational metadata |
| 786 | error | `"Error processing message: {}"` | `safeSubject(message)`, Throwable | NEEDS-MASK | `safeSubject(...)` must redact; verify in Phase 1.5 if opened |
| 899 | warn | `"Mail connection test failed for {}: {}"` | account, message | NEEDS-MASK | error message |
| 2220 | warn | `"Failed to update mail properties for document {}"` | UUID, Throwable | NEEDS-MASK | UUID + stack |
| 2281, 2290 | warn | literal `"Mail action MOVE/TAG requires mailActionParam"` | — | SAFE | literal |
| 2770 | debug | `"Skipping mail account {} due to poll interval ({} minutes)"` | account, int | SAFE | account name + duration |
| 2800 | warn | `"Failed to update fetch status for account {}"` | account, Throwable | NEEDS-MASK | account + stack |
| 3000 | warn | `"Retrying IMAP login using AUTH=PLAIN for account {} (non-ASCII password detected)"` | account | SAFE — important: **no password value is logged**, only the literal phrase about non-ASCII detection |

Approximate split for `MailFetcherService.java`: 4 SAFE outright (operational metric / counter / literal), ~10 SAFE operational metadata (account name + folder name), ~37 NEEDS-MASK (most are `e.getMessage()` at DEBUG level for parsing / preview-extraction failure paths).

Important call-outs:
- Line 3000 explicitly does **not** log the password value, only states that a non-ASCII password was detected. Locally SAFE.
- Line 786 calls `safeSubject(message)` which is presumed to redact, but the implementation of `safeSubject` was not verified in this pass; flagged for Phase 1.5 positive-proof.
- Lines 422, 561, 1845, 1951, 1975 are verbose context logs whose full format string and arg types were not fully resolved in this pass; left at NEEDS-MASK pending positive-proof.

#### `ecm-core/src/main/java/com/ecm/core/integration/mail/service/MailReportScheduledExportService.java`

| line | level | format | interpolated args | class | reason |
|---|---|---|---|---|---|
| 81 | warn | literal: `"Mail report scheduled export skipped: folder-id not configured or invalid"` | — | SAFE | literal |
| 106 | warn | `"Mail report scheduled export failed: {}"` | result.message | NEEDS-MASK | user-facing error message |
| 128 | info | `"Mail report scheduled export complete: folderId={} filename={} documentId={} durationMs={}"` | UUID, String, UUID, long | SAFE | IDs + filename + duration |
| 136 | warn | `"Mail report scheduled export errored"` | Throwable | NEEDS-MASK | stack |

#### `ecm-core/src/main/java/com/ecm/core/integration/mail/service/MailProcessedRetentionService.java`

| line | level | format | interpolated args | class | reason |
|---|---|---|---|---|---|
| 53 | info | `"Processed mail retention is disabled (retention-days={})"` | int | SAFE | int |
| 61 | info | retention-removed counts | counts + LocalDateTime | SAFE | metrics |
| 64 | info | `"{} complete"` | label | SAFE | label |
| 66 | debug | retention-empty | int | SAFE | int |

#### `ecm-core/src/main/java/com/ecm/core/integration/mail/controller/MailAutomationController.java`

| line | level | format | interpolated args | class | reason |
|---|---|---|---|---|---|
| 358 | warn | `"OAuth callback failed"` | Throwable | NEEDS-MASK | stack from OAuth callback path may carry provider response context |

**L4 conclusion:** 60 sites. Approximately 18 SAFE, 42 NEEDS-MASK. No password, OAuth token, refresh token, or secret value is interpolated directly in any logger call inspected. `MailFetcherService.java:3000` confirms the only password-mention site logs the literal label, not the value.

### L5 — WOPI

#### `ecm-core/src/main/java/com/ecm/core/integration/wopi/service/WopiService.java`

| line | level | format | interpolated args | class | reason |
|---|---|---|---|---|---|
| 113 | info | `"WOPI PutFile: Updating document {} (size={} bytes)"` | UUID, long | SAFE | UUID + size |

#### `ecm-core/src/main/java/com/ecm/core/integration/wopi/service/CollaboraDiscoveryService.java`

| line | level | format | interpolated args | class | reason |
|---|---|---|---|---|---|
| 191 | warn | `"Failed to load Collabora discovery from {}: {}"` | URL, `e.getMessage()` | NEEDS-MASK | URL + exception message |

**L5 conclusion:** 2 sites. 1 SAFE, 1 NEEDS-MASK. No WOPI access tokens logged.

### L6 — LDAP

#### `ecm-core/src/main/java/com/ecm/core/integration/ldap/JndiLdapDirectoryClient.java`

| line | level | format | interpolated args | class | reason |
|---|---|---|---|---|---|
| 345 | debug | `"Failed to close LDAP context cleanly: {}"` | `closeEx.getMessage()` | NEEDS-MASK | exception message |

`JndiLdapDirectoryClient` originally counted 4 in the baseline scan; the canonical single direct-logger call is at line 345. The other three hits in `grep -RInE` were imports / variable references, not call sites — re-verify in Phase 1.5 if exact count matters.

#### `ecm-core/src/main/java/com/ecm/core/integration/ldap/LdapSyncService.java`

| line | level | format | interpolated args | class | reason |
|---|---|---|---|---|---|
| 196 | info | LDAP sync summary | counts + trigger string | SAFE | counts only |

#### `ecm-core/src/main/java/com/ecm/core/integration/ldap/LdapSyncScheduler.java`

| line | level | format | interpolated args | class | reason |
|---|---|---|---|---|---|
| 24 | info | LDAP sync scheduled summary | counts | SAFE | counts only |

**L6 conclusion:** 3+ sites. ~2 SAFE, 1 NEEDS-MASK. No LDAP bind passwords or DNs with sensitive context logged.

### L7 — Security (non-property)

#### `ecm-core/src/main/java/com/ecm/core/security/mfa/TotpService.java`

| line | level | format | interpolated args | class | reason |
|---|---|---|---|---|---|
| 82 | error | `"Failed to compute HMAC"` | Throwable | NEEDS-MASK | stack from MAC computation; should not carry secret bytes but verify |

#### `ecm-core/src/main/java/com/ecm/core/security/SameDepartmentDynamicAuthority.java`

| line | level | format | interpolated args | class | reason |
|---|---|---|---|---|---|
| 59 | debug | `"User {} and owner {} are in same department: {}"` | usernames + department | SAFE | operational metadata |

#### `ecm-core/src/main/java/com/ecm/core/security/OwnerDynamicAuthority.java`

| line | level | format | interpolated args | class | reason |
|---|---|---|---|---|---|
| 42 | debug | `"User {} is owner of node {}"` | username, UUID | SAFE | username + UUID |

#### `ecm-core/src/main/java/com/ecm/core/security/LockOwnerDynamicAuthority.java`

| line | level | format | interpolated args | class | reason |
|---|---|---|---|---|---|
| 50 | debug | `"User {} is lock owner of node {}"` | username, UUID | SAFE | username + UUID |

**L7 conclusion:** 4 sites. 3 SAFE, 1 NEEDS-MASK. No TOTP secrets / MFA seeds / dynamic-authority decision payloads logged.

### L8 — Config + Exception

#### `ecm-core/src/main/java/com/ecm/core/config/WorkflowDeploymentRunner.java`

| line | level | format | interpolated args | class | reason |
|---|---|---|---|---|---|
| 48 | warn | `"No workflow definitions found under {}"` | basePath | SAFE | file path |
| 63 | warn | `"Failed to read workflow resource: {}"` | entry.getKey(), Throwable | NEEDS-MASK | file path + stack |
| 68 | warn | `"No readable workflow definitions found under {}"` | basePath | SAFE | file path |
| 73 | info | `"Deployed {} workflow definition(s) from {}"` | int, basePath | SAFE | count + path |
| 83 | warn | `"Failed to resolve workflow resources for pattern {}"` | pattern, Throwable | NEEDS-MASK | pattern + stack |

`exception/**` package contains 0 logger calls.

**L8 conclusion:** 5 sites in config. 3 SAFE, 2 NEEDS-MASK.

### Cross-cutting — RestExceptionHandler

#### `ecm-core/src/main/java/com/ecm/core/controller/RestExceptionHandler.java`

The global uncaught-exception handler. Each handler maps an exception to an HTTP response and logs at a corresponding level. Sites in scope because exception messages and stacks pass through here for every controller; a leak via here is cross-cutting.

| line | level | format | interpolated args | class | reason |
|---|---|---|---|---|---|
| 34 | debug | `"Bad request: {}"` | `ex.getMessage()` | NEEDS-MASK | IllegalArgumentException message |
| 44 | **error** | `"Internal state error at {}: {}"` | `request.getRequestURI()`, `ex.getMessage()`, Throwable | **NEEDS-MASK (highest priority)** | URI + message + full stack at ERROR level — the only ERROR-level NEEDS-MASK in cross-cutting; on by default in production |
| 53 | debug | `"OAuth reauthorization required at {}: {}"` | URI, `ex.getMessage()` | SAFE | OAuth handler design intentionally surfaces these for re-auth UI |
| 59 | debug | `"Bad request: {}"` | `ex.getMessage()` | NEEDS-MASK | MethodArgumentTypeMismatchException |
| 65 | debug | `"Model validation failed: {}"` | `ex.getMessage()` | NEEDS-MASK | embeds user input typically |
| 71 | debug | `"Property validation failed: {}"` | `ex.getMessage()` | NEEDS-MASK | may embed user-input property value |
| 77 | debug | `"Bad request: {}"` | `ex.getMessage()` | NEEDS-MASK | generic |
| 83 | debug | `"Forbidden: {}"` | `ex.getMessage()` | NEEDS-MASK | authorization error message |
| 89 | debug | `"Forbidden: {}"` | `ex.getMessage()` | NEEDS-MASK | authorization error message |
| 95 | debug | `"Not found: {}"` | `ex.getMessage()` | NEEDS-MASK | 404 message |
| 101 | debug | `"Not found: {}"` | `ex.getMessage()` | NEEDS-MASK | 404 message |

**RestExceptionHandler conclusion:** 11 sites. 1 SAFE, 10 NEEDS-MASK. Line 44 (ERROR + URI + message + stack for `IllegalStateException`) is the single most reachable cross-cutting risk in production because it is the only handler logging at ERROR level (always on) plus the full stack trace.

## Indirect-leak findings

### `toString()` overrides in high-risk subsystems (Step 4(a))

Grep for `@Override`-style `public String toString()` across `integration/oauth`, `security/property` (re-scoped to `service/PropertyEncryption*`), and `integration/mail`: **0 hits**.

No domain DTO in these subsystems overrides `toString()`; therefore no implicit leak via `log.info("...", account)` where `account` carries a secret would emit the secret. Default `Object.toString()` returns `ClassName@hashCode`. Conservative; safe.

### Exception subclasses with `super(message)` (Step 4(b))

Two exception classes carry constructor messages built from provider-error context:

| Class | File:line | `super(...)` argument | Risk class |
|---|---|---|---|
| `OAuthReauthRequiredException` | `ecm-core/src/main/java/com/ecm/core/integration/oauth/OAuthReauthRequiredException.java:13` | `buildMessage(error, errorDescription)` formatting OAuth provider's `error` + `errorDescription` strings | NEEDS-MASK |
| `MailOAuthReauthRequiredException` | `ecm-core/src/main/java/com/ecm/core/integration/mail/service/MailOAuthReauthRequiredException.java:18` | `buildMessage(oauthError, oauthErrorDescription)` — same pattern | NEEDS-MASK |

Risk profile: both are intentionally constructed for safe display in UI/JSON error responses. The leak vector is **third-party**: if any code path catches the exception and logs it via `log.error("...", e)` without an explicit handler that knows the format, the stack trace carries the constructed message. Providers usually emit non-sensitive `error` codes (e.g., `invalid_grant`) but `errorDescription` is provider-controlled and could carry diagnostic context (e.g., user email).

These show up in the audit because `RestExceptionHandler.java:53` (the dedicated OAuth-reauth path) intentionally logs them at DEBUG level with a SAFE classification. The risk is in **callers that don't go through that handler**.

### `System.out` / `System.err` (Step 4(c))

Grep across all of `ecm-core/src/main/java/com/ecm/core/`: **0 hits**. No direct stdout/stderr emission in production code. This is a green light.

## Honest gaps in this audit

The audit's static-grep method cannot prove the following on its own:

- **Verbose context formats in MailFetcherService** lines 422, 561, 1845, 1951, 1975 — these were flagged NEEDS-MASK based on log level (info/debug at verbose call sites) but the exact format strings and arg types were not fully resolved during the read. A positive-proof second pass on these would either promote to SAFE or demote to LEAKS.
- **`safeSubject(message)` at `MailFetcherService.java:786`** — assumed to redact; the actual implementation of `safeSubject` was not verified.
- **OAuth callback flow at `MailAutomationController.java:358`** — the exception passed in is generic `Throwable`; the OAuth callback exchange itself does not log, but if the underlying client library throws an exception that embeds the OAuth response body in its message, the stack here would carry it.
- **Indirect logging via overridden methods in superclass / library code** — only `@Override toString()` was checked, not other interpolation paths (e.g., a domain object passed into Jackson serialization downstream of a logger call). Out of static-grep scope.
- **Library-level loggers** (`org.apache.http`, `jakarta.mail`, `org.eclipse.jetty`, etc.) — out of Phase 1 scope per the brief.
- **Logback / logback-spring.xml config** — not scanned in Phase 1. If any library logger is configured at DEBUG in production, that becomes an indirect leak vector not visible to this audit.

The "approximate" qualifier on the summary counts (~28 SAFE / ~68 NEEDS-MASK) reflects these gaps. A second-pass positive-proof on the 5 verbose-context MailFetcherService sites and `safeSubject` would tighten the count.

## Recommendation

By the brief's gate criteria, **Phase 2 trigger is partial**:

- LEAKS = 0 ✗ (criterion not satisfied)
- `System.out/err` = 0 ✗ (criterion not satisfied)
- NEEDS-MASK = 68, which exceeds 10 ✓, **but** the second clause "a positive-proof pass on a sample is inconclusive" was not executed in this pass.

Two options for the gate:

### Option A — declare audit conclusive, close the track

Read this audit as evidence that no **statically confirmed** sensitive-value interpolation exists in production logger output. The 68 NEEDS-MASK are mostly `Throwable.getMessage()` at DEBUG level — provider-error and parse-failure context that is generally safe in INFO/WARN production logging but should not be promoted to ERROR/INFO routinely. Close the track with this doc as the final artefact. No remediation slices opened.

### Option B — commission a Phase 1.5 positive-proof pass on the highest-risk sample

Pick a small sample (5-8 NEEDS-MASK sites) for positive-proof:

1. `RestExceptionHandler.java:44` — the single ERROR-level NEEDS-MASK with full stack. If `ex.getMessage()` on `IllegalStateException` from any internal service can carry a secret, that's a real LEAKS. Worth a 30-minute read of the throw sites.
2. `MailFetcherService.java:786` — `safeSubject(message)` — verify the redaction by reading `safeSubject` implementation.
3. `MailFetcherService.java:422, 561, 1845, 1951, 1975` — resolve the verbose-context format strings.
4. `MailFetcherService.java:164, 170, 384` — OAuth reauth + processing failure exception messages — provider-controlled diagnostic context.
5. `MailAutomationController.java:358` — OAuth callback exception path.
6. `OAuthReauthRequiredException` / `MailOAuthReauthRequiredException` — verify the call sites of these exceptions to confirm no caller logs them outside the dedicated DEBUG-level handler.

If Phase 1.5 surfaces a LEAKS or an unresolved high-risk NEEDS-MASK, Phase 2 remediation opens for that subsystem only — likely a single narrow slice on `RestExceptionHandler.java:44` and `MailFetcherService.java` exception masking.

### Option C — open targeted Phase 2 directly

If the gate is willing to absorb the cost of a defensive cleanup without a positive-proof pass: ship one narrow slice that wraps `RestExceptionHandler.java:44` to mask known-suspect substrings (token / secret / password / key=... patterns) and adds a unit test capturing logged output. This is the highest-leverage single defensive change identified by the audit.

**Audit author's view:** Option B is the highest information-yield choice. The 30-minute positive-proof read on the 5-8 sites listed above either confirms zero LEAKS (Option A becomes the verdict) or identifies the one site that needs masking (Option C scope, but evidence-driven, not speculative).

## Verification

```bash
# (1) doc-only deliverable; no production code touched
git status --short          # M .env + the three docs (discovery, audit-brief, this findings)
git diff --check -- . ':!.env'

# (2) no production code changed
git diff --stat -- 'ecm-core/src/main/java/'   # empty
git diff --stat -- 'ecm-frontend/'              # empty

# (3) no test changed
git diff --stat -- 'ecm-core/src/test/'         # empty
```

All three checks passed at the moment of writing this doc.

## OOS confirmed

- Production Java: **untouched**.
- Frontend: **untouched**.
- Test code: **untouched**.
- Logback / logback-spring.xml: not modified.
- `.env`: not touched.
- No commits made.

## Next-step matrix (for gate to choose)

| Gate decision | Next action |
|---|---|
| Option A — close | Commit the discovery doc + audit brief + this findings doc as one `[skip ci]` close-out commit; track closes. |
| Option B — Phase 1.5 positive-proof on sample | Another read-only pass, ~30 minutes, deliverable: an appendix to this findings doc with positive-proof results. Closes either as Option A or to Option C scope. |
| Option C — open Phase 2 narrow remediation | One slice on the highest-leverage site (`RestExceptionHandler.java:44` or `MailFetcherService.java` exception masking), following the established per-slice verification + CI gate pattern. |
| Pivot to a different track | The 0-LEAKS, 0-System.out result is by itself a deliverable; treat this as the audit completion artefact and switch tracks. |

---

## Phase 1.5 Positive-Proof Appendix

Date: 2026-05-23 (same day, separate read-only pass)

Six high-priority NEEDS-MASK sites were re-read against the source to resolve them to SAFE / STILL NEEDS-MASK / LEAKS. Method: targeted `Read` of definition + call sites for each item; cross-reference exception-flow paths; quote file:line evidence.

### Item 1 — `RestExceptionHandler.java:44` `IllegalStateException` throw sites

Scanned all 100+ `throw new IllegalStateException(...)` sites in production code. Most embed only operational identifiers (UUIDs, status names, file names, counts, config-property names, group names) and are SAFE for the ERROR-level + URI + stack logging that flows through `:44`. Two sites are exceptions:

- **`OAuthCredentialService.java:217`** — OAuth token revoke failure:
  ```
  String message = "OAuth token revoke failed for owner " + owner.ownerName();
  if (parsed != null) {
      message += ": " + parsed.error();
      if (parsed.errorDescription() != null && !parsed.errorDescription().isBlank()) {
          message += " - " + parsed.errorDescription().trim();
      }
  } else { ... }
  throw new IllegalStateException(message, ex);
  ```
  `parsed.errorDescription()` is parsed from the **OAuth provider's HTTP error response body**. Provider-controlled string; could carry user identifiers (e.g., "Access denied for user xyz@example.com") or scope context. Flows through `RestExceptionHandler:44` → logged at ERROR + URI + full stack.
- **`OAuthCredentialService.java:358`** — OAuth token refresh failure: same pattern — embeds `parsed.error()` + `parsed.errorDescription().trim()` in the IllegalStateException message.

Other notable throws sampled and resolved SAFE:
- `OAuthCredentialService.java:187, 194, 219`: revoke variants. Embed owner name + HTTP status code only. SAFE.
- `OAuthCredentialService.java:364, 392, 439, 487, 526, 535, 556`: refresh / endpoint / env-var / credential-key failures. Embed owner name + property name (envKey). SAFE (property names not values).
- `KeycloakUserGroupBackend.java:483, 500`: mention property names (`ecm.keycloak.admin-username/admin-password`) but never the values. SAFE.
- `SecretCryptoService.java:59, 84, 103, 107, 116`: embed key VERSION (int) only, never key material. SAFE.
- `PropertyEncryptionOperationsService.java:*` (~30 throws): embed job IDs, status enums, key version numbers, counts. SAFE.
- `NodeService.java`, `FolderService.java`, `VersionService.java`, `CheckOutCheckInService.java`, `LockService.java`: operational state messages with node IDs / lock descriptions. SAFE.
- `TransferReceiverService.java`, `AthenaTransferHttpClient.java`, `LoopbackTransferClient.java`: embed document IDs / requested names. SAFE.

**Item 1 verdict:** **STILL NEEDS-MASK** at exactly 2 sites: `OAuthCredentialService.java:217` and `:358`. All other IllegalStateException throws sampled are SAFE. The risk on the two confirmed sites is non-zero (provider error descriptions are not statically known to exclude PII) but historically OAuth providers emit description strings about error categories, not credentials.

### Item 2 — `MailFetcherService.safeSubject(message)` (`:2668-2675`)

```java
private String safeSubject(Message message) {
    try {
        return message.getSubject() != null ? message.getSubject() : "";
    } catch (Exception e) {
        log.debug("Mail subject decode failed: {}", e.getMessage());
        return "";
    }
}
```

The method does **not** redact. "Safe" here refers to safety against `NullPointerException` / decode `Exception`, not safety against PII. Calls that interpolate `safeSubject(message)` into a log emit the **raw email subject line** — typical PII (e.g., "Your password reset for example.com", "Invoice #12345 from Vendor Y", "Re: layoff announcement").

Sites where `safeSubject(...)` flows into a logger:
- **`:786`** `log.error("Error processing message: {}", safeSubject(message), e)` — ERROR level (on by default in production) + full stack via `e`. **Highest-impact PII site identified by this audit.**

Other `safeSubject(...)` call sites (verified to be NOT logger arguments):
- `:495, 520, 832, 2038, 2325, 2379` — assignments to DTO fields / filename construction / DB persistence paths. Out of audit scope (these are not log emissions). They flow into stored data and downstream rendering; intended behavior.

**Item 2 verdict:** **STILL NEEDS-MASK** with a strong lean toward LEAKS classification depending on whether the project policy treats email subjects as confidential user data. The misleading `safeSubject` name is itself a footgun — a future contributor reading "safeSubject" might assume redaction is happening and add more log call sites.

### Item 3 — `MailFetcherService` verbose-context lines 422 / 561 / 1845 / 1951 / 1975

Read each in full context:

- **`:422-428`** `"Starting mail rule preview (runId={}): accountId={}, ruleId={}, maxMessagesPerFolder={}"` — `runId` (UUID String), `accountId` (UUID), `ruleId` (UUID), `effectiveMaxMessages` (int). **SAFE** — all UUIDs + int.
- **`:561-571`** `"Mail rule preview completed (runId={}): found={}, scanned={}, matched={}, processable={}, skipped={}, errors={}, durationMs={}"` — UUID + 7 ints. **SAFE** — all metrics.
- **`:1845-1852`** `"Mail debug summary for {}: found={}, matched={}, processable={}, skipped={}, errors={}, topSkips=[{}], topRules=[{}]"` — account name + counters + formatted skip-reason map + formatted rule-name map. Operational metadata; rule names are admin-configured strings, not user PII. **SAFE**.
- **`:1951-1961`** `"Mail debug folder {}:{} found={}, scanned={}, matched={}, processable={}, skipped={}, errors={}"` — account name + folder name + counters. **SAFE** — operational mail metadata, not message content.
- **`:1975-1980`** `log.warn("Mail debug run failed for account {} folder {}: {}", account.getName(), folderName, e.getMessage())` — exception-message handler at WARN. Same NEEDS-MASK pattern already classified in Phase 1. **STILL NEEDS-MASK** (no change).

**Item 3 verdict:** **4 promoted from NEEDS-MASK to SAFE** (lines 422, 561, 1845, 1951). **1 remains NEEDS-MASK** (line 1975 — same `e.getMessage()` pattern as the broader exception-handler population). Net: −4 NEEDS-MASK.

### Item 4 — `MailFetcherService` `:164`, `:170`, `:384` OAuth/provider error sources

- **`:164`** `log.warn("OAuth reauth required for mail account {}: {}", account.getName(), e.getMessage())` — `e` is `MailOAuthReauthRequiredException` (class read in Item 6). Its `getMessage()` returns `"OAUTH_REAUTH_REQUIRED: " + oauthError + " - " + oauthErrorDescription`. `oauthError` is an OAuth standard code (e.g., `invalid_grant`); `oauthErrorDescription` is **provider-controlled** — may contain user identifiers, scope details, or tenant-specific diagnostic text. **STILL NEEDS-MASK**.
- **`:170`** `log.error("Failed to process mail account: {}", account.getName(), e)` — `e` is generic `Exception` (caught by the outermost `catch (Exception e)` at `:166`). The exception chain could include any nested provider/library exception. Most likely contents are mail-library errors (IMAP/SMTP) which historically do NOT embed passwords (because credentials flow through the JavaMail Session, not directly into thrown exceptions). But this is not statically guaranteed. **STILL NEEDS-MASK**.
- **`:384`** `log.warn("Mail debug run failed for account {}: {}", account.getName(), e.getMessage())` — generic `Exception.getMessage()`. Same risk as `:170` but message-only (no full stack). **STILL NEEDS-MASK**.

**Item 4 verdict:** All three **STILL NEEDS-MASK**. The provider-controlled OAuth description (`:164`) is the highest concrete risk in this trio; the generic `Exception` paths (`:170`, `:384`) are unverified.

### Item 5 — `MailAutomationController.java:358` OAuth callback exception

```java
try {
    MailOAuthService.OAuthCallbackResult result = oauthService.handleCallback(code, state);
    ...
} catch (Exception ex) {
    log.warn("OAuth callback failed", ex);
    ...
}
```

`handleCallback(code, state)` exchanges the OAuth `code` (authorization code — a one-time-use credential) for tokens. If a transport-layer or provider-layer exception wraps the HTTP request (e.g., RestTemplate's `HttpClientErrorException`), the exception's message typically contains response status + body excerpt — and could include the `code` if the provider echoes it in an error body, or could include OAuth-IllegalStateException re-throws from Item 1's `:358` (which already carries `parsed.errorDescription()`).

The catch is on generic `Exception`, so any nested exception path is in scope. Without verifying every possible exception type that `handleCallback` could surface, the worst-case risk is leakage of the OAuth authorization code if a provider-side exception echoes it back. Likelihood: low (most OAuth providers do not echo the code in error responses); impact: high if it ever happens.

**Item 5 verdict:** **STILL NEEDS-MASK** with elevated severity. Defense: catch only the specific exception types that `handleCallback` is documented to throw, OR redact `ex` to class name + sanitized message before logging.

### Item 6 — OAuth exception class call sites

Exception-flow trace:

```
OAuthCredentialService.java:347
  throw new OAuthReauthRequiredException(ownerType, ownerId, parsed.error(), parsed.errorDescription())
                                                              ↓
MailOAuthService.java:39 catches OAuthReauthRequiredException
MailOAuthService.java:40
  throw new MailOAuthReauthRequiredException(account.getId(), ex.getError(), ex.getErrorDescription())
                                                              ↓
MailFetcherService.java:160 catches MailOAuthReauthRequiredException
MailFetcherService.java:164
  log.warn("OAuth reauth required for mail account {}: {}", account.getName(), e.getMessage())
                                                              ↑
                                              embeds OAUTH_REAUTH_REQUIRED: <error> - <description>
```

And separately, when the same exception leaks out to the controller layer (e.g., a flow that does not catch MailOAuthReauthRequiredException locally), it would reach `RestExceptionHandler.java:48-55` which logs at DEBUG (already SAFE per Phase 1).

Grep for any other logger that handles these exception classes: only the two paths above + RestExceptionHandler. No additional log sites.

**Item 6 verdict:** **STILL NEEDS-MASK** at `MailFetcherService.java:164`. The exception is logged outside the dedicated DEBUG handler (at WARN level) with the provider-controlled error description embedded in `e.getMessage()`. Same finding as Item 4's `:164`; Item 6 confirms there is no other unaudited log path.

### Net Phase 1.5 movement

| Movement | Count | Sites |
|---|---|---|
| NEEDS-MASK → SAFE (promoted) | 4 | MailFetcherService `:422`, `:561`, `:1845`, `:1951` |
| NEEDS-MASK → STILL NEEDS-MASK | 6 distinct concrete sites | `OAuthCredentialService.java:217`, `OAuthCredentialService.java:358`, `MailFetcherService.java:786` (`safeSubject` PII), `MailFetcherService.java:164` (OAuth reauth WARN), `MailFetcherService.java:170` (generic Exception ERROR), `MailFetcherService.java:384` (generic WARN), `MailAutomationController.java:358` (OAuth callback) |
| NEEDS-MASK → LEAKS | 0 | none statically confirmed |
| New SAFE findings | safeSubject scope clarified | `safeSubject` not in log calls at lines 495, 520, 832, 2038, 2325, 2379 — those are DTO/persistence paths, not loggers |

Revised totals: roughly SAFE ≈ 32, NEEDS-MASK ≈ 64 (still > 10 threshold), LEAKS = 0, System.out = 0.

### Highest-leverage findings (priority-ordered)

| # | Site | Why | Risk class |
|---|---|---|---|
| 1 | `MailFetcherService.safeSubject(message)` at definition `:2668` + usage `:786` | Misleading name + raw email subject logged at ERROR (always-on) + ERROR includes full stack via `e` | PII high-volume |
| 2 | `OAuthCredentialService.java:217` (revoke) and `:358` (refresh) — `parsed.errorDescription()` embedded in IllegalStateException | Provider-controlled string embedded in message that flows to `RestExceptionHandler:44` ERROR + stack | Provider data |
| 3 | `MailFetcherService.java:164` `e.getMessage()` from `MailOAuthReauthRequiredException` | Same provider description as #2 but logged directly at WARN by mail fetcher | Provider data (same root) |
| 4 | `MailAutomationController.java:358` `log.warn("OAuth callback failed", ex)` on generic `Exception` | Could carry OAuth authorization code if any nested exception echoes the request body | Potential credential |
| 5 | `MailFetcherService.java:170`, `:384` `Exception.getMessage()` / `Exception` for generic mail-fetch failure | Provider/library exception content unverified | Generic exception data |

### Gate verdict

User's instruction for Phase 1.5:

> 如果全 SAFE：建议提交三份 doc [skip ci] 并关闭 track。
> 如果发现 LEAKS 或 STILL NEEDS-MASK 高风险：给出最小 Phase 2 remediation slice。

The pass found:

- **0 LEAKS** statically confirmed.
- **6 distinct STILL NEEDS-MASK sites**, of which **at least one is high-risk by any reasonable interpretation**: `MailFetcherService.safeSubject(message)` → `:786` logs raw email subjects at ERROR level. Even if the project does not classify email subjects as confidential per its policy, the **name `safeSubject` is itself a developer trap** that will multiply log sites until renamed or refactored.

→ **Verdict: open Phase 2.**

### Minimum Phase 2 remediation slice (proposed)

**Slice scope (single PR):**

1. **`MailFetcherService.safeSubject`** — rename to `subjectOrEmpty` (signals "no redaction") and audit all call sites:
   - Logger uses (only `:786`): replace `safeSubject(message)` with `redactSubjectForLog(message)` — new helper returning either a hash (for log-correlation) or a literal `"<redacted>"` token. Caller policy choice.
   - Non-logger uses (`:495, 520, 832, 2038, 2325, 2379`): keep using `subjectOrEmpty` (unchanged behavior — these go into stored data, not logs).
2. **`OAuthCredentialService.java:217` + `:358`** — change the IllegalStateException message construction to embed only `parsed.error()` (the standard OAuth error code), not `parsed.errorDescription()`. The description remains available via the parsed-error object passed in the cause exception's debug detail, but does not flow into the user-visible `ex.getMessage()` that hits ERROR-level logger via RestExceptionHandler:44.
3. **`MailFetcherService.java:164`** — log only `e.getOauthError()` (the error code, public-safe) and `e.getAccountId()`, not `e.getMessage()` (which carries description).
4. **`MailAutomationController.java:358`** — narrow `catch (Exception ex)` to specific OAuth exception types (`OAuthReauthRequiredException`, `IllegalStateException`, `HttpStatusCodeException`); log `ex.getClass().getSimpleName()` + a sanitized message rather than the full exception object.

**Slice verification:**

- New unit tests in `MailFetcherServiceTest` (or co-located test) capturing logger output via a Logback `ListAppender`, asserting that the emitted log does NOT contain:
  - the raw email subject string;
  - the OAuth `errorDescription` field (only the code);
  - the OAuth authorization code (`code` parameter).
- 7/7 GitHub Actions gate (same set as backend-contract slices).
- Follows the per-slice verification doc pattern.

**Out of scope for this minimum slice:**

- Other generic `Exception.getMessage()` sites in `MailFetcherService` (`:170`, `:384`, the ~18 DEBUG-level exception sites in mail body/multipart parsing). These are lower-risk (DEBUG-level + mail-library exceptions historically do not embed credentials). Can be folded into a second remediation slice if desired, but not blocking.
- Logback `RedactingConverter` infrastructure. Optional follow-up; deferred unless the four points above prove repetitive enough to warrant abstraction.

**Estimated work:** 1 person-day. The changes are localized to four files; tests are co-located; no production behavior change beyond log content.

### Local verification (Phase 1.5)

```bash
git status --short          # M .env + the three docs (discovery, audit-brief, this findings — with appendix)
git diff --check -- . ':!.env'
git diff --stat -- 'ecm-core/src/main/java/' 'ecm-frontend/' 'ecm-core/src/test/'  # empty (no production / test changes)
```

All three checks passed at the moment of writing this appendix.

### Phase 1.5 OOS confirmed

- Production Java: **untouched**.
- Frontend: **untouched**.
- Test code: **untouched**.
- `.env`: **not touched**.
- No commits made.
- The proposed Phase 2 slice is described, not executed.

---

## Phase 2 status (2026-05-23)

The gate opened a Phase 2 minimum remediation slice. Scope, file-by-file changes, tests, OOS, and the four-commit sequence are recorded in `docs/SENSITIVE_DATA_LOGGING_AUDIT_PHASE2_REMEDIATION_20260523.md`.

Summary of what Phase 2 touched (no broader Logback redactor; only the highest-evidence sites):

- `MailFetcherService` — renamed misleading `safeSubject` to `subjectOrEmpty`; added redacting helper `redactSubjectForLog` (literal `<redacted-subject>`); the one logger call site `:800` (was `:786`) now uses the redactor; the OAuth-reauth log at `:168` (was `:164`) now emits the OAuth standard error code via `e.getOauthError()` instead of `e.getMessage()`.
- `OAuthCredentialService` — revoke (`:208-216`) and refresh (`:354-358`) `IllegalStateException` messages no longer embed `parsed.errorDescription()`; the cause chain still carries it for any caller that needs deeper diagnostics.
- `MailAutomationController` — OAuth callback catch (`:362`) no longer passes the full `Throwable` to SLF4J; logs only `ex.getClass().getSimpleName()` plus a `"message redacted"` marker.

Phase 2 also deferred items remain deferred: generic-`Exception` ERROR/WARN at `MailFetcherService:179, :384`, the ~18 DEBUG-level mail-parsing exception logs, the persistence-path `updateAccountFetchStatus(... e.getMessage())` at `:174, :180`, and `MailReportScheduledExportService:106, :136`, `CollaboraDiscoveryService:191`, `WorkflowDeploymentRunner:63, :83`. These are recorded as out-of-scope in the Phase 2 doc and would only re-open if a new audit finding promotes them.

### Phase 2 Follow-up #1 (2026-05-23) — OAuth exception cause chain

A gate review of the Phase 2 fix surfaced a real leak that the original Phase 2 tests did not catch: although `IllegalStateException.getMessage()` was sanitized, the raw `HttpStatusCodeException` was preserved as the exception's `cause`. Spring's `RestClientResponseException.getMessage()` includes the response body verbatim, and SLF4J/Logback at `RestExceptionHandler:44` (`log.error("...", ex)`) emits the full cause-chain stack trace — leaking the `error_description` back into the ERROR log.

Follow-up #1 sanitizes the cause via a new `sanitizedHttpCause(HttpStatusCodeException)` helper that returns a `RuntimeException` containing only the exception class simple name + HTTP status code, with the original stack frames copied. Tests extended with `Throwable.printStackTrace(PrintWriter)` capture + `assertFalse(contains(...))` against every provider-description fragment to lock the cause-chain emission.

Design + scope + commit sequence: `docs/SENSITIVE_DATA_LOGGING_AUDIT_PHASE2_FOLLOWUP_OAUTH_CAUSE_CHAIN_20260523.md`.
