# Sensitive-Data Logging Audit — Track Design (Phase 1 = Read-Only Audit)

Date: 2026-05-23

## Context

The frontend service guard and backend response-contract tracks both closed in May 2026. `docs/NEXT_TRACK_DISCOVERY_20260523.md` re-verified its initial Top 3 and pivoted to the Sensitive-Data Logging Audit as the chosen next track, on the operational-risk axis ("new risk surface", not "more tests for completeness").

Driving observations:

- `docs/PROPERTY_ENCRYPTION_CLOSEOUT_TODO_20260505.md` §449 explicitly demands *"No protected payloads, key material, or admin-operation plaintext values are printed in logs, docs, API responses, or UI diagnostics"* — but the closeout did not include a systematic logger-call-site audit.
- `docs/OAUTH_CREDENTIAL_STORE_CLOSEOUT_TODO_20260507.md` required token plaintext be kept out of responses, tests, and docs; logs were similarly not audited.
- Both subsystems are now in production; a single missed `log.info("payload {}", request)` would be a data-protection incident with external visibility and irreversibility.
- `MEMORY.md` records no prior logger audit; this is genuinely unverified territory.

This brief covers **Phase 1 only — read-only audit with a single doc deliverable**. Phase 2 (remediation) is conditional on Phase 1 finding leaks and is scoped separately.

## Scope

### In scope (Phase 1)

Every `Logger`/`log` call site in production Java code under:

- `ecm-core/src/main/java/com/ecm/core/integration/oauth/**`
- `ecm-core/src/main/java/com/ecm/core/security/property/**`
- `ecm-core/src/main/java/com/ecm/core/security/**` (everything else under security)
- `ecm-core/src/main/java/com/ecm/core/transfer/**` and `ecm-core/src/main/java/com/ecm/core/service/transfer/**`
- `ecm-core/src/main/java/com/ecm/core/integration/mail/**`
- `ecm-core/src/main/java/com/ecm/core/integration/wopi/**`
- `ecm-core/src/main/java/com/ecm/core/integration/ldap/**`
- Cross-cutting: `ecm-core/src/main/java/com/ecm/core/config/**` (Spring config, filters, exception handlers), `ecm-core/src/main/java/com/ecm/core/exception/**`

Classification levels (defined fully in §Classification taxonomy below):

- **SAFE** — interpolated values are IDs, counts, non-sensitive names, or string constants.
- **NEEDS-MASK** — interpolated values *could* be sensitive depending on call context, or use `Object.toString()` of a domain object whose `toString()` is not audited; the call needs a positive proof that it is safe, or a defensive mask.
- **LEAKS** — call site is statically confirmed to interpolate a sensitive value (token, secret, password, key material, full request payload, raw exception with secret in message).

### Out of scope (Phase 1)

- `ecm-core/src/test/**` — test code is not a production leak path.
- `ecm-frontend/**` — out of scope; frontend logging (`console.*`) does not flow to a backend log sink and any sensitive value visible client-side is already exposed.
- Library / framework log output (`org.springframework.*`, `org.hibernate.*`, `io.netty.*`, etc.) — out of audit scope; if remediation needs to silence a library logger, that becomes Phase 2 scope.
- `application*.yml` / Logback config — out of audit scope unless Phase 1 finds a leak whose remediation requires a Logback redactor.
- Documentation, comments, JavaDoc — only the actual log call sites count.
- Any production code change. **This brief is read-only; produce a doc.**
- `.env` — never touched.

## Method

### Step 1: enumerate log call sites

For each subsystem bucket, grep the production Java tree for SLF4J-style log calls:

```bash
# baseline: count call sites per subsystem
for d in oauth security/property security transfer/main service/transfer mail wopi ldap config exception; do
  echo "=== $d ==="
  grep -RInE '\b(log|logger|LOG|LOGGER)\.(trace|debug|info|warn|error)\b' \
    "ecm-core/src/main/java/com/ecm/core/${d}" 2>/dev/null | wc -l
done
```

If any bucket exceeds ~80 call sites, fan out per-subsystem to parallel sub-briefs (each becomes its own audit fragment doc) rather than doing all in one pass. Otherwise a single audit doc is fine.

### Step 2: per-site classification

For every call site, read the surrounding 10 lines to understand:

1. What level is it? (trace/debug/info/warn/error)
2. What values are interpolated into the format string?
3. Is any interpolated value typed as `String accessToken`, `String secret`, `String password`, `String apiKey`, `String credential`, `byte[]`, `char[]`, `OAuth*`, `Encryption*`, a request DTO, an exception with potentially sensitive `getMessage()`, etc.?
4. Does the format string contain literal text like `payload`, `request`, `body`, `token`, `secret`, `password`, `credential`, `key` (case-insensitive) that *suggests* a sensitive value is being logged?

Classify per the taxonomy below.

### Step 3: positive proof for NEEDS-MASK

For NEEDS-MASK sites, do not assume "looks fine". Either:

- Read the type of every interpolated argument and confirm SAFE — promote to SAFE.
- Confirm a sensitive value flows in — demote to LEAKS.

If neither can be confirmed in <5 minutes of source-reading, leave at NEEDS-MASK with a `reason: needs Phase 2 owner review` note.

### Step 4: search for indirect leak vectors

In addition to direct logger calls, grep for these patterns within the in-scope subsystems:

```bash
# (a) toString() on domain DTOs that might carry secrets — only the @Override toString() implementations
grep -RInE '\b(public|protected)[[:space:]]+String[[:space:]]+toString[[:space:]]*\([[:space:]]*\)' \
  ecm-core/src/main/java/com/ecm/core/integration/oauth \
  ecm-core/src/main/java/com/ecm/core/security/property \
  ecm-core/src/main/java/com/ecm/core/integration/mail

# (b) exception subclasses that pass user-controlled data into super(message) — could leak when stack-traced
grep -RInE 'extends[[:space:]]+[[:alnum:]_]*Exception' \
  ecm-core/src/main/java/com/ecm/core/integration/oauth \
  ecm-core/src/main/java/com/ecm/core/security/property

# (c) System.out / System.err — must be empty in production code
grep -RInE 'System\.(out|err)\.print' \
  ecm-core/src/main/java/com/ecm/core/
```

Record any non-empty result; (c) is automatically a LEAKS finding.

## Classification taxonomy

| Class | Definition | Example |
|---|---|---|
| **SAFE** | Interpolated values are: UUIDs, integer counts, enum names, hard-coded literals, request IDs, time values, status names, file paths that do not embed user-private content, exception class names without message, boolean flags. Format string contains no suspect keyword. | `log.info("OAuth revoke succeeded for accountId={}", accountId)` |
| **NEEDS-MASK** | At least one interpolated value cannot be statically confirmed safe (`Object`, generic DTO, exception `getMessage()`, byte[]); or format string contains a suspect keyword (`token`/`secret`/`password`/`payload`/`body`/`request`/`response`/`credential`/`key`); or call is at TRACE/DEBUG level which historically logs verbose context. | `log.debug("Refreshing token: {}", account)` (account.toString() unaudited) |
| **LEAKS** | Statically confirmed to interpolate a sensitive value: a String typed/named `accessToken`/`refreshToken`/`secret`/`password`/`apiKey`/`encryptionKey`; or `request.body()` / `responseEntity.getBody()` of a DTO that carries a secret; or `System.out.print*` of any value originating from a secret-carrying type. | `log.info("Saving credential with secret={}", credential.getSecret())` |

A LEAKS finding triggers Phase 2 remediation. A NEEDS-MASK finding triggers either a positive-proof read or a defensive-mask retrofit.

Severity priority for remediation if LEAKS appear:

1. INFO/WARN/ERROR-level leaks of token/secret/password — top priority, on by default in production.
2. DEBUG/TRACE leaks of token/secret/password — high priority because debug is often switched on under incident response.
3. Stack-trace-borne leaks (exception message embedding a secret) — high priority because frameworks log uncaught exceptions automatically.
4. NEEDS-MASK that does not resolve to either SAFE or LEAKS after the positive-proof pass — medium priority; defensive mask without disturbing log semantics.

## Output format — Phase 1 deliverable

Produce **one Markdown doc**: `docs/SENSITIVE_DATA_LOGGING_AUDIT_FINDINGS_<date>.md`.

Structure:

```markdown
# Sensitive-Data Logging Audit — Findings

## Summary

- Subsystems audited: N
- Total log sites scanned: M
- SAFE: x
- NEEDS-MASK: y
- LEAKS: z
- System.out / System.err call sites: k (should be 0)

If z = 0 and k = 0, the audit closes the track with no remediation needed.

## Per-subsystem tables

### oauth (path)

| File:line | Level | Format | Interpolated args | Class | Notes |
|---|---|---|---|---|---|
| OAuthCredentialService.java:142 | info | "OAuth revoke succeeded for {}" | accountId (UUID) | SAFE | id-only |
| OAuthCredentialService.java:198 | debug | "Refreshing token for {}" | account (MailAccount) | NEEDS-MASK | unverified toString |
| ... |

(repeat per subsystem)

## Indirect-leak findings

- toString() implementations reviewed: ...
- Exception subclasses passing user-controlled message: ...
- System.out / System.err call sites: ...

## Recommendation

- If LEAKS or System.out/err > 0: Phase 2 remediation slices proposed by subsystem.
- If only NEEDS-MASK > 0: pick a thresholded subset for promotion to SAFE / defensive mask.
- If all SAFE: close the track with this doc as the final artefact.
```

The doc should be CI-light: it is itself the deliverable and does not change production code. `[skip ci]` is appropriate when committed.

## Subsystem bucket plan (initial)

These are the audit "lanes". Order is by descending suspected risk; a single agent can do them sequentially or fan out per lane.

| Lane | Path | Why this bucket | Suspected risk |
|---|---|---|---|
| L1 | `integration/oauth/**` | OAuth tokens are the canonical sensitive value. Provider exchanges, refresh, revoke flows all handle plaintext tokens. | Highest |
| L2 | `security/property/**` | Encryption key material, rewrap/backfill flows. `PROPERTY_ENCRYPTION_CLOSEOUT_TODO` §449 explicitly named this. | Highest |
| L3 | `transfer/**` + `service/transfer/**` | Per-job bearer tokens (`X-Athena-Transfer-Secret`), receiver registration credentials. | High |
| L4 | `integration/mail/**` | Mail account passwords, OAuth credentials for IMAP/SMTP, fetch payloads. | High |
| L5 | `integration/wopi/**` | WOPI opaque access tokens. | Medium-high |
| L6 | `integration/ldap/**` | LDAP bind credentials. | Medium |
| L7 | `security/**` (non-property) | Auth filters, security event audit logging, decision logging. | Medium |
| L8 | `config/**` + `exception/**` | Spring config, exception handlers — usually safe but stack-trace borne leaks live here. | Lower but high blast-radius if present |

Honest gap: this list is by current package layout. If a high-risk subsystem (e.g., a future API key management module) appears later, the audit needs a refresh.

## Honest gaps in the audit method

Static grep can miss:

- **Logger method on a different name** (`tracker.log`, `eventBus.publish`, `auditLog.record`). Mitigation: enumerate all Logger-shaped APIs in the codebase first, not just SLF4J-style.
- **Indirect interpolation via overridden `toString()`** of a sensitive DTO not audited under Step 4. Mitigation: Step 4 (b) catches the common case but is best-effort; remediation may add explicit `@JsonIgnore`-equivalent annotations or override toString to redact.
- **Reflection-based loggers** (`MethodHandle`, `MethodInvoker`). Mitigation: rare in this codebase; flag if any appear during the scan.
- **Third-party libraries** that we configure to log at DEBUG, where the leak originates outside `com.ecm.*`. Mitigation: out of Phase 1 scope; Phase 2 may need to add a `Logger` level config in `logback-spring.xml`.

Surface these in the findings doc's "Honest gaps" section so the reader knows what the audit does and does not assert.

## Phase 2 (deferred — remediation)

Triggered only if Phase 1 finds LEAKS, NEEDS-MASK that survives the positive-proof pass, or System.out/err call sites.

Slice plan (proposed, not executed in this brief):

- One slice per identified leak source (typically one subsystem) — mask or remove the leak; add a regression test that proves the leak is gone (e.g., a unit test capturing log output and asserting the sensitive string is absent).
- Optional cross-cutting slice: introduce a `Logger` wrapper or a Logback `RedactingConverter` if leak patterns are repetitive. Optional — do not introduce abstraction for a single-occurrence fix.
- Each remediation slice follows the established per-slice verification doc + CI gate pattern (Backend Verify + the other six gates).

If Phase 1 finds zero leaks, Phase 2 does not open and the audit doc itself closes the track.

## Verification (for Phase 1)

```bash
# (1) doc-only deliverable
git status --short          # should show only ?? docs/SENSITIVE_DATA_LOGGING_AUDIT_FINDINGS_<date>.md (plus pre-existing .env)
git diff --check -- . ':!.env'

# (2) no production code touched
git diff --stat -- 'ecm-core/src/main/java/'  # should be empty
git diff --stat -- 'ecm-frontend/'             # should be empty
```

No CI run is required for Phase 1 by itself, because the deliverable is read-only documentation. The commit (when authorized) should carry `[skip ci]`. The audit's correctness is gated by the gate reader's review of the findings doc, not by a CI job.

## OOS (explicit)

- No production code changes in Phase 1.
- No new tests in Phase 1.
- No Logback / logging config changes in Phase 1.
- No frontend changes.
- No `.env` touch.
- No commits without explicit user "go".

## Gate criteria for opening Phase 2

Phase 2 opens only if **at least one** of:

- LEAKS count ≥ 1
- System.out / System.err count ≥ 1
- NEEDS-MASK count exceeds 10 AND a positive-proof pass on a sample is inconclusive

Otherwise the track closes with Phase 1 as the only artefact.

## Recommended execution flow

1. Gate reviews this brief; says go or amends scope.
2. Author the findings doc (single agent pass, or fan out per lane L1-L8 if scale demands).
3. Gate reviews the findings doc; classifies whether to open Phase 2.
4. If Phase 2 opens, scope per-lane remediation slices using this same brief's §Phase 2 plan as the seed.

## What this brief does not commit to

- No track is opened by this brief.
- No commits made; the brief sits in the working tree alongside the discovery doc.
- The audit itself requires the gate to say "go" before any grep runs against the production tree are turned into the findings doc.
