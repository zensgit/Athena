# Failure Inventory follow-ups — OCR & Mail-ERROR index + privacy DECISION taskbook

> Date 2026-06-29 · **decision surface, NOT an implementation.** Narrow scope: turn the two
> items the §4 first-cut (#48 / verified #49) deliberately left out — **#3 OCR failed/running**
> and **#4 ProcessedMail ERROR** — into *buildable* slices by resolving the one or two gating
> decisions each requires. No code is proposed for merge here; each slice is "ready to build"
> only after the marked **DECISION** is made. Priority (operator): **#3 then #4** (#2 async
> control plane and #5 licensing stay separately gated).

## 0. Why these two are deferred (grounded)
The §4 inventory honored two §5A guards — **index-first / O(1)** and **PII-safe (count/time/type
only)** — and therefore:
- **#3 OCR failed/running** was **deferred**: OCR state lives in `Document.metadata["ocrStatus"]`
  (values `READY | PROCESSING | FAILED`, `OcrQueueService`), a **JSON-map key with no index**. The
  `documents` table indexes only `content_id` + `mime_type`, so a FAILED/PROCESSING count today is an
  **O(n) metadata scan** — the exact anti-pattern §4 refused. *Privacy is not the blocker here* (a
  status count carries no PII); **the index口径 is.**
- **#4 ProcessedMail ERROR** was **excluded**: `ProcessedMail.status` (`PROCESSED | ERROR`) is not the
  index §4 reused (it surfaced only **account-level** `errorAccountCount` via
  `QueueBacklogObservabilityService`), AND the per-mail rows carry **PII** (`subject`, `error_message`).
  So #4 has **two** blockers — an index口径 AND a privacy/retention口径 — and the privacy one must be
  decided *first*, or we build a leaky surface.

## 1. #3 — Document OCR-state index口径 (single DECISION → then buildable)
**Goal:** an O(1)/index-backed count of `ocrStatus ∈ {FAILED, PROCESSING}` so the Failure Inventory
can show "OCR stuck/failed: N" with the same cadence as the queue-backlog card.

**DECISION (pick one):**
- **A — dedicated indexed column.** Add `documents.ocr_status` (+ `@Index idx_document_ocr_status`),
  written on the OCR transition in `OcrQueueService`, backfilled once from `metadata["ocrStatus"]`.
  → `countByOcrStatus(FAILED)` is index-backed. Cost: a migration + a write-path line + a backfill;
  most robust + DB-portable. *(recommended — mirrors how every other §4 source is an indexed count.)*
- **B — JSON-expression / partial index.** Index `metadata->>'ocrStatus'` (Postgres functional/partial
  index). → count without a new column. Cost: DB-specific, depends on the metadata column being real
  JSON(B) not a serialized blob — **verify the column type before choosing B**.
- **C — separate O(1) status registry.** A small counter the OCR worker increments/decrements on
  transition (mirrors the preview dead-letter registry). → fully decoupled from any `documents` scan.
  Cost: a registry + a write-path hook + a reconcile/backfill; best if OCR volume is huge.
- **D — keep deferred.** Inventory continues to note OCR-failure *invisibility*; no surface.

**Once decided → the buildable slice** (single-revert unit, mirrors §4): extend
`FailureInventoryService` with the index-first OCR count → a new `OcrFailures{available, failedCount,
runningCount}` block on `FailureInventorySummaryDto` → `FailureInventoryCard` shows it + links to the
OCR/preview deep surface. **PII-safe by construction** (count/type only), so no privacy decision needed.

## 2. #4 — ProcessedMail ERROR: privacy/retention口径 FIRST, then index口径
**Goal:** a per-mail ERROR backlog signal (count + triage entry) without leaking mail PII.

**DECISION 4a — privacy/retention (decide FIRST):**
- What may the inventory surface expose? Recommended **count/time/type only**: `errorCount`,
  `latestErrorAt`, and at most a non-PII tally by `account_id` / `folder` / a coarse error *category*
  — **never** `subject` or `error_message` (those stay in the existing ADMIN-gated mail diagnostics:
  `EmailAdminController` / `MailAutomationController`). A **reflection-guard test** (as in §4) should
  pin the DTO to the count/time/type allow-list so a future PII field fails the build.
- **Retention:** does an ERROR row persist indefinitely (then the backlog count grows unbounded +
  conflates old/triaged with live)? Decide a retention/triage semantics: e.g. count only ERRORs newer
  than N days, or only un-acknowledged ones — otherwise the number is not actionable. *(This is the
  decision that most shapes whether the surface is useful vs noise.)*

**DECISION 4b — index口径 (after 4a):**
- Index `ProcessedMail.status` (or a partial index `WHERE status = 'ERROR'`, optionally composite with
  `processed_at` to support the retention window from 4a) → `countByStatus(ERROR)` / windowed count is
  O(1)/index-backed, replacing any scan. Confirm against the existing `@Table` index set before adding.

**Once both decided → the buildable slice:** an index-first ERROR-mail count (honoring the 4a window) →
`MailFetchErrors` gains a per-mail `errorCount`/`latestErrorAt` (alongside the existing
`errorAccountCount`) → card shows it, **links out** to the deep mail surface for the PII detail. Read-only;
no replay/clear here (that's the mail control plane, separately gated).

## 3. Explicitly out of scope (still gated, not in this taskbook)
- **#2 async control plane** — product-semantics decision (**observe-only vs allow operations**) must
  be made before any design; highest slippage risk.
- **#5 licensing** — commercial product-line decision; last.
- Any **replay/clear/retry/requeue** on #3/#4 — those are per-domain control planes, not inventory.

## 4. Recommended decision order
1. **#3 DECISION** (A/B/C/D) — smallest, no privacy dimension; A recommended.
2. **#4 DECISION 4a** (privacy/retention) — then **4b** (index). 4a gates the whole surface.
Each decision, once made, yields a single clean, single-revert slice that mirrors the §4 pattern
(index-first count + PII-safe DTO + card + link-out). No build proceeds without its DECISION.
