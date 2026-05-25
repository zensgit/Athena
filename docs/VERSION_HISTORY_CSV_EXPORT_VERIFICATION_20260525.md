# Document Version-History CSV Export (#2) — Verification

Date: 2026-05-25
Brief: `docs/VERSION_HISTORY_CSV_EXPORT_ADJUDICATION_AND_DESIGN_20260525.md` (gate-approved; D-cols/D-filename/D-cap resolved).

## Production changes

### Backend
- `VersionService.java` — `exportVersionHistoryCsv(documentId, majorOnly)` reuses `getVersionHistory(documentId, majorOnly)` (so the **READ permission check applies identically** — export === view), maps each `Version` via `VersionDto.from`, and builds the CSV with a local `appendCsvRow`/`csvEscape` (RFC-4180 quoting, matching the established mail-export pattern). Columns (D-cols): `Version Label, Created Date, Created By, Comment, Size (bytes), Major, MIME Type, Status` — `contentHash`/`contentId` excluded.
- `DocumentController.java` — `GET /api/v1/documents/{documentId}/versions/export?majorOnly=false` (+ `/api/documents` alias) returning a `text/csv` attachment, mirroring the existing CSV-download response (`ResponseEntity<String>` + `ContentDisposition.attachment().filename(..., UTF_8)` + `MediaType "text/csv"`). Filename (D-filename): `"<safeDocumentName>-versions-<yyyyMMdd-HHmmss>.csv"`, document name sanitized (`[^A-Za-z0-9._-]→_`), blank → documentId. No `@PreAuthorize` (consistent with the sibling version reads; the service `READ` check is the gate). No row cap (D-cap).

### Frontend
- `services/nodeService.ts` — `exportVersionHistoryCsv(nodeId, majorOnly=false)` → `api.downloadFile('/documents/{nodeId}/versions/export', '<safe>-versions-<ts>.csv', { params: { majorOnly } })`; derives the client filename from `getNode` (sanitized, dated), mirroring `downloadVersion`.
- `components/dialogs/VersionHistoryDialog.tsx` — "Export CSV" button in the dialog actions (`data-testid="version-export-csv"`), disabled while `loading` or `versions.length === 0`; success/failure toast (server errors also surfaced by the api interceptor).

### Schema
- **None.** No migration, no new dependency, no change to existing version endpoints/DTO.

## Tests added

- `VersionServiceCsvExportTest.java` (new, 4) — header + rows + **RFC-4180 escaping** (comment with comma + embedded quotes); **`SecurityException` propagates without READ** (export === view); `majorOnly=true` uses `findMajorVersions`; document-not-found → `NoSuchElementException`.
- `nodeService.versionHistory.test.ts` (+2) — `exportVersionHistoryCsv` calls `downloadFile` with the export URL + `{ params: { majorOnly } }` + a sanitized dated filename (`Contract.pdf-versions-<ts>.csv`); `majorOnly=true` forwarded.

## Local verification

```
nodeService.versionHistory.test.ts ....... 10/10 PASS (incl. 2 new export tests)
react-scripts build (CI=true) ............ success (ESLint clean — CI=true fails on warnings)
```

Backend tests (`VersionServiceCsvExportTest`) ship via the Surefire glob and run in CI (no local Docker/mvnw on this box).

## Decisions / notes

- **D-cols/D-filename/D-cap** applied exactly as gate-adjudicated.
- **Size correction:** `VersionDto` (`VersionDto.java:15`) already exposes `long size` (+ `mimeType`, `status`) — the brief's earlier "VersionDto omits size" was wrong and corrected; CSV is built from `VersionDto` without widening it.
- **Permission contract is trivial here** (READ to view = READ to export), which is why #2 was sequenced before #1 (bulk share-link), whose READ-vs-`canWrite`/`CHANGE_PERMISSIONS` contract is still to be adjudicated in its own brief.

## CI Follow-Up

```
Run id:        <pending>
Head SHA:      <pending>
Conclusion:    <pending — gh run view authority per feedback_gh_run_watch_unreliable>
```
