# Phase 1 P57: Reference Benchmark Gap Analysis + Next 7 Days Plan

Date: 2026-02-08

## Scope
This phase is **planning + benchmarking**, not a runtime feature change.

Inputs:
- `reference-projects/alfresco-community-repo` (ECM baseline: repository services, versioning, renditions, ACLs, audit)
- `reference-projects/paperless-ngx` (document intake baseline: mail ingestion, OCR/text extraction pipelines, tagging/categorization)

Output:
- A concrete, decision-complete 7-day delivery plan focused on:
  - Mail Automation
  - Search
  - Version Management
  - Preview / Renditions
  - Permissions
  - Audit / Reporting

## Current Athena Baseline (Phase 1)
From existing Phase 1 deliverables, Athena already supports:
- Search:
  - Accurate totals via ES total hits
  - Facets/ranges via ES aggregations
  - Spellcheck suggestions (optional)
  - ACL-filtered hits (no cross-tenant leakage)
- Versioning:
  - Paged version history
  - Major-only history filter
  - Download/restore versions
  - (P58) On-demand, bounded text diff between two versions
- Preview:
  - Persisted preview status + failure reason
  - Retry/force rebuild controls in UI
- Permissions:
  - Permission presets (Coordinator/Editor/Contributor/Consumer)
  - Permission diagnostics endpoint (why a permission is granted/denied)
- Audit:
  - Filtered audit search
  - Export presets and UI binding fixes
- Mail Automation:
  - IMAP integration
  - Rule-based ingestion + dry-run diagnostics
  - Connection summary / trigger fetch

## What Reference Projects Suggest We Should Improve Next

### A) Alfresco: Repository-Centric ECM Features
Alfresco emphasizes:
- Rich version services:
  - Revert/restore behavior and edge cases
  - Version history APIs and consistency rules
- Renditions/transforms:
  - Distinguish "unsupported" vs "failed" transforms
  - Admin tooling to re-run transforms and track reasons
- Permissions/ACL model:
  - Strong inheritance semantics
  - Clear explanations for effective permissions
- Search as a first-class feature:
  - Query + folder scope
  - Stable facet counts and diagnostics

Implication for Athena:
- Most core pieces exist, but we should tighten:
  - Preview failure taxonomy + retry behaviors
  - Folder-scoped search and filter persistence
  - Effective-permission explanation UX
  - Version compare UX beyond "current vs previous"

### B) Paperless-ngx: Intake Pipeline + Classification
Paperless-ngx emphasizes:
- Robust intake:
  - IMAP polling, deterministic idempotency, clear skip reasons
  - Ingestion states and reporting for what happened to a message/attachment
- Text extraction pipeline:
  - OCR/metadata extraction and full-text indexing loop
- User-facing organization:
  - Tags, correspondents, document types, inbox vs archive

Implication for Athena:
- Mail automation exists, but we should improve:
  - Idempotency/skip-reason reporting and troubleshooting UX
  - Pipeline observability (per rule, per mailbox, per folder)
  - Optional downstream enrichment (later phase): OCR/text extraction

## Gap List (Prioritized)

### P0 (Correctness / Reliability)
1. Preview taxonomy:
   - Ensure "preview not supported" is not treated as a generic "FAILED".
   - Avoid retry loops for unsupported types; provide a separate remediation path.
2. MIME detection robustness:
   - Reduce `application/octet-stream` misclassification where possible (extension-aware fallback).
3. Folder-scoped search:
   - Users expect "search within this folder" behavior in ECMs.

### P1 (UX + Operability)
4. Version compare UX:
   - Compare arbitrary versions (not only current vs previous).
   - Provide download-of-diff and show truncation clearly.
5. Mail troubleshooting:
   - Clear per-message ingest report (matched rule, action taken, skip reason, target node).
6. Audit discoverability:
   - Link node actions to audit queries (filter by nodeId).

### P2 (Advanced Features)
7. Document enrichment:
   - Optional OCR/text extraction pipeline integration
   - Auto-tagging / classification rules

## Next 7 Days Plan (Decision-Complete)
Each day produces:
- A scoped code change (frontend/backend as needed)
- A Playwright CLI verification
- Two docs: `DESIGN_YYYYMMDD.md` and `VERIFICATION_YYYYMMDD.md`

### Day 1: P59 Preview Unsupported Taxonomy + Safer Retry
Goal:
- Separate `UNSUPPORTED` from `FAILED` and prevent retry actions from targeting unsupported items.

Implementation:
- Backend:
  - Ensure preview generation sets `PreviewStatus.UNSUPPORTED` (not `FAILED`) when transform is not supported.
  - Normalize existing rows where `failureReason` indicates "not supported" to `UNSUPPORTED`.
- Frontend:
  - Advanced Search / Preview Queue: show `Unsupported` count and filter.
  - Update retry buttons:
    - `Retry failed` should exclude `UNSUPPORTED`
    - Add a new action: `Re-detect mime + retry` for octet-stream candidates (optional if feasible)

Verification:
- Add Playwright coverage for preview status filtering on advanced search for an unsupported type.

Docs:
- `docs/PHASE1_P59_PREVIEW_UNSUPPORTED_TAXONOMY_DESIGN_20260209.md`
- `docs/PHASE1_P59_PREVIEW_UNSUPPORTED_TAXONOMY_VERIFICATION_20260209.md`

### Day 2: P60 Folder-Scoped Search (API + UI)
Goal:
- Add `folderId` + `includeChildren` search scope.

Implementation:
- Backend:
  - Extend search endpoints to accept optional scope:
    - `folderId` (UUID)
    - `includeChildren` (boolean, default true)
  - Enforce ACL at query time (no leakage).
- Frontend:
  - When searching from a folder page, default to that folder scope; show a "Scope: This folder" chip with a clear toggle.

Verification:
- Playwright: create a folder, upload 2 files in different folders, search with scope on/off and assert counts.

Docs:
- `docs/PHASE1_P60_FOLDER_SCOPED_SEARCH_DESIGN_20260210.md`
- `docs/PHASE1_P60_FOLDER_SCOPED_SEARCH_VERIFICATION_20260210.md`

### Day 3: P61 Version Compare UX (Arbitrary Pairs)
Goal:
- Compare any two versions in history.

Implementation:
- Frontend:
  - Add a "Compare..." action that lets the user pick `from` and `to` versions.
  - Reuse P58 API for text diff; keep it lazy-loaded.
- Backend:
  - No new endpoint expected (reuse `/versions/compare`).

Verification:
- Playwright: create 3 text versions, compare v1->v3 and assert diff includes expected markers.

Docs:
- `docs/PHASE1_P61_VERSION_COMPARE_ANY_TWO_DESIGN_20260211.md`
- `docs/PHASE1_P61_VERSION_COMPARE_ANY_TWO_VERIFICATION_20260211.md`

### Day 4: P62 Mail Ingestion Traceability (Per Message Report)
Goal:
- Make it easy to answer: "Why was this email/attachment ingested or skipped?"

Implementation:
- Backend:
  - Persist per-message ingest report (minimal schema):
    - account, folder, uid, messageId, subject, ruleId, action, status, reason, targetNodeId, createdAt
  - Expose API to query recent mail activity with filters.
- Frontend:
  - Admin Mail Automation: show a report table with filters and a link to the created document/folder.

Verification:
- Playwright: trigger fetch, assert the report table contains the new message row and links to the target node.

Docs:
- `docs/PHASE1_P62_MAIL_TRACEABILITY_DESIGN_20260212.md`
- `docs/PHASE1_P62_MAIL_TRACEABILITY_VERIFICATION_20260212.md`

### Day 5: P63 Audit Filter by Node + Deep Links
Goal:
- From a node details page, jump to audit logs filtered to that node.

Implementation:
- Backend:
  - Extend audit search to accept `nodeId` filter.
- Frontend:
  - Add "View Audit" action in node details/version dialog (admin only).

Verification:
- Playwright: perform an action (rename/download), then use deep link and assert audit results show the nodeId.

Docs:
- `docs/PHASE1_P63_AUDIT_NODE_FILTER_DESIGN_20260213.md`
- `docs/PHASE1_P63_AUDIT_NODE_FILTER_VERIFICATION_20260213.md`

### Day 6: P64 Effective Permission Explanation UX
Goal:
- Make permission diagnostics understandable: show inherited vs explicit grants and the decision path.

Implementation:
- Frontend:
  - Enhance Permissions dialog with an "Explain access" panel calling existing diagnostics API.
  - Show: evaluated permission, result, matched grants, inheritance markers.

Verification:
- Playwright: create folder -> set inherited perms -> verify explanation shows inherited grants.

Docs:
- `docs/PHASE1_P64_PERMISSION_EXPLANATION_DESIGN_20260214.md`
- `docs/PHASE1_P64_PERMISSION_EXPLANATION_VERIFICATION_20260214.md`

### Day 7: P65 Regression + Release Gate
Goal:
- Make sure the week’s work stays stable and reproducible.

Implementation:
- Add/extend E2E core-gate specs for the newly added flows (scope search, version compare pickers, audit deep link, permission explanation).
- Run:
  - `mvn test` (or targeted suites if full is too slow)
  - Playwright core-gate suite

Docs:
- `docs/PHASE1_P65_WEEKLY_REGRESSION_DESIGN_20260215.md`
- `docs/PHASE1_P65_WEEKLY_REGRESSION_VERIFICATION_20260215.md`

## Assumptions / Defaults
- Keep the text diff feature bounded and text-only (no binary diff).
- Continue using Playwright CLI (`npx playwright test ...`) as the primary verification harness.
- No secrets are committed; OAuth credentials remain env-/runtime-provided only.

