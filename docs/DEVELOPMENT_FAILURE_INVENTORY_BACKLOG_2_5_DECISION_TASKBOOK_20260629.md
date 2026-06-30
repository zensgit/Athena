# Failure Inventory backlog #2–#5 — Owner DECISION taskbook (2026-06-29)

> **Decision surface, NOT an implementation.** This turns the four remaining decision-gated backlog
> items — **#2 async control plane, #3 OCR index, #4 Mail ERROR backlog, #5 licensing** — into
> *buildable slices*, each gated on one or two explicit owner **DECISION**s. No code is proposed for
> merge here. It **supersedes** `DEVELOPMENT_FAILURE_INVENTORY_OCR_MAIL_INDEX_PRIVACY_DECISION_TASKBOOK_20260629.md`
> (which covered only #3/#4) by (a) recording that **#3 has since been built** and (b) adding #2 and #5.
> Operator priority: **#3 (ratify) → #4 → #2 → #5.** Findings are code-grounded (real file refs); an
> investigation fan-out over the live tree produced them.

## 0. What changed since the #3/#4 taskbook — the material findings

Three investigation findings move items from "needs N decisions" toward "needs 1, or just a ratify":

- **#3 is no longer "to decide" — it is built.** PR **#52** (branch `claude/ocr-failure-inventory-index`)
  implements **Option A** (a dedicated, indexed `documents.ocr_status` column). CI **Backend Verify /
  Frontend Build & Test / Phase C Security = green** (broader smoke/regression gates were still running
  at snapshot). The open action is **RATIFY / REVISE / HOLD**, not build.
- **#4 lost two of its three stated blockers.** Mail **retention already exists** —
  `MailProcessedRetentionService` purges `mail_processed_messages` on a daily 02:00 `@Scheduled` job
  (default 90d, status-agnostic) — so the count is already naturally bounded; and choosing **count-only**
  display resolves the PII blocker. **Only the status index (4b) is real work.**
- **#2 needs no new primitives.** Every mutation the inventory card links to **already exists**
  per-domain, **ADMIN-gated and audited** (preview: `PreviewDiagnosticsController` / `OpsRecoveryController`;
  transfer: `TransferReplicationController`; mail: `MailAutomationController`). #2 is "surface/unify
  existing actions," not "build missing ones" — with two real traps (transfer-retry is non-idempotent;
  OCR has no admin control surface).

The backlog rows in `docs/DEVELOPMENT_CROSS_SUBSYSTEM_FAILURE_DEADLETTER_OBSERVABILITY_TASKBOOK_20260627.md`
§8 and `docs/DEVELOPMENT_PLAN_COMPLETION_AND_VERIFICATION_20260627.md` §3 are **stale** for #3 and #4
and should be updated when those land.

---

## 1. #3 — OCR FAILED/RUNNING index — **RATIFY (already built in #52)**

**State:** built as **Option A** in PR #52 — migration `095-add-document-ocr-status.xml` (column +
`idx_document_ocr_status` btree + Postgres-only one-time backfill from `nodes.metadata`), `Document.ocrStatus`
(`@Index`, `length=32` under `ddl-auto: validate`), `OcrQueueService` mirrors the status on every transition
(works on any DBMS going forward; only the historical backfill is Postgres-gated), `DocumentRepository.countByOcrStatus`,
`FailureInventoryService.ocrFailures()` → count-only `OcrFailures(available, failedCount, runningCount)`, an
"OCR processing" card panel, and backend+frontend tests incl. a reflection PII-guard. Reuses
`GET /api/v1/admin/failure-inventory`; no new endpoint, no replay/retry.

**DECISION — RATIFY / REVISE / HOLD** (the "Document.metadata index-strategy" call, now concrete):

- **A — dedicated indexed column** `documents.ocr_status` *(CHOSEN, built, CI-green — recommended RATIFY)*.
  Cleanest query shape (plain JPA derived `COUNT … WHERE ocrStatus=:status AND deleted=false`), serves
  **both** FAILED and PROCESSING(running), scoped to `documents`, DBMS-agnostic DDL + pure-JPA write path.
  Cost (already paid): entity↔DDL coupling under `validate` (095 must always ship) + a one-time Postgres
  backfill.
- **B — jsonb index on `nodes.metadata`** (GIN containment, or an expression btree on
  `((metadata->>'ocrStatus'))`). No entity change, but a strictly worse/Postgres-only query shape: default
  GIN can't serve the `->>` equality; the expression index is brittle and needs a native, node-scoped count;
  neither cleanly serves the "running" count.
- **C — separate O(1) status registry** (worker increments/decrements, like the preview dead-letter
  registry). Fully decoupled from `documents`; costs a registry + reconcile/backfill. Best only if OCR
  volume is huge.
- **D — defer** (discard #52; OCR failures stay invisible). Throws away a green, single-revert slice.

**Recommendation: RATIFY A (merge #52).** It is the only option giving an index-backed COUNT for both
FAILED and running without an O(n) jsonb scan, and its costs are already paid + CI-exercised. (Honest
caveat: A's "portability" edge is largely theoretical — the module is de-facto Postgres-pinned today.)

**Non-blocking fast-follow (not part of #52, to keep it single-revert):** the OCR status strings are
duplicated (`OcrQueueService` literals vs `FailureInventoryService.OCR_STATUS_*`); hoist to a shared
constant or add a pin-test to close the silent-drift surface the #52 dev/verification doc §6 itself flags.

**Owner questions:** (1) Ratify A as the standing OCR index口径 (merge #52) and mark §8/§3 rows shipped?
(2) Is counting **PROCESSING as "running"** the intended semantics (RETRYING is collapsed into PROCESSING)
— or do you want FAILED-only / a distinct RETRYING bucket? (3) Fold the constant-drift tidy into the
merge, or track as a fast-follow?

---

## 2. #4 — Mail ProcessedMail ERROR backlog — **4a (PII/retention) → 4b (index)**

**State:** the mail panel today is **account-level fetch-health** (`MailFetchErrors.errorAccountCount`,
from `QueueBacklogObservabilityService` counting `MailAccount.lastFetchStatus == ERROR`) — it deliberately
**never queries** `mail_processed_messages`. The per-message table `ProcessedMail` has
`status (PROCESSED|ERROR)`, with **PII only in `subject` + `error_message`** (no sender/transport columns).
It has **no status index** (only `(account_id, folder, uid)` unique + `(account_id)`), so an unbounded
`WHERE status='ERROR'` count is a full scan today. **Retention already exists** (90d daily purge,
status-agnostic). Raw per-message detail is **already reachable** by ADMINs at
`GET /api/v1/integration/mail/diagnostics`.

**DECISION 4a — PII/retention (decide FIRST):**
- **A — count-only** (count + latest `errorAt`; no subject/sender/text) — *recommended*; matches the shipped
  §5A guards and the `OcrFailures` precedent; retention stays the shared 90d (bounds the count → no new work).
- **B — redacted breakdown** (per-account/per-rule counts + an error *category*, text stripped). More
  actionable, but adds a redaction/PII-classification burden and breaks the symmetric "count/time/type only"
  posture.
- **C — full raw detail in the card** — **rejected**: violates the DTO PII contract + reflection-guard test,
  and duplicates the existing ADMIN diagnostics surface.
- **D — defer / keep account-level, sharpen the link-out** (relabel so account-level vs per-message isn't
  conflated). Zero code, but leaves the real gap: a fetch-healthy account can still hide message-level ERRORs.

**DECISION 4b — index (after 4a):** smallest precedent-matching index = a **plain single-column
`idx_mail_processed_status`** (mirrors `idx_replication_job_status` / `idx_document_ocr_status`); or a
composite `(status, processed_at)` / partial `WHERE status='ERROR'` if you want windowed reads later.

**Recommendation: A, then a plain status index.** Wire it as a **new** `FailureInventoryService` signal
reading `ProcessedMailRepository.countByStatus(ERROR)` **directly** (mirroring `ocrFailures()`), kept
**separate** from the existing account-level `MailFetchErrors`, and **not** routed through
`QueueBacklogObservabilityService` (whose contract forbids touching `mail_processed_messages`). **Do not
build 4b before 4a is set** — adding the query first invites a payload that leaks subject/errorMessage into
the card.

**Buildable slice (once 4a = count-only):** migration `096-add-mail-processed-status-index.xml` (index +
rollback) + `ProcessedMailRepository.countByStatus` + a count-only DTO record (e.g.
`MailProcessedErrors(available, errorCount, latestErrorAt)`) + `FailureInventoryService.mailProcessedErrors()`
(try/catch isolation) + card panel + tests (index-first assert, source-isolation, extend the reflection
PII-guard, security test). Single-revert.

**Owner questions:** (1) 4a = count-only (A), redacted breakdown (B), or defer (D)? (2) ERROR rows fine on
the shared 90d retention (recommended), or a distinct window? (3) 4b index = plain `status`, or composite
`(status, processed_at)`? (4) Supplement the account-level count (two signals) vs replace it?

---

## 3. #2 — Async control plane — **observe-only vs allow operations**

**State / key finding:** the inventory is read-only by design, but the **mutations already exist** per-domain,
ADMIN-gated and **audited**: preview (`PreviewQueueService.enqueue/cancel` + `PreviewDiagnosticsController` /
`OpsRecoveryController` replay/clear by filter, dry-run, history), transfer
(`TransferReplicationController` retry/run), mail (`MailAutomationController` fetch/replay/bulk-delete). Two
real traps: **transfer retry is NOT idempotent** today (`queueRetryJob` lacks the `hasActiveJob` guard →
double-click = two RUNNING jobs replicating the same source→target), and **OCR has no admin control surface**
at all (only a doc-scoped, non-ADMIN per-document re-queue). The `DocumentController` preview/OCR queue
endpoints also lack `@PreAuthorize`.

**DECISION — product scope:**
- **A — stay observe-only** *(recommended)*: keep the card read-only; mutations stay in the existing audited
  per-domain surfaces it links to. Zero new code/risk; honors the taskbook §8 boundary.
- **B — one safe op: "retry one preview dead-letter"**: an ADMIN-only confirm-dialog button that **delegates**
  to the existing idempotent preview replay path. The only minimal opt-in that's safe today (preview enqueue
  is idempotent via `enqueueIfAbsent`/governanceKey; already audited). Single-revert; preview-only.
- **C — full unified control plane** (requeue/cancel/retry for all four): **decline for now** — maximum trap
  surface (transfer non-idempotency, cancel races, net-new OCR surface, partial-success batches); the exact
  "slides into action buttons" risk §8 warned about.

**Recommendation: A (observe-only).** If you want visible action, **B** is the only safe minimal step, and
even then transfer/mail/OCR stay excluded. **C** should wait until transfer-retry is made idempotent and OCR
gets a real admin surface (i.e., until/after #3 and an OCR control-plane decision).

**Owner questions:** (1) Observe-only or allow operations? (2) If operations: scope to preview-retry only
(B), with transfer **excluded** until an active-job guard exists? (3) Any cancel op (preview cancel is
best-effort; transfer/mail have none)? (4) Do OCR control actions belong in #2 or wait for an OCR admin
surface? (5) Confirm ADMIN-only + audited, routed through the existing per-domain endpoints (not the
non-ADMIN `DocumentController` ones)?

---

## 4. #5 — Real licensing (commercial entitlement) — **sequence LAST; set 4 axes first**

**State:** a **mock stub** exists and is mostly **dead/unsafe**: `LicenseService.validateLicense()`
fabricates **Enterprise** for any key ≠ `"invalid"` (the RSA/JWT imports are present but never called — a
**false-grant landmine**, not a head-start); `checkUserLimit()` / `isFeatureEnabled()` have **zero callers**;
the read-only `GET /api/v1/system/license` (ADMIN, with a security test) is live but **unconsumed** by the UI.
`BUSINESS_PLAN.md` defines Community / Enterprise / Ultimate editions + per-seat pricing — the `LicenseInfo`
model already carries edition/maxUsers/maxStorageGb/expiry/features. **Not to be conflated:**
`TenantQuotaService` (real, wired per-tenant **storage** quota — adjacent hard-block prior art, not
licensing); the `DETAILED_IMPLEMENTATION_PLAN.md` user-tiers (API **rate-limiting**, not licensing); Yuantus
`is_entitled` + license CLI (a **separate repo** — prior art only).

**DECISION — four axes (no engineering until all are set):**
- **A — what is licensed:** seats / features / **editions+seat-cap** *(recommended; matches BUSINESS_PLAN.md
  + the existing model)*. Define a "billable seat" (today `UserRepository.count()` would over-count
  disabled/system accounts).
- **B — enforcement:** hard-block / **observe-and-warn first** *(recommended; matches the read-only-first
  cadence)* / honor-system.
- **C — source of truth:** **signed offline license file** *(recommended; air-gapped-friendly; the stub's
  unused RSA/JWT imports already point here)* / license server / plain config.
- **D — expiry & fail-mode:** fail-closed / **grace + soft-degrade** *(recommended; never lock a customer out
  of their own data)* / fail-open. (Retire the current silent-Enterprise-on-any-key behavior regardless.)

**Recommendation: sequence #5 LAST.** Once axes are set, the **first** slice is observe-only and tiny: make
the readout **truthful** (stop fabricating Enterprise; report actual Community-when-no-key) and surface it as
a read-only AdminDashboard "License / Edition" card consuming the existing endpoint — **no enforcement**, real
signature verification deferred to an axis-C slice.

**Owner questions:** (1) Confirm #5 runs last and no engineering starts until A–D are decided? (2) A: editions
+ seat-cap, and what counts as a billable seat? (3) B: observe-and-warn first, or must something hard-block on
day one? (4) C: signed offline file vs license server; who issues/signs (a minting/revoke CLI like Yuantus's)?
(5) D: grace window + what "degrade" means per feature? (6) Retire the silent-Enterprise false-grant
immediately as a standalone fix?

---

## 5. Recommended decision order & slice readiness

| # | Item | Gating decision(s) | Recommendation | Slice state |
|---|---|---|---|---|
| 3 | OCR index | index口径 (already answered = A) | **RATIFY #52** | **built, CI-green — merge** |
| 4 | Mail ERROR | 4a PII/retention → 4b index | **A: count-only + plain status index** | ready to cut once 4a set (1 PR) |
| 2 | Async control plane | observe-only vs operate | **A: stay observe-only** (B if any op) | A = no code; B = 1 small PR |
| 5 | Licensing | axes A–D | **sequence last**; honest readout first | no slice until axes set |

Each decided item yields a single clean, single-revert slice mirroring the §4 pattern (index-first count +
PII-safe DTO + card + link-out). **No build proceeds without its DECISION.**

## 6. Guardrails that hold across all items

Index-first (no O(n) jsonb / `nodes.metadata` / `ProcessedMail` scans); PII-safe (count/time/type only on the
inventory; raw failure text stays in the existing ADMIN-gated deep surfaces, link-out only); the reflection
PII-guard test pins every inventory DTO record to a count/time/type allow-list; any mutation (if ever
approved) is ADMIN-only + audited via `AuditService` and routes through the existing per-domain endpoints. No
replay/clear/retry/requeue is part of the *inventory* — those are per-domain control planes, separately gated.
