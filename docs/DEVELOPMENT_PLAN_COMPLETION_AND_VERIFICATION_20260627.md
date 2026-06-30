# Development Plan — Completion & Verification Status (2026-06-27)

> **Superseded (2026-06-29):** the one remaining buildable item — the **cross-subsystem failure first-cut** — is
> no longer held. It was ratified (§7), built, CI-verified **7/7 green**, and merged as **#48 (`df7ec2c`)**, with the
> authoritative closeout in **`DEV_AND_VERIFICATION_CROSS_SUBSYSTEM_FAILURE_INVENTORY_20260629.md` (#49)**. The body
> below remains an accurate **2026-06-27 point-in-time snapshot** (it predates the operator's autonomous-development
> mandate that lifted the "hold"); only the §8 decision-gated items (#2–#5) now remain.

- Date: 2026-06-27
- Scope: the Athena ECM observability / governance development cadence — completion status, unfinished-item
  inventory, and verification.
- Method: hand-verified, grounded inventory (open PRs, code markers, all June taskbooks/verification docs, recent
  commits). An automated multi-agent inventory sweep was run first but its synthesis returned a degenerate stub, so this
  document is the **hand-verified** inventory, not the workflow's output.
- State: **All ready / ungated development is COMPLETE.** The one remaining buildable item (the cross-subsystem failure
  first-cut) is **gated on owner actions** (merge #46 + ratify §7) and an explicit "hold" instruction; the §8 "not-now"
  items each need a prerequisite decision. **Nothing safe is left to build in this pass** — and that is a verified
  finding, not an omission.

## 1. Evidence (lead with the proof, not the verdict)

A thorough grounded scan of the plan surface:

- **Open PRs: only `#46`** (cross-subsystem Day-1 taskbook), CI **7/7 green**, awaiting merge. No other open PR.
- **Code markers: zero.** `grep TODO|FIXME` over `ecm-core/src/main` + `ecm-frontend/src` → **0**; **no** `@Disabled`/`@Ignore`
  backend tests; **no** `.skip(`/`xit(`/`xdescribe(` frontend tests. There is no half-finished code to complete.
- **Doc markers:** across every June observability doc, the **only** "pending build" marker is the cross-subsystem
  taskbook §4 — *"Recommended first-cut slice … for ratification, not yet built."*
- **All other lines closed** (taskbook → feat → verification, all merged): queue-backlog, scheduler-run,
  storage-capacity, Microsoft-OAuth-revoke, sensitive-data-logging-audit.

→ **Verdict:** the development plan is at a **clean, deliberate stopping point**. The one open item is owner-gated.

## 2. Completed development (the cadence) — with verification

Each line shipped as the established three-leg cadence (Day-1 taskbook → Day-2 implementation → verification doc),
each leg a separate merged PR with CI green.

| Line | Taskbook | Implementation | Verification | CI |
|---|---|---|---|---|
| Queue Backlog observability | #43 `c61674e` | #44 `1b7350e` | #45 `f641e73` | 7/7 (#44 run `28255519448`; #45 green) |
| Scheduler-run observability | #40 `424b106` | #41 `5739867` | #42 `5a2700b` | 7/7 (per its verification doc, run `28214250818`) |
| Storage capacity observability | #34 `dff6949` | #35 `1d1f029` | #36 `7bf8136` | 7/7 (per its verification doc, run `28148733196`) |
| Microsoft OAuth revoke | #37 `131bcc4` | #38 `7332308` | #39 `4641788` | merged & closed |
| Sensitive-data logging audit | phase1 (20260623) | — (audit/remediation) | `6f84e47` close-out (20260624) | green at close-out |
| **Cross-subsystem failure / dead-letter** | **#46 `1a96a36` (green, awaiting merge)** | **— (deferred)** | **— (deferred)** | #46 run `28295940494` 7/7 |

`main` is at `f641e73`; `#46` is the only delta in flight.

## 3. Unfinished-item inventory (answers: what's unfinished / parallelizable / safe to aggregate-build now)

"Safe to build now" = concrete + in-scope + **not** owner-gated and **not** blocked on a prerequisite decision.

| # | Item | Status | Parallelizable | Safe to build now | Why |
|---|---|---|---|---|---|
| 1 | Cross-subsystem failure **first-cut** (taskbook §4: preview dead-letter count + failure-triage hub, reusing `QueueBacklogObservabilityService`) | ratified-pending / **not built** | **Yes** — backend (service + read-only endpoint + tests) ∥ frontend (card + tests) ∥ the preview dead-letter count accessor | **No** | Gated by **three** owner-side blocks (see §4): #46 unmerged, §7 unratified, explicit "hold" |
| 2 | Async **control plane** (requeue/cancel/retry across subsystems) | deferred-not-ready (§8) | independent | **No** | Heavy product semantics; explicitly "not now" |
| 3 | OCR **failed/running** index (a `Document.metadata` btree/expression index) | deferred-not-ready (§8) | independent | **No** | Needs a `Document.metadata` index-strategy decision first |
| 4 | Mail **ProcessedMail error backlog** (indexed ERROR inventory) | deferred-not-ready (§8) | independent | **No** | Needs `ProcessedMail` index + retention + PII-display decisions first |
| 5 | **Real licensing** (commercial entitlement) | deferred-not-ready (§8) | independent | **No** | Commercial product line, not a small closed loop |
| 6 | Code-level unfinished work (TODO/FIXME/disabled tests) | **none found** | — | — | Zero markers across backend + frontend |

So the **only** genuinely buildable item is #1, and it is **owner-gated**, not engineer-gated. Items #2–#5 are
independent of each other (could parallelize) **but each is blocked on a decision**, so none is buildable yet. There is
no hidden #6 work.

## 4. Why nothing is built in this pass (honest, and not a dodge)

Building the cross-subsystem first-cut (#1) now would require clearing **three independent blocks, none of which an
engineer can clear unilaterally**:
1. **Cadence/sequence:** every shipped line ran taskbook → **merge** → feat → **merge** → verification. PR #46 (the
   Day-1 taskbook) is **not merged**. Building Day-2 on an unmerged Day-1 is out of sequence with every line shipped.
2. **§7 ratification** is explicitly the **owner's** decision; the taskbook correctly left it OPEN.
3. **Explicit instruction** to hold the build until the narrow gap is accepted.

Items #2–#5 each need a **prerequisite decision** (index strategy / retention + PII / product semantics / commercial)
before they are a safe closed loop. And there are **no code TODOs / disabled tests** to finish. Therefore "complete all
development" resolves to: **all ready + ungated development is complete; the remainder is owner-gated, not
engineer-gated.** Force-building now would break the cadence and contradict the hold instruction.

(Noted but deliberately **not** acted on: `RecordsManagementController` computes its failed governed-transfer count via
`replicationJobRepository.findAll().stream()` — a full-table in-memory scan. It is a pre-existing wart in a closed area;
refactoring it unprompted would be exactly the "don't auto-pick more work" the project guidance warns against. Flagged
for a future decision, not changed here.)

## 5. Ready-to-go build plan (the moment §6 unblocks #1)

When #46 merges and §7 is ratified, the §4 first-cut is one parallel fan-out away — staged here so it is a single word
to start:
- **Backend** (one unit): a read-only aggregator that reuses `QueueBacklogObservabilityService` for the already-cheap
  transfer/mail counts and adds a **preview dead-letter count** via `PreviewDeadLetterRegistry` (`list`/`itemCount`,
  O(1)); a `GET /api/(v1/)admin/failure-inventory` endpoint (`hasRole('ADMIN')`, read-only); `*SecurityTest` +
  service/shape tests. Under both §5A guards: **index-first** (no `ProcessedMail`/`Document.metadata` scan) and
  **PII-safe** (count + timestamp + type/category only; raw failure text stays in the existing ADMIN-gated deep surfaces).
- **Frontend** (parallel unit): an AdminDashboard "Failure Inventory / triage" card — per-source counts + `available`
  flag + **links out** to the deep surfaces (preview diagnostics, mail diagnostics, transfer jobs); failure-isolated
  fetch; render + isolation tests.
- **OCR / ProcessedMail-ERROR**: remain excluded (unindexed / PII) → link out only.

## 6. Verification of this status (reproducible)

```bash
cd ~/Downloads/Github/Athena
gh pr list --state open --json number,title                       # → only #46
grep -rIn -E "TODO|FIXME" ecm-core/src/main ecm-frontend/src | wc -l   # → 0
grep -rIn -E "@Disabled|@Ignore" ecm-core/src/test                # → (none)
grep -rIn -E "\.skip\(|xit\(|xdescribe\(" ecm-frontend/src        # → (none)
grep -rIn "not yet built" docs/*2026*.md                           # → only the cross-subsystem §4 marker
git log --oneline -1                                               # → f641e73 (#45)
```
CI evidence: queue-backlog `#44` run `28255519448` 7/7; cross-subsystem taskbook `#46` run `28295940494` 7/7; the
scheduler-run / storage / OAuth lines record 7/7 in their own verification docs.

## 7. Conclusion

The development plan is **complete to the extent it can be without owner action**: five lines fully shipped and verified,
the sixth (cross-subsystem failure) landed at Day-1 (PR #46, CI-green) with Day-2 deliberately deferred. The inventory is
conclusive — only #46 is open, there are **zero** code-level unfinished markers, and the single remaining buildable item
is gated on **merge + §7 ratification** (owner) plus an explicit hold. The §8 candidates each await a prerequisite
decision. The one decision that unblocks further development is the owner's: **merge #46 and ratify §7 (then the §4
first-cut builds in a parallel backend/frontend fan-out), or continue to hold.**
