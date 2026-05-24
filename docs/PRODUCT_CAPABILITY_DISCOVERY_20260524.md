# Product Capability Discovery — Read-Only Scoping

Date: 2026-05-24

## Purpose

Five defensive / architecture tracks closed in May 2026 (frontend guard, backend contract, logging audit, Microsoft OAuth revoke, ADR-001/003 storage routing). Gate asked for the next track to deliver **user-visible value**, not more cleanup. This document is the read-only scoping pass that surfaces product-capability candidates.

## Exclusion zone (verbatim)

Per gate brief, the following surfaces are explicitly **out of scope** for the next track:

- Frontend service response-shape guard (closed `0741087`).
- Backend response-contract test (closed `d266cc9`).
- Sensitive-data logging audit (closed `fab096a`).
- Microsoft OAuth provider-side revoke (closed Option N `5d97950`).
- ADR-001 storage routing / tenant isolation (closed Accepted: Option A `099c9ca`).
- ADR-003 content-at-rest encryption (Deferred, same commit, not in flight).
- Any candidate whose primary deliverable lives in `ecm-core/src/test/` or `*.test.ts`.

## Priority directions scanned

Gate-named: site invitations, records management, mail automation, smart/virtual folders, legal holds. Each scanned for partial-shipped state, "future work" doc trails, and operator-visible pain points.

## Discovery method

- Explore agent fan-out for `docs/*CLOSEOUT*`, `*TODO*`, `*FOLLOWUP*`, `*HANDOFF*`, `*ROADMAP*`, `git log --oneline -150`, plus the named services / controllers / pages for `TODO:` markers and disabled UI controls.
- 6 candidates returned. Each evaluated against gate exclusion list, session memory (especially `project_rm_preset_delivery_closeout`), and the "user-visible value" bar.
- Three promoted to Top 3; three carried as honest mentions with their respective concerns.

## Top 3 candidates (ranked)

### 1. Bulk site invitation with per-row send-tracking — `Operator workflow`

**One-line pitch:** Operators paste a list of emails (CSV / newline / comma-separated) and create N site invitations in a single action; each invitation still individually shows SENT / FAILED / resend per the existing send-tracking infrastructure.

**User value:** A typical "onboard 20 new team members" workflow becomes one dialog instead of 20 separate clicks. Each row still gets the full live-send + retry telemetry that shipped on 2026-05-07. Failure visibility per row is preserved — bulk does not sacrifice diagnostic granularity.

**Evidence of partial-shipped state:**

- `SiteInvitationController.java:39-47` — single-email `invite(siteId, InviteRequest)` endpoint; no batch endpoint exists.
- `SiteInvitationsPage.tsx:154-191` — `CreateInvitationDialog` accepts exactly one `inviteeEmail` per open.
- `SiteInvitation` entity already has full send-tracking columns (`lastSendStatus`, `lastSendError`, `sendAttemptCount`) per migration `092-add-site-invitation-send-tracking.xml`; the backend per-row send semantics are stable.
- `docs/SITE_INVITATION_RESEND_OPERATOR_RUNBOOK_20260507.md` §6 names a bulk-resend operator flow on the frontend (multi-select existing failed invitations and resend), confirming the multi-row UI pattern is already familiar to operators. **Bulk-create does NOT exist.**

**Affected surfaces:**

- Backend: new `POST /api/v1/sites/{siteId}/invitations/bulk` on `SiteInvitationController`; service method `inviteBulk(siteId, List<InviteRequest>)`. Reuses existing `invite()` body per row inside a single transaction.
- Frontend: replace single-email `inviteeEmail` text field with a textarea (newline / comma-separated); parse + dedup client-side; show per-row outcome in a summary toast (e.g., "18 sent, 2 failed — see Resend tab"); update the existing send-tracking row list afterwards.
- Schema: none. Each invitation is still one row.

**Estimated size:** < 1 person-day. Backend endpoint is a loop with transactional boundary; frontend is textarea + regex split + outcome summary.

**Risk:** Low. Single-email send is stable; bulk is a thin loop. Rate-limit risk is no worse than 20 single-invite operator clicks today (SMTP latency is the natural throttle).

**Why it fits "user value, not defense":** Direct operator ergonomics on a workflow operators perform regularly. No test / guard / contract surface.

**Memory cross-check:** No relevant feedback memory flags this surface.

### 2. Legal-hold bulk-apply + structured release-reason capture — `Compliance + operator workflow`

**One-line pitch:** (a) Operators can create a legal hold and populate it with a node-list in a single dialog (today: create-then-add-items in two separate UX steps). (b) When releasing a hold, operators must choose a structured release reason (enum + optional comment), which is persisted and auditable for compliance reporting.

**User value:** Two distinct wins bundled. The bulk-apply half cuts the "create hold → open hold → add nodes" sequence to one dialog. The release-reason half satisfies compliance/audit reviewers who currently get only free-text comments (often blank) on hold releases — a real audit deficiency for regulated tenants.

**Evidence:**

- `LegalHoldService.java:73-96` — `addItems(holdId, AddHoldItemsRequest)` already accepts a list of node IDs — the bulk-add backend exists. **Bulk-create-with-items does not.**
- `LegalHoldService.java:107-117` — `releaseHold(holdId, ReleaseLegalHoldRequest)` accepts optional `comment` only; no structured reason.
- `LegalHoldsPage.tsx:138-200` — `AddNodesDialog` accepts comma / newline-separated node IDs; the bulk-row UX pattern is already operator-ready.
- Schema: `release_comment` is free-text in `legal_hold` table; no `release_reason` enum column.

**Affected surfaces:**

- Backend: new `POST /api/v1/legal-holds/bulk-create-with-items` endpoint, or extend `createHold` to accept an optional `nodeIds` list. New `HoldReleaseReason` enum (e.g., `LITIGATION_ENDED`, `SCHEDULED_DISPOSITION`, `REQUEST_BY_REQUESTOR`, `OTHER`). Migrate `legal_hold.release_comment` → adds `release_reason` column (NOT NULL on new releases, NULL on legacy rows).
- Frontend: refactor `CreateLegalHoldDialog` to optionally accept a node-list (mirror the `AddNodesDialog` pattern); refactor `ReleaseLegalHoldDialog` to require a reason dropdown + optional comment.
- Schema: one liquibase migration (`0XX-add-hold-release-reason.xml`) adding the new enum column.

**Estimated size:** Small (< 1 person-day) for release-reason capture alone. Medium (1-2 person-days) for the full bundle including bulk-create UI.

**Risk:** Low. Release-reason is additive (legacy rows null-tolerant); bulk-create reuses an existing service method. Audit risk is **positive** (more structured data, not less).

**Why it fits "user value, not defense":** Compliance teams gain real audit-trail value; operators save UX steps. Touches a feature gap on a deployed-and-used surface.

**Memory cross-check:** No relevant feedback memory flags this surface.

### 3. Bulk record declaration with category assignment — `RM operator workflow`

**One-line pitch:** Operators multi-select N documents in the records browser and declare them all as records (optionally assigning the same record category) in one action. Per-document declare audit events are still logged individually; failure handling is per row.

**User value:** A high-volume declare workflow ("all 2023 contracts in this folder" / "every email imported from this mailbox last quarter") goes from N clicks to one. RM is one of the surfaces where operators routinely process hundreds of documents at once today.

**Evidence:**

- `RecordsManagementService.java:501-543` — `declareRecord(nodeId, DeclareRecordRequest)` is single-document.
- `RecordsManagementService.java:545-568` — `assignRecordCategory(nodeId, categoryId)` is also single-node.
- `RecordsManagementController.java` — no endpoint that accepts a list of node IDs for bulk declare.
- `RecordsManagementPage.tsx` — no multi-select + bulk-declare button surface.
- The bulk pattern exists elsewhere in the system (e.g., legal-hold `addItems`, bulk-operation service), so backend ergonomics are not novel.

**Affected surfaces:**

- Backend: new `POST /api/v1/records/bulk-declare` endpoint; service method `declareRecordsBulk(List<DeclareRecordBulkRequest>)`. Each entry: `nodeId` + optional `categoryId` + optional `comment`. Transactional boundary per-row (one document's failure does not roll back others).
- Frontend: row-level checkbox multi-select on the records browser; "Declare selected" toolbar action; dialog modal for shared `categoryId` + `comment`; progress toast summarising "N declared, M failed (see audit log)".
- Schema: none. Reuses `rm:record` aspect + existing record-category assignment.

**Estimated size:** Medium (1-2 person-days). Backend endpoint + tests + frontend multi-select are all small individually; UI integration with the existing records browser is the main effort.

**Risk:** Low. Declare is append-only per document — no overwrite, no deletion. Audit log gets N events not 1, which is the intended semantic.

**Why it fits "user value, not defense":** Direct ergonomics on a real operator workflow. Not a polish of preset-delivery / not a polish of report scheduling.

**Memory cross-check:** Borderline against `project_rm_preset_delivery_closeout` ("PR-95..121 declared done 2026-04-23; don't auto-pick more polish, open a new capability"). The relevant test for this memory is whether bulk-declare is *RM preset delivery polish* (forbidden) or *a new capability on the RM surface* (allowed). Bulk-declare is on the declaration workflow, **not** the preset-delivery workflow; preset delivery handles already-declared records' scheduled reports, while bulk-declare handles getting documents into the records system in the first place. These are distinct subdomains; bulk-declare qualifies as new capability. Worth surfacing to the gate for explicit confirmation before opening.

## Honest mentions (not in Top 3)

### A. Email notification on scheduled RM preset delivery — **carries memory risk**

Operators currently rely on in-app notifications for scheduled RM preset delivery success/failure. Email notification preference keys exist in the UI (`RecordsManagementPage.tsx:88-91, 362-365`) but are not wired to a backend email-channel implementation.

**Why not Top 3:** Memory `project_rm_preset_delivery_closeout` explicitly warns "PR-95..121 declared done 2026-04-23; don't auto-pick more polish on RM preset delivery". This candidate sits exactly on that surface — RM preset delivery polish. Could be promoted only after the gate explicitly waives the memory warning. If waived, it is a clean ~1-2 person-day medium slice with a clear value story. Recommend pivoting only on gate's say-so.

### B. Mail rule preview-export to folder

Mail rule preview (`MailFetcherService.java:418-571`) returns matched messages but operators cannot export the preview result as a one-shot or recurring folder delivery for later review.

**Why not Top 3:** Value proposition is narrower than #1-#3. Operators *can* already see the preview in-UI today; the missing piece is durability (export). Niche enough that without a specific operator request, the work-to-value ratio is lower than the Top 3. Promotable if a customer signal surfaces.

### C. Smart-folder content refresh + query-criteria editor

Smart folder backend (`Folder.smart`, `Folder.queryCriteria`) exists with create/update API surfaces (`FolderController.java:339-383`, `:50-51`, `:130-131`); `CreateFolderDialog.tsx` exposes a smart-folder toggle plus basic query / pathPrefix fields. **But** there is no dedicated post-create editor and no "Refresh contents" affordance.

**Why not Top 3:** The discovery could not verify whether smart-folder query evaluation is materialized or live-on-read; either implementation choice would change the slice scope substantially. A small read-only follow-up (one Read of `FolderController.getContents` + `FolderService` smart-folder branch) would resolve this before committing to a slice. Currently the candidate carries unresolved scope risk. Promotable after that follow-up read.

## Recommendation framework

If picking exactly one for the next track:

| Pick | When |
|---|---|
| **#1 Bulk site invitation** | If the priority is fastest visible win on a well-understood surface. < 1 pd, low risk, clean operator ergonomics. **Default recommendation.** |
| **#2 Legal-hold bulk-apply + release-reason** | If compliance / audit signal is the dominant driver. Two-in-one slice (ergonomics + structured audit). 1-2 pd. |
| **#3 Bulk record declaration** | If RM is the current product focus AND the gate explicitly confirms it does not collide with the `project_rm_preset_delivery_closeout` memory ("new capability, not polish"). 1-2 pd. |

For parallel work: #1 and #2 share no code surface and parallelize cleanly. #3 touches the records browser page which is heavily integrated with RM read endpoints; would need to land sequentially with any future records-browser changes.

## What this discovery does not commit to

- No track is opened by this document.
- No code, test, frontend, schema, or `.env` change touched.
- No commits made by this discovery itself (gate decides commit + push).
- The Top 3 candidates each still need a formal worker brief before code work begins. Brief format mirrors prior discovery-to-brief flow: read X, output to Y, must answer 1-N, OOS list.

## Verification (this discovery)

```bash
git status --short                           # M .env + this discovery doc only
git diff --check -- . ':!.env'               # passes
git diff --stat -- 'ecm-core/src/main/java/' # empty
git diff --stat -- 'ecm-frontend/'           # empty
git diff --stat -- 'ecm-core/src/test/'      # empty
git diff --stat -- 'ecm-core/src/main/resources/' # empty
```

Confirmed at time of writing.
