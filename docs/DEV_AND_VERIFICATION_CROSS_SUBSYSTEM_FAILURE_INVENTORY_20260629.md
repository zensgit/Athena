# Cross-Subsystem Failure Inventory — Development & Verification (2026-06-29)

- Date: 2026-06-29
- Line: Cross-subsystem failure / dead-letter observability — **Day-2 first-cut** (the §4 slice of the
  Day-1 taskbook `DEVELOPMENT_CROSS_SUBSYSTEM_FAILURE_DEADLETTER_OBSERVABILITY_TASKBOOK_20260627.md`, #46).
- Feat PR: **#48 (`df7ec2c`) — merged**, branch `claude/cross-subsystem-failure-inventory`.
- Governance note: built under the operator's **autonomous-development mandate** ("complete the remaining
  development plan without the user"), which **supersedes the earlier "B (hold)"** on this slice. The §7
  ratification below is recorded as part of that mandate; the feat PR is a clean, single-revert unit if the
  operator wants to reverse the decision on return.

## 1. State at a glance

| Leg | PR | Status |
|---|---|---|
| Day-1 taskbook / gap audit | #46 (`feb5ae5`) | merged |
| **Day-2 first-cut (this line)** | **#48 (`df7ec2c`)** | **merged — CI 7/7 green** |
| Verification doc (this file) | this PR | merging |

The taskbook deliberately wrote the gap **honestly-narrow**: the one genuinely new cheap signal is the
**preview dead-letter count**; everything else is co-location + links. This slice builds exactly that — no
padding to disguise the thinness.

## 2. §7 ratification (decided)

Per the taskbook §7 checklist (recommendation → ratified):

- **First-cut surfaces** → preview dead-letter count (NEW) + a failure-triage hub linking the existing deep surfaces. ✅
- **Transfer / mail** → count only, **reuse** `QueueBacklogObservabilityService` (no duplication). ✅
- **ProcessedMail ERROR** → **exclude** (unindexed + PII); link out to `/integration/mail/diagnostics`. ✅
- **OCR failed/running** → **defer** (unindexed `Document.metadata`); note invisibility. ✅
- **Surface** → AdminDashboard card (inherits the queue-backlog / scheduler-run cadence). ✅
- **Guards** → §5A index-first **and** PII-safe (count/time/type only; raw text stays in the deep surfaces). ✅

## 3. What was built (code-grounded)

- **`FailureInventorySummaryDto`** (`com.ecm.core.failureinventory`, Java record): nested `PreviewDeadLetter`
  (`available`, `deadLetterCount`, `categoryTally: Map<String,Long>`, `latestFailedAt: Instant`),
  `TransferFailures` (`available`, `failedCount`), `MailFetchErrors` (`available`, `errorAccountCount`).
  The shape carries **no raw failure text** by construction.
- **`FailureInventoryService`**: the NEW signal is `previewDeadLetterRegistry.getItemCount()` (O(1)) +
  `list(500)` for a non-PII category tally + latest `failedAt`; transfer FAILED and mail account-level ERROR
  counts are pulled from a **single** `queueBacklogObservabilityService.getSummary()` call (reuse, not
  recompute), with each upstream `available` flag propagated. Every source is wrapped so a failure yields
  `available=false` and the service **never throws**.
- **`FailureInventoryAdminController`**: dual-path `GET /api/admin/failure-inventory` +
  `/api/v1/admin/failure-inventory`, `@PreAuthorize("hasRole('ADMIN')")`, read-only.
- **Frontend `FailureInventoryCard`** (mounted on `AdminDashboard`): mirrors `QueueBacklogCard` —
  failure-isolated fetch (a fetch error shows a local warning, never breaks the dashboard), per-source
  `available` fallback, and **links out** to `/admin/preview-diagnostics`, `/admin/transfer-replication`,
  `/admin/mail`.

## 4. §5A guards — honored (verified, not asserted)

- **Index-first / O(1)**: preview = registry `getItemCount()` (Redis ZCARD or in-memory size) + bounded
  `list(500)`; transfer = index-backed `countByStatus(FAILED)` (`idx_replication_job_status`), reused; mail =
  account rows, reused. This slice adds **no** `ProcessedMail.status` scan, **no** `Document.metadata` scan,
  and does **not** adopt the `RecordsManagementController.findAll().stream()` anti-pattern.
- **PII-safe**: the DTO + card expose **count + timestamp + type/category only**. Raw failure text
  (`reason`/`subject`/`errorMessage`/`transportMessage`/`errorLog`/`lastMessage`/`entryReport`) never leaves
  the existing ADMIN-gated deep surfaces. A **reflection guard test** asserts the DTO's record components are
  within the count/time/type allow-list and never a raw-text field — so a future field addition fails the build.
- **Read-only**: no replay / clear / retry / requeue (those are the per-domain control planes, out of scope).

## 5. Verification

### 5.1 Tests added

| Test | Class | Cases | Asserts |
|---|---|---|---|
| Service aggregation/isolation | `FailureInventoryServiceTest` | 4 | new preview count + tally + latest; reused transfer/mail counts; per-source `available=false` + never-throws; upstream-unavailable propagation; **reflection PII guard** (count/time/type only) |
| Controller security | `FailureInventoryAdminControllerSecurityTest` | 3 | 401 unauthenticated, 403 ROLE_USER, 200 ROLE_ADMIN (TestSecurityConfig mirrors prod `/api/**`-authenticated + `@EnableMethodSecurity`) |
| Controller shape | `FailureInventoryAdminControllerTest` | 1 | JSON path for every DTO field incl. `latestFailedAt` as an ISO string (production-equivalent `Instant` JSON) |
| Frontend card | `FailureInventoryCard.test.tsx` | 3 | renders 3 panels + a category chip + the 3 deep-surface links; per-source unavailable isolation; fetch-failure isolation (no crash) |

### 5.2 Local results

- **Frontend `FailureInventoryCard` test: 3/3 PASS** (`npm test -- --watchAll=false FailureInventoryCard`).
- **Backend: no Maven on the dev box** (the `ecm-core/mvnw` wrapper needs Docker, also absent), so backend
  compile + full tests were **not** run locally — they were verified by CI's `Backend Verify` gate (§5.3).
  Static compile-correctness was additionally confirmed by reading every reused accessor against its source
  (`QueueBacklogSummaryDto.transfer().failedCount()` / `.mail().errors()`,
  `PreviewDeadLetterRegistry.getItemCount()`/`list(int)`/`DeadLetterEntry.category()/failedAt()`,
  `getSummary()` public) — see §5.4.

### 5.3 CI — the authoritative gate (7/7 green)

CI run **`28384980621`** on PR #48 — **all 7 gates pass**:

| Gate | Result |
|---|---|
| Backend Verify | ✅ pass (2m35s) — backend compile + full test suite incl. the 3 new tests |
| Frontend Build & Test | ✅ pass (8m52s) |
| Phase C Security Verification | ✅ pass (5m16s) |
| Property Encryption Closeout Gate | ✅ pass (5m17s) |
| Acceptance Smoke (3 admin pages) | ✅ pass (6m54s) — AdminDashboard card change did not break the admin pages |
| Frontend E2E Core Gate | ✅ pass |
| Phase 5 Mocked Regression Gate | ✅ pass |

### 5.4 Adversarial review (pre-merge)

Two independent review passes (compile-correctness + §5A/PII; pattern-conformance + test-calibration + frontend):

- **Compile-correctness**: PASS — every reused accessor verified against source; record constructors, Lombok
  arity, `Collectors.groupingBy(counting())→Map<String,Long>`, imports all confirmed. (This static pass was
  the only pre-CI backend compile check, given no local Maven; CI's `Backend Verify` then confirmed it.)
- **§5A / PII**: PASS — no raw-text leak; reflection guard non-vacuous; no new unindexed/unbounded read.
- **Pattern conformance**: PASS — dual-path endpoint, `@PreAuthorize`, record DTO, card isolation, mount all
  mirror the queue-backlog line.
- **Test calibration**: 2 MEDIUM findings (both in the shape test), **fixed before merge** — (1) the shape test
  now asserts `$.preview.latestFailedAt`; (2) it installs a production-equivalent `ObjectMapper`
  (`disable(WRITE_DATES_AS_TIMESTAMPS)`) so `Instant` serializes as the ISO string the frontend expects,
  matching the repo's `*ResponseContractTest` convention.

## 6. Boundaries / out of scope

- Read-only — no replay/clear/retry/requeue (per-domain control planes excluded).
- Does **not** re-aggregate the 6 async-task governance domains (Axis A).
- ProcessedMail-ERROR (unindexed + PII) and OCR failed/running (unindexed) are **excluded/deferred** — link-out
  / note-invisibility only.
- Did not touch the closed lines (queue-backlog, scheduler-run, storage, OAuth, logging).

## 7. Remaining development plan — status after this slice

With the §4 first-cut delivered + merged, the development plan's only remaining items are the §8 "not-now"
candidates, each **blocked on a prerequisite product/commercial/index decision** that must not be fabricated
autonomously:

| # | Item | Blocking decision (must precede a safe build) |
|---|---|---|
| 2 | Async control plane (requeue/cancel/retry across subsystems) | Product semantics — easily slides into action buttons; needs scope approval. |
| 3 | OCR failed/running index | A `Document.metadata` index-strategy decision (btree expression vs GIN-containment selectivity). |
| 4 | Mail ProcessedMail ERROR backlog | A `ProcessedMail` index + retention + PII-display decision. |
| 5 | Real licensing (commercial entitlement) | A commercial product-line decision, not a small closed loop. |

There are **zero** code-level unfinished markers (no TODO/FIXME, no disabled/skipped tests) across
`ecm-core/src/main` + `ecm-frontend/src`. So after this slice, **all ready/ungated development is complete**;
the remainder is **decision-gated**, awaiting the operator's product/commercial/index calls.

## 8. Conclusion

The cross-subsystem failure inventory first-cut is built to the taskbook's deliberately-narrow §4/§7 scope,
under both §5A guards, mirroring the established observability cadence, with the load-bearing PII guard made a
build-failing assertion. **CI is 7/7 green (run `28384980621`) and the feat landed on `main` as `df7ec2c` (#48).**
The remaining plan items (#2–#5) are decision-gated, not engineer-buildable, and are recorded above with their
specific blocking decisions for the operator's return.
