# Product Capability Discovery — Refresh 3 (read-only)

Date: 2026-05-25
Supersedes the ranking in `docs/PRODUCT_CAPABILITY_DISCOVERY_REFRESH2_20260525.md` (both its Top 2 delivered).

## Headline (honest)

**The clearly-shaped operator workflow gaps are exhausted.** Seven consecutive slices closed the obvious "bulk a frequent single op" / "view-but-not-export" gaps. What this scan surfaces is now **incremental** — more CSV-export buttons and niche bulk mutations — **with no captured operator demand in the repo** (no TODO/FIXME trail, no operator-complaint signal in git history). Notably, the scan's first-guess candidate (audit-history export) **turned out already built**.

Recommendation up front: this is a reasonable point to **pause auto-picking and wait for an operator/product signal** rather than build for continuity. If a small low-risk win is still wanted, the least-weak candidate is **Saved-search results CSV export** (§C1) — but its value is inferred, not requested.

## Status of refresh-2 — delivered

| Candidate | Closed by |
|---|---|
| Bulk document share-link creation | `310656d` / CI `5d96e8e` |
| Document version-history CSV export | `313de9a` / CI `17c6e33` |

## Exclusion zone (confirmed not proposed)

All delivered slices (site-invite, legal-hold bulk-apply, bulk record-declare, mail preview export, smart-folder editor, version CSV export, bulk share-link create) + RM preset/scheduled-report delivery (FORBIDDEN) + defensive/test-only/guard/logging surfaces + `*/test/` deliverables.

## A. Carried-direction verdicts (re-assessed with evidence)

### 1. Bulk share-link MANAGEMENT (multi-select deactivate/reactivate/extend/delete) — **DEFER (weak signal)**
- Backend: `ShareLinkController` single-token deactivate (`:141`), reactivate (`:158`), update (`:119`), `/share/admin/all` list — no batch endpoint.
- Frontend: `AdminDashboard.tsx` `ShareLinksAdminPanel` lists links with **per-row** action icons; **no checkbox/multi-select**.
- The table could host multi-select cheaply, but operators manage a bounded set of links and there's no evidence of batch-management volume. Lower value than the delivered slices. **Not recommended without a concrete signal.**

### 2. Bulk checkout/checkin with batch upload — **DEFER (high risk, large, separate track)**
- Backend: `DocumentController` checkout/checkin/working-copy are single-document (`:301`–`:435`); no bulk.
- Stateful (locks, working copies, version baselines) and the checkin half needs file-to-document matching — large, error-prone, not a small slice. No captured multi-doc-edit demand. **Only as its own track if content-authoring becomes an explicit product direction.**

## B. Correction to the scan's first guess

**Audit-history CSV export is ALREADY DELIVERED** — `AnalyticsController` exposes `/audit/recent` (`:105`), `/audit/export` (`:137`), and a full `/audit/export-async` subsystem with task list/summary/cancel/download (`:153`–`:266`). It is **not** a gap. (Flagged because the scan initially ranked it #1 on a guess that no audit export existed.)

## C. Candidates (least-weak first; none has captured demand)

### C1. Saved-search results CSV export — `view-but-not-export` — least-weak
- **Pitch:** export the results of a saved search to CSV (one-shot), mirroring the just-shipped version-history CSV pattern.
- **Value (inferred, not requested):** compliance/discovery teams re-run saved searches; exporting results for offline review is plausible.
- **Evidence (verified):** `SavedSearchController` has save (`:25`), list (`:32`), templates (`:38`), get (`:54`), delete (`:67`), execute (`:74`), smart-folder (`:86`) — **no export**. `SearchController` CSV export exists **only** for preview-diagnostics (`/preview/queue-failed/dry-run/export` `:321`), not general/saved-search results.
- **Surfaces:** Backend new `GET /api/v1/search/saved/{id}/export` reusing the established CSV attachment-response + escaping pattern (`AnalyticsController`/`BulkOperationController`); frontend "Export CSV" button on the saved-search list. **Scheduled/recurring export is OUT — that's a scheduler track, not this slice.** Schema: none.
- **Size:** Small (<1pd). **Risk:** Low (read-only). **Closeout:** none.

### C2. Workflow task bulk reassign (user A → user B) — `operator workflow`, needs a pre-check
- **Pitch:** reassign all pending tasks from one assignee to another (offboarding/leave coverage).
- **Value (inferred):** plausible ops scenario; reduces per-task clicks.
- **Evidence (verified):** task inbox exists — `WorkflowController` `/tasks/my` (`:133`), `/tasks`/`/tasks/inbox` with `assignee` filter (`:142`). **Open pre-check before promoting:** confirm a single-task reassign/claim mutation exists to reuse (the grep surfaced inbox reads + approval submits, not a reassign endpoint). If no single reassign exists, this grows (new assignment mutation + Flowable semantics) and is no longer small.
- **Size:** Small-medium (if single reassign exists) / Medium (if not). **Risk:** Low-medium. **Closeout:** none.

### C3. Bulk tag/category deletion with cascade policy — `governance`, medium risk
- Single tag delete/merge exist (`TagController` `:101`,`:108`); `BulkMetadataDialog` adds tags in bulk but cannot remove. A bulk-delete with merge/orphan/cascade policy is governance cleanup — **niche, and cascade-delete is risky** (must be admin-gated, needs policy design). Defer unless a category-restructure need is real.

### C4. Bulk share-link lifecycle update (extend expiry / raise access limit) — niche
- `ShareLinkController.updateShareLink` (`:119`) carries `expiryDate`/`maxAccessCount`; a bulk variant is low-complexity but **niche** (admins rarely adjust links post-create). Lower priority than C1.

### C5. Bulk comment deletion (+ redaction) — weak signal
- `CommentController` has single add/get/delete; no bulk. Single-delete is usually sufficient; only high-volume moderation justifies bulk, and redaction-vs-hard-delete needs policy. **Weak.**

## D. Recommended ranking

Honest: **no candidate strongly clears the bar.** If forced to order by value-per-risk with verified evidence:

1. **Pause / await operator signal** — the principled default. Continuing risks building for continuity.
2. If a slice is still wanted: **C1 Saved-search results CSV export** — cleanest, smallest, lowest-risk; reuses the version-CSV pattern. One-shot only (no scheduler).
3. **C2 Workflow task bulk reassign** — only after the single-reassign pre-check (§C2); real ops value if it stays small.
4. C3 / C4 / C5 and both carried directions — defer (niche / risky / weak signal).

## E. Honest mentions / notes

- No `TODO`/`FIXME`/"not implemented" operator-facing trail in `ecm-core/src/main/java` or `ecm-frontend/src` — little documented debt to mine.
- Bulk-op + CSV-export patterns are well-established (`BulkOperationController`, `AnalyticsController` audit export) — any of the above would reuse them, which is also why they're low-differentiation.
- The recurring honesty caveat from refresh-2 holds and is stronger now: value framing is plausibility, not captured demand.

## F. What this discovery does not commit to

- No track opened; no code/test/frontend/schema/`.env` change; no commits by this doc itself (gate decides).
- C1/C2 still need a formal read-only worker brief + gate adjudication before any code; C2 needs the single-reassign pre-check first.

## G. Verification (this discovery)

```bash
git status --short                              # M .env + this doc only
git diff --stat -- 'ecm-core/' 'ecm-frontend/'  # empty
```
