# Document Version-History CSV Export — Adjudication & Implementation Brief (read-only)

Date: 2026-05-25
Status: **read-only brief — no code/test/schema/`.env` written by this document.**
Candidate: **#2** in `docs/PRODUCT_CAPABILITY_DISCOVERY_REFRESH2_20260525.md` (gate recommended opening this first).

## 0. Purpose

A document's version history is viewable in the UI (`VersionHistoryDialog`) but cannot be exported. This slice adds a **CSV export** of a document's versions — a clean *view-but-not-export* gap. Plausible driver is offline/compliance review; **no captured operator signal** (value is inferred, per the refresh-2 honesty caveat).

## 1. Adjudication — memory / closeout cross-check

- Not RM-preset (FORBIDDEN closeout); versions are a general document feature. Not defensive/test-only. No memory collision.
- Why this before #1 (bulk share-link): **permission semantics are trivial here** — `getVersionHistory` already enforces `READ`, so export === view (same `READ` gate). #1 needs the READ-vs-`canWrite`/`CHANGE_PERMISSIONS` contract adjudicated first (gate finding), so #1 stays deferred. See §7.

**Verdict:** in-scope, small, frontend+thin-backend, no schema. Proceed.

## 2. The gap (verified)

- `DocumentController.java:192/201/213/238/260` — versions, versions/paged, versions/compare, version download, revert. **No export/CSV endpoint.** Class mapping `{"/api/documents","/api/v1/documents"}` (`:50`), no class-level `@PreAuthorize` (the version reads rely on the service-layer `READ` check).
- `VersionService.getVersionHistory(documentId, majorOnly)` (`:185`) → `List<Version>`, and it **enforces `securityService.hasPermission(document, READ)`** (`:189`, `SecurityException` otherwise). Reusing it makes the export inherit the exact view permission.
- `VersionHistoryDialog.tsx` — lists versions; **no export control.**

## 3. Scope (locked)

1. Backend: `GET /api/v1/documents/{documentId}/versions/export` (+ `/api/documents` alias via the dual mapping) returning a `text/csv` attachment of the document's versions; optional `majorOnly` query param (parity with the sibling endpoints). Reuses `getVersionHistory` (→ inherits the `READ` check). CSV built in a service method using the established `appendCsvRow`/`csvEscape` quoting.
2. Frontend: an **"Export CSV"** button in `VersionHistoryDialog` → a `versionService`/`documentService` method that fetches the blob and triggers a download.

## 4. Out of scope — no-touch ring

1. No schema, no migration, no new dependency.
2. No change to the existing version endpoints/DTO or `getVersionHistory` signature.
3. No scheduled/recurring export (one-shot, operator-initiated).
4. No comment-thread export, no diff/compare export (separate candidates).
5. No pagination on the export (a document's version count is bounded; export all matching `majorOnly`). If an absolute safety cap is wanted, surface it (D-cap), but versions-per-doc is not an unbounded set like mail messages.

## 5. Contract

### Endpoint
```
GET /api/v1/documents/{documentId}/versions/export?majorOnly=false
# no @PreAuthorize — consistent with the sibling version reads; the service-layer READ
# check in getVersionHistory is the gate (export === view).
Response: 200 text/csv
  Content-Disposition: attachment; filename="<safeDocumentName>-versions-<yyyyMMdd-HHmmss>.csv"
  Content-Type: text/csv
  body: CSV string
# Filename (gate-resolved, D-filename): sanitize the document name (strip path/illegal chars);
# fall back to documentId when the name is empty/blank.
```
Mirror the existing CSV download response exactly (`MailAutomationController.exportReport:577-581`: `ResponseEntity<String>` + `Content-Disposition attachment` header + `MediaType.valueOf("text/csv")` + csv body).

### Errors (existing handler)
- Document not found → `NoSuchElementException` → **404**.
- No `READ` permission → `SecurityException` → **403** (raised by `getVersionHistory`).

### CSV shape (gate-resolved, D-cols)
- Header row, then one row per version, newest-first (the repo order `findByDocumentIdOrderByVersionNumberDesc`).
- **Columns:** `Version Label`, `Created Date` (ISO), `Created By`, `Comment`, `Size (bytes)`, `Major` (true/false), `MIME Type`, `Status`. **Excludes `contentHash` / `contentId`** (internal implementation detail; add only on a forensic requirement).
  - **Correction:** `VersionDto` (`VersionDto.java:8`) already exposes `size` (`long`, line 15), `mimeType`, and `status` (plus `creator`, `comment`, `createdDate`, `versionLabel`, `major`). So the CSV can be built from **`VersionDto` or the `Version` entity** — use whichever is convenient without changing the DTO shape, keeping columns aligned to the existing DTO/UI fields. (`Created By` column ← DTO `creator`.)
- **Escaping:** reuse the RFC-4180 quoting from `MailFetcherService.csvEscape` (quote when the value contains `"` `,` `\n` `\r`; double embedded quotes). It is a `private static` in another package — replicate the same tiny helper in the version-export code (or a shared `CsvUtil` if one already exists; do not cross-package-import a private method). This is the gate's Low finding: anchor to the **CSV attachment response + escaping pattern**, not just one service method.

### Frontend
- `services/...` — new `exportVersionsCsv(documentId, majorOnly?)` returning `Blob` via `api.getBlob('/documents/{documentId}/versions/export', { params })` (mirror `mailAutomationService.exportReportCsv:988`).
- `VersionHistoryDialog.tsx` — "Export CSV" button; handler does `URL.createObjectURL(blob)` → anchor `download` (sanitized `<safeDocumentName>-versions-<yyyyMMdd-HHmmss>.csv`, falling back to documentId) → click → revoke (mirror `MailAutomationPage.tsx:1398-1410`). Disable while the dialog has no versions. (Server also sets the filename via `Content-Disposition`; the anchor `download` is the client-side default.)

## 6. Affected surfaces

- Backend: `DocumentController` (one new GET) + `VersionService` (one CSV-builder method reusing `getVersionHistory` + a local `csvEscape`). Ships with the controller-security-test treatment only if the sibling version endpoints have one — otherwise the service `READ`-check is covered by a `VersionService` test asserting `SecurityException` propagates and CSV content/escaping is correct.
- Frontend: one service method + one button + co-located test.
- Schema: none.

## 7. Decisions (gate-adjudicated 2026-05-25)

- **D-cols — RESOLVED:** `Version Label`, `Created Date`, `Created By`, `Comment`, `Size (bytes)`, `Major`, `MIME Type`, `Status`. Keep `MIME Type` + `Status` (already public on `VersionDto`/UI; the audit export should fully cover existing DTO/UI semantics). **Exclude `contentHash`/`contentId`** (internal; forensic-only).
- **D-filename — RESOLVED:** `"<safeDocumentName>-versions-<yyyyMMdd-HHmmss>.csv"`; the document name **must be sanitized** (strip path/illegal chars), empty name **falls back to `documentId`**.
- **D-cap — RESOLVED:** **no row cap.** Versions-per-doc is a naturally bounded set and `majorOnly` remains available as a filter; a cap would create an "incomplete export" audit risk.
- **#1 deferral (recorded):** Bulk share-link creation stays deferred until its permission contract is adjudicated. Gate finding to resolve in the #1 brief: `ShareLinkService.createShareLink` checks only `PermissionType.READ`, but the existing UI (`ShareLinkManager`) gates creation behind `ROLE_ADMIN|ROLE_EDITOR`. The #1 bulk brief must decide whether the bulk backend strictly reuses the single-create `READ` semantics or tightens to `canWrite`/`CHANGE_PERMISSIONS` — **without silently changing the existing single-create contract.**

## 8. Memory checklist

- `feedback_brief_paths_must_be_grep_verified` — endpoints/paths verified: versions routes `DocumentController:192-260`, `getVersionHistory` READ check `VersionService:185-193`, CSV response `MailAutomationController:577-581`, `csvEscape`/`appendCsvRow` in `MailFetcherService`, blob download `mailAutomationService:988` + `MailAutomationPage:1398`.
- `feedback_http_success_is_not_semantic_success` — n/a (download is the outcome); the frontend just triggers the blob download.
- `feedback_per_slice_fix_commit_stages_code_and_test` — stage the co-located service/dialog test with the code at implementation.

## 9. What this brief does not commit to

- No track opened; no code/test/frontend/schema/`.env` change; no commits by the brief itself.
- §5 contract is firm pending the §7 decisions.
- Implementation begins only after gate adjudication.

## 10. Verification (this brief)

```bash
git status --short                              # M .env + this brief only
git diff --stat -- 'ecm-core/' 'ecm-frontend/'  # empty
```
