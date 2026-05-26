# Product Capability Discovery — Refresh 4 (read-only)

Date: 2026-05-26 · Supersedes the ranking in `docs/PRODUCT_CAPABILITY_DISCOVERY_REFRESH3_20260525.md`.

## Headline (honest)

**Pause is now the strongly-recommended default — the residual queue got thinner, not richer.** Refresh 3's single least-weak candidate (**saved-search results CSV export, C1**) has since been **delivered** (commit `b3ab3d8`, `SavedSearchController:85-93` `/{id}/export` + `SavedSearchesPage:160` `exportResultsCsv`). A fresh sweep (REST controllers ↔ frontend callers, list-views without export/bulk, and a TODO/FIXME/"not implemented" demand-trail search) surfaced **no new operator-valued gap** and **no captured demand**. Recommendation: **pause auto-picking; wait for an operator/product signal.** Building further would be continuity-for-its-own-sake.

## Method (proportionate — Refresh 3 already did the broad fan-out the day before)

- Verified the status of every Refresh 3 candidate (delivered / parked / still-weak).
- Fresh Explore sweep for "backend ready but UI missing" / "view but no export" / "single op but no bulk" with **real** value, explicitly excluding the delivered + catalogued sets.
- Searched code + recent docs for a **captured-demand trail** (TODO/FIXME/"not implemented"/"coming soon"/operator complaint). **None found.**
- Explicitly excluded the just-closed production-hardening line (`docs/HANDOFF_HARDENING_20260526.md`): owner/ops cutover work is delivery readiness, not product capability discovery.

## Status of Refresh 3 candidates (re-verified)

| Ref | Candidate | Refresh-4 status |
|---|---|---|
| **C1** | Saved-search results CSV export | ✅ **DELIVERED** (`b3ab3d8`) — was the least-weak; now built |
| C2 | Workflow task bulk reassign | **PARKED** (commit `06cbbd6`: "value signal is inferred, not requested"); single-reassign primitive exists (`WorkflowController:393`), bulk intentionally not built |
| C3 | Bulk tag/category deletion (cascade) | Still **deferred** — risky cascade semantics, needs policy + admin gate; no demand |
| C4 | Bulk share-link lifecycle (extend expiry / raise limit) | Still **deferred** — low complexity but niche; `ShareLinkController.updateShareLink:119` single only |
| C5 | Bulk comment deletion (+ redaction) | Still **deferred** — weak; single-delete suffices outside high-volume moderation |
| (carried) | Bulk checkout/checkin; MS OAuth provider-side revoke | Unchanged — large/contingent-on-customer-signal tracks, not small slices |

## Fresh scan result

- **No backend-ready-but-UI-missing gap** with genuine operator value. Bulk pattern is mature (`BulkOperationController`: move/copy/delete/restore/metadata/share-links); export pattern is mature (analytics audit export + async, version-history CSV, saved-search CSV, search-preview CSV).
- **No new "view but no export"** worth a slice.
- **Zero captured-demand signals** in code or recent docs.
- **Alfresco service-contract 对标** (separate doc: `docs/ALFRESCO_SERVICE_CONTRACT_PARITY_20260526.md`): the `com.ecm.core.alfresco.*` shim is unused, self-contained Alfresco-*shaped* scaffolding (no `org.alfresco` dep, no consumer — 4-way verified); completing it = recreate Foundation API (forbidden by `CLAUDE.md`). C4's two stubs live in unconsumed code (blast radius lower than the matrix implies; matrix unchanged). The consumed Alfresco-interop contract is **CMIS** (already roadmap-complete). No demand-backed slice; only a hygiene item (document-vs-delete the shim — owner's call) and a contingent Foundation-API-drop-in track if a real customer signal appears.

## Recommendation

1. **Pause product auto-picking. Wait for an operator/product/customer signal.** This is the honest call after the delivered operator-workflow slices through C1.
2. If a low-risk win is *insisted* despite no demand, the least-weak **remaining** item is **C4 (bulk share-link lifecycle update)** — lowest complexity of the residual set — but its value is inferred, not requested; I do **not** recommend opening it without a signal.
3. **Triggered tracks** (open only on signal): MS OAuth provider-side revoke (Microsoft Entra customer), bulk checkout/checkin (explicit batch-editing workflow need), C3 (a real category-restructure need).

## What this discovery does not commit to

- No code change; this doc is left uncommitted pending gate review (per cadence).
- No brief opened. C2–C5 each still need a formal read-only brief + gate adjudication **only if** a signal arrives.
- This is not a claim that the product is "feature complete" — only that **code inspection alone has stopped yielding demand-backed gaps**; real gaps will now come from operator requests, not scans.

## Verification (this discovery)

- C1-delivered confirmed in code (`SavedSearchController:85-93`, `SavedSearchesPage:158-161`, commit `b3ab3d8`) — not just asserted.
- Exclusion zone honored (no RM preset/scheduled-report, no hardening/cutover, no test/guard/logging, no storage-routing).
- `.env` remains untracked/untouched; current worktree delta is this doc only.
