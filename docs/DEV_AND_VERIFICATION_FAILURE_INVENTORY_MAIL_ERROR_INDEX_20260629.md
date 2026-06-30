# Failure Inventory #4 — Mail per-message ERROR index (Option A) — Development & Verification (2026-06-29)

## 1. State at a glance

This slice closes taskbook item **#4** from the backlog decision taskbook
(`DEVELOPMENT_FAILURE_INVENTORY_BACKLOG_2_5_DECISION_TASKBOOK_20260629.md`) using the owner-ratified
**Option A**: a count-only, index-first per-message mail ERROR signal on the cross-subsystem Failure
Inventory.

Owner decisions recorded: **4a = count-only** (display count only — no `subject`/`error_message`),
retention left on the existing shared 90d purge; **4b = a plain single-column `status` index**. Options B
(redacted per-account/per-rule breakdown), C (raw detail in the card), and D (defer) were not taken.

Two of the three blockers the original taskbook named for #4 were already resolved in code: **retention
already exists** (`MailProcessedRetentionService`, daily 90d purge — bounds the count), and choosing
count-only resolves the **PII-display** blocker. The only real work was the **status index**.

## 2. What was built (code-grounded)

The new signal is **one O(index) count**, distinct from the existing account-level fetch-health count.

- **Schema** — `db/changelog/changes/096-add-mail-processed-status-index.xml` (+ master include after 095,
  before 007-insert): `createIndex idx_mail_processed_status` on `mail_processed_messages(status)`; rollback
  drops it. `status` (enum `PROCESSED|ERROR`) already existed (migration 014) but was unindexed — only
  `(account_id, folder, uid)` unique + `(account_id)` were indexed, so a `WHERE status='ERROR'` count was a
  full scan.
- **Read path** — `ProcessedMailRepository.countByStatus(ProcessedMail.Status)` — a derived, index-backed
  count. (No soft-delete column on this log table, so no `deleted` predicate, unlike the OCR/document count.)
- **Aggregator** — `FailureInventoryService.mailProcessedErrors()` returns
  `MailProcessedErrors(available, errorCount)` from `countByStatus(ERROR)`, wrapped in try/catch →
  `MailProcessedErrors(false, 0)` so a DB fault isolates to this one source and never throws.
- **DTO** — `FailureInventorySummaryDto` gains a 5th component `MailProcessedErrors mailProcessed`
  (`boolean available, long errorCount`) — count only, **no** `subject`/`error_message`. It is a **different
  axis** from the reused account-level `MailFetchErrors` (an account can be fetch-healthy yet still have
  message-level processing errors), so it supplements rather than replaces it.
- **Wiring boundary** — read **directly** from `ProcessedMailRepository`, **not** routed through
  `QueueBacklogObservabilityService`, whose contract promises it never queries `mail_processed_messages`.
- **UI** — `FailureInventoryCard` "Mail" panel now shows two lines: "Accounts in error (fetch)" (existing)
  and "Messages failed (processing)" (new), each independently available-gated, with the existing link-out to
  the ADMIN mail diagnostics surface for the raw per-message detail.

## 3. PII / §5A guards — honored

Count + boolean only. The `MailProcessedErrors` record carries no `subject`, `error_message`, sender, or
account identity. The reflection-guard test walks `MailProcessedErrors` and asserts every component name is
in the explicit allow-set (`mailProcessed`, `errorCount` added). The read is index-first
(`idx_mail_processed_status`), not a full-table scan. Raw per-message detail stays in the existing
ADMIN-gated `GET /api/v1/integration/mail/diagnostics` surface (link-out only).

## 4. Verification

### 4.1 Tests added / updated

Backend (`ecm-core`):
- `FailureInventoryServiceTest` — 4-arg ctor (mock `ProcessedMailRepository`); the aggregation test stubs +
  asserts `mailProcessed`; the all-sources-isolation test asserts `mailProcessed.available=false` on a DB
  fault; two new focused tests: `mailProcessedErrorIsIndexFirst` (verifies `countByStatus(ERROR)` is the only
  repository interaction) and `mailProcessedSourceFailsClosedIndependently` (per-message fault isolates; the
  account-level mail signal stays healthy). Reflection-guard allow-set/records extended with
  `mailProcessed`/`errorCount`/`MailProcessedErrors.class`.
- `FailureInventoryAdminControllerTest` — DTO built with the 5th arg; new `$.mailProcessed.*` jsonPath
  assertions.
- `FailureInventoryAdminControllerSecurityTest` — `sampleSummary()` built with the 5th arg.

Frontend (`ecm-frontend`):
- `FailureInventoryCard.test.tsx` — `summary` mock gains `mailProcessed`; the panel test asserts both mail
  axes render; new tests assert the per-message count renders distinct from the account-level line and that a
  per-message fault isolates without hiding the account-level line. **Run locally — 7/7 green.**

### 4.2 Local results — honest scope

This environment has **no Maven cache and no Central access**, so the JVM module is not compiled/tested here;
backend verification is **static review** (constructor arity, all DTO call-sites, reflection-guard
allow-set/records, migration index/rollback, repository derived-query name) plus the **first PR CI run as the
authoritative gate**. The frontend card test was run locally (`react-scripts test`) — **7/7 green**.

### 4.3 CI — the authoritative gate

CI boots Liquibase against Testcontainers Postgres under `ddl-auto: validate`, so migration 096 is exercised
there (the no-DB unit/controller tests mock the repository/service). Treat the first green PR run as the gate.

## 5. Boundaries / out of scope

- **No new endpoint, no replay/clear/retry** — observability only, reusing `GET /api/v1/admin/failure-inventory`.
- **No raw per-message detail** — count only; subject/error text stay in the ADMIN mail diagnostics surface.
- **No change to `QueueBacklogObservabilityService`** — its account-level signal and its
  "never queries `mail_processed_messages`" boundary are intact; the per-message count supplements it.
- **No new retention** — the shared 90d purge already bounds the count. A timestamp (`latestErrorAt`) and a
  per-account/per-rule breakdown were deliberately left out (the former would argue for a composite
  `(status, processed_at)` index; the latter is taskbook Option B, a separate decision).
- **#2 / #5** stay decision-gated and untouched.
