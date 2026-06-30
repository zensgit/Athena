# Failure Inventory #3 — OCR Status Index (Option A) — Development & Verification (2026-06-29)

## 1. State at a glance

This slice closes taskbook item **#3** from
`DEVELOPMENT_FAILURE_INVENTORY_OCR_MAIL_INDEX_PRIVACY_DECISION_TASKBOOK_20260629.md`
by implementing the **Option A**口径 that taskbook recommended: promote the OCR state — today an
**unindexed** `nodes.metadata["ocrStatus"]` jsonb key (`READY|PROCESSING|FAILED|SKIPPED`) — to a
dedicated, indexed `documents.ocr_status` column, and surface FAILED / PROCESSING counts in the
existing cross-subsystem Failure Inventory card built in §4.

Decision recorded: **#3 = Option A** (indexed mirror column). Options B (live jsonb scan), C (reuse
the queue-backlog OCR pending depth only), and D (defer) were not taken — A is the only option that
gives an O(1) FAILED count without an O(n) jsonb scan and without conflating "failed" with "pending".

## 2. What was built (code-grounded)

The new failure signal is **two O(1) index counts**, nothing more.

- **Schema** — `db/changelog/changes/095-add-document-ocr-status.xml` (+ master include after 094,
  before 007-insert-initial-data): `addColumn documents.ocr_status varchar(32)` nullable +
  `createIndex idx_document_ocr_status` + a **Postgres-only** one-time backfill
  `UPDATE documents d SET ocr_status = n.metadata->>'ocrStatus' FROM nodes n WHERE n.id = d.id AND
  n.metadata IS NOT NULL` (metadata lives on the JOINED parent `nodes` table; `documents.id =
  nodes.id`). Rollback drops the index then the column.
- **Entity** — `Document`: `@Index(name = "idx_document_ocr_status", columnList = "ocr_status")` on
  `@Table` + `@Column(name = "ocr_status", length = 32) String ocrStatus` (length matches the DDL;
  the module runs `ddl-auto: validate`).
- **Write path** — `OcrQueueService` already owns every OCR transition; it now mirrors the status
  onto the column at the two points it writes `metadata["ocrStatus"]` (`READY` on success;
  `PROCESSING` / `FAILED` / `SKIPPED` via `setOcrMetadata`). The column is therefore kept current
  going forward on every DBMS, independent of the Postgres-only backfill.
- **Read path** — `DocumentRepository.countByOcrStatus(status)` =
  `SELECT COUNT(d) … WHERE d.ocrStatus = :status AND d.deleted = false` (index-backed, count only).
- **Aggregator** — `FailureInventoryService.ocrFailures()` returns
  `OcrFailures(available, failedCount, runningCount)` from `countByOcrStatus("FAILED")` /
  `("PROCESSING")`, wrapped in try/catch → `OcrFailures(false, 0, 0)` so an OCR/DB fault isolates to
  this one source and never throws (same §6 isolation contract as the other three sources).
- **DTO** — `FailureInventorySummaryDto` gains a 4th component `OcrFailures ocr`
  (`boolean available, long failedCount, long runningCount`) — count only, **no** document name,
  text, or failure reason.
- **UI** — `FailureInventoryCard` gains an "OCR processing" panel showing `Failed` / `Processing`
  counts (or `unavailable`). It intentionally carries **no "Open …" deep-surface link**: unlike the
  preview/transfer/mail panels, OCR has no dedicated admin diagnostics route, so a caption
  ("Per-document OCR state is shown on each document.") replaces a link that would point at nothing.

## 3. PII / §5A guards — honored

Count + boolean only. The `OcrFailures` record carries no `reason`, `subject`, `errorMessage`,
document name, or text. The same reflection-guard test that protects the §4 DTO now also walks
`OcrFailures` and asserts every component name is in the explicit allow-set
(`available`, `failedCount`, `runningCount`). The read is index-first
(`idx_document_ocr_status`), not a `nodes.metadata` jsonb scan.

## 4. Verification

### 4.1 Tests added / updated

Backend (`ecm-core`):
- `FailureInventoryServiceTest` — 3-arg ctor (mock `DocumentRepository`); the aggregation test now
  stubs + asserts `ocr` FAILED/PROCESSING; the isolation test asserts `ocr.available=false` on a DB
  fault; two new focused tests: `ocrCountsAreIndexFirst` (verifies `countByOcrStatus("FAILED")` +
  `("PROCESSING")` are the only repository interactions) and `ocrSourceFailsClosedIndependently`
  (OCR DB fault isolates; transfer/mail/preview stay healthy). Reflection-guard allow-set/records
  list extended with `ocr`/`runningCount`/`OcrFailures.class`.
- `FailureInventoryAdminControllerTest` — DTO built with the 4th arg; new `$.ocr.*` jsonPath
  assertions.
- `FailureInventoryAdminControllerSecurityTest` — `sampleSummary()` built with the 4th arg.

Frontend (`ecm-frontend`):
- `FailureInventoryCard.test.tsx` — `summary` mock gains `ocr`; the panel-count test now expects
  four panels + the OCR caption; new tests assert OCR FAILED/PROCESSING counts render with **no**
  "Open OCR" link, and that OCR `available=false` isolates without breaking peers.

### 4.2 Local results — honest scope

This environment has **no Maven cache and no access to Maven Central**, so the JVM module cannot be
compiled or tested here; backend verification is **static review** (constructor arity, all three DTO
call-sites updated, reflection-guard allow-set/records, migration column/index/backfill, entity↔DDL
length parity) plus the **first PR CI run as the authoritative gate**. `nodes.metadata` was confirmed
`jsonb` (entity `columnDefinition="jsonb"` + migration `003` `type="JSONB"`) and the backfill join
(`documents.id = nodes.id`, JOINED inheritance) confirmed by hand. The frontend card test was run
locally (`react-scripts test`) once dependencies installed — see the PR run for the recorded result.

### 4.3 CI — the authoritative gate

CI compiles the module under `ddl-auto: validate` and boots Liquibase against Testcontainers
Postgres in the repository/integration tests, so migration 095 — **including** the Postgres-only
backfill — is exercised there (it cannot be on the no-DB unit/controller tests, which mock the
service). Treat the first green PR run as the gate.

## 5. Boundaries / out of scope

- **No new endpoint, no replay/retry** — observability only, reusing the existing
  `GET /api/v1/admin/failure-inventory`.
- **No raw OCR failure detail** — counts only; per-document OCR state stays on the document surfaces.
- **No backfill on non-Postgres** — the column is populated going forward by `OcrQueueService`;
  only the historical backfill is Postgres-gated.
- **#4 (Mail ERROR index)** stays decision-gated (awaits the 4a privacy/retention decision);
  **#2 / #5** unchanged. This slice is single-revert (one changeset + one column + one DTO field +
  card + tests).

## 6. Known follow-up (non-blocking)

`FailureInventoryService` pins the OCR status vocabulary (`"FAILED"`, `"PROCESSING"`) as local
constants that must match the literals `OcrQueueService` writes. They match today; if that
vocabulary ever drifts, the card would silently read 0 with nothing failing. A future tidy could
hoist these to a single shared constant (or a pin-test) to close that silent-failure surface. Left
out here to keep the slice single-revert.
