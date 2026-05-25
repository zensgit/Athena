# Product Capability Discovery — Refresh 2 (read-only)

Date: 2026-05-25
Supersedes the rankings in `docs/PRODUCT_CAPABILITY_DISCOVERY_20260524.md` (and its same-day refresh section), whose candidates are all delivered.

## Status of the prior refresh — fully delivered

| Candidate | Delivered by |
|---|---|
| Bulk site invitation | `e7f76c3` |
| Legal-hold bulk-apply + structured release-reason | `32381d8` (migration 094) |
| Bulk record declaration with category assignment | `0a597d3` (+ drain polish `4bc9856`) |
| Mail rule preview export to folder | `ae8a41b` |
| Smart-folder post-create query editor | `1c6e85e` |

The 2026-05-24 ranking (incl. its two promoted honest mentions) is exhausted. This refresh re-scans for fresh user-visible workflow gaps.

## Method & honesty caveat

Explore fan-out over `git log -80`, `docs/*CLOSEOUT/FOLLOWUP/HANDOFF/TODO/ROADMAP/DISCOVERY*`, `TODO/FIXME` sweeps, and a create-vs-edit / view-vs-export / single-vs-bulk sweep across documents, sharing, versions, saved searches, tasks/workflow, legal holds, records, mail, tenant/quota, audit, CMIS. Every gap below is grep-verified (file:line). **No captured operator signal exists in the repo** — value framing is "plausible workflow," pattern-matched to the surfaces operators already use, not evidence that operators have requested it. (Matches the honesty posture of the C′ discovery.)

## Exclusion zone (confirmed not proposed)

Delivered (above) + RM preset delivery / scheduled report / preset email (FORBIDDEN closeout, PR-95..122 + lane `4653f3e`) + defensive/test-only/guard/logging surfaces + anything whose deliverable lives in `*/test/` or `*.test.ts(x)`. **Bulk transfer-target / replication-definition submission stays an honest-mention only** (gap is real but no operator-volume signal — targets are configured once per remote site), same verdict as the last refresh.

## Top 2 (ranked)

### 1. Bulk document share-link creation — `Operator workflow` (DEFAULT)

**Pitch:** multi-select N documents and create share links in one action, with shared expiry/password/permission defaults (per-link override optional); per-row outcome (created / failed).

**Plausible value (no captured signal):** external-distribution batches (send a set of related docs to an auditor/partner/requestor) currently require N separate share-link creations. This is the same "bulk a frequently-used single op" shape as the three delivered bulk slices.

**Evidence (verified):**
- `ShareLinkController.java:40` — single-node create `POST /api/v1/share/nodes/{nodeId}`; deactivate/reactivate by token (`:141`, `:158`). No bulk route.
- `BulkOperationController.java:35` (`/api/v1/bulk`) exposes only `move/copy/delete/restore/metadata` (`:48-82`) — no share.
- Frontend: no multi-select share-link creation in the browser/search results.

**Affected surfaces:** Backend — new `POST /api/v1/bulk/share-links` looping the existing `createShareLink` per node with **per-row partial success** (mirror the delivered bulk pattern) and **the existing per-node permission check preserved per row** (the single endpoint validates the caller may share that node — do not strip it). Frontend — checkbox multi-select + "Create share links" action + shared-settings dialog + per-row outcome summary. Schema — none.

**Size:** Medium (1–2 pd). **Risk:** Low — share-link create is non-stateful (no lock/checkout/version), worst case is a per-row failure surfaced in the summary.

**Memory/closeout:** none. Not RM-specific; not transfer/replication; not test-only.

### 2. Document version-history export to CSV — `Audit / view-but-not-export gap`

**Pitch:** export a document's full version history to CSV (version label, timestamp, author, comment, size) from the version dialog.

**Plausible value (no captured signal):** a clear **view-but-not-export** gap — versions are visible in-UI but cannot be exported for offline review/archival. Audit/compliance is the plausible driver, but this is inferred, not requested.

**Evidence (verified):**
- `DocumentController.java:192/201/213/238/260` — versions, versions/paged, versions/compare, version download, revert. **No export/CSV endpoint.**
- `VersionHistoryDialog.tsx` — lists versions, no export control.

**Affected surfaces:** Backend — new `GET /api/v1/documents/{documentId}/versions/export` returning CSV; **reuse the existing CSV-export pattern** (`MailFetcherService.exportDiagnosticsCsv` precedent), do not invent a new one. Frontend — "Export CSV" button in `VersionHistoryDialog` triggering a download. Schema — none.

**Size:** Small (< 1 pd). **Risk:** Low — read-only, no mutation.

**Memory/closeout:** none. Not RM preset; not defensive.

### Ranking note (the #1↔#2 call)

Both are defensible as #1 and neither has hard signal. **#1 bulk-share** is the more workflow-shaped gap and matches the established bulk pattern; **#2 version-export** is smaller and lower-risk but more "missing button" than "workflow." Ranked by workflow-shape; flip if the gate prefers the fastest, lowest-risk win first. They share no code surface and parallelize cleanly.

## Honest mentions (not promoted)

- **Bulk document checkin/checkout with batch version upload** — high *potential* impact for high-churn content authoring, BUT medium-large (2–3 pd), **stateful** (locks, working copies, version baselines), and the checkin half needs non-trivial file-to-document matching (ZIP/filename/order). Risk and scope are sprint-sized, and the "content-team focus" signal is speculative. Promote only if content authoring is an explicit product direction, and only as its own track with testing bandwidth — not bundled.
- **Bulk share-link management (batch deactivate / extend-expiry / refresh)** — lower value than creation; revocation/extension is less frequent.
- **Document comment/discussion export to CSV** — weaker compliance driver than version export; comments are typically in-app.
- **Bulk transfer-target / replication-definition submission** — gap real, **no operator-volume signal** (once-per-remote-site config); unchanged honest-mention from the last refresh.

## What this discovery does not commit to

- No track opened; no code/test/frontend/schema/`.env` change; no commits by this doc itself (gate decides).
- Value framing is plausibility, not captured operator demand.
- Each promoted candidate still needs a formal read-only worker brief + gate adjudication before code, per the discovery → brief → gate → implement cadence.

## Verification (this discovery)

```bash
git status --short                              # M .env + this doc only
git diff --stat -- 'ecm-core/' 'ecm-frontend/'  # empty
```
