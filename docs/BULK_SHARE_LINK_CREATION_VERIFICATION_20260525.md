# Bulk Share-Link Creation (#1) — Verification

Date: 2026-05-25
Brief: `docs/BULK_SHARE_LINK_CREATION_ADJUDICATION_AND_DESIGN_20260525.md` (gate-approved; D1–D6 at recommended defaults).

## Production changes

### Backend
- `BulkShareLinkService.java` (new) — orchestrator injecting the **proxied** `ShareLinkService`; `@Transactional(propagation = NOT_SUPPORTED)` so a row failure cannot mark an enclosing tx rollback-only and `createShareLink`'s own `@Transactional` engages per row (a same-bean self-call would bypass the proxy). Validates null/empty/null-only `nodeIds`, dedupes first-seen (D3), and per row maps: `NoSuchElementException`/`ResourceNotFoundException`→`NODE_NOT_FOUND`, `SecurityException`→`NO_PERMISSION`, `IllegalArgumentException`→`VALIDATION_ERROR`, other `RuntimeException`→`INTERNAL_ERROR`. FAILED messages are fixed copy (never `ex.getMessage()`); the raw `Throwable` is never passed to SLF4J.
- `BulkOperationController.java` — `POST /api/v1/bulk/share-links` (`@PreAuthorize("hasAnyRole('ADMIN','EDITOR')")`, D2), maps the request to `ShareLinkService.CreateShareLinkRequest`, calls the orchestrator, and maps `CREATED` rows' entity → `ShareLinkController.ShareLinkResponse`. Request/response records added (`BulkShareLinkCreateRequest`, `BulkShareLinkCreateResult`, `…Results`, `…Response`).

**Permission contract (D1) unchanged:** bulk reuses `createShareLink` → keeps `READ`; no tightening to `CHANGE_PERMISSIONS`. Frontend keeps the existing `canWrite` gate.

### Frontend
- `services/shareLinkService.ts` — bulk types + status-keyed response guard (`CREATED`⇒valid `ShareLink`+null error; `FAILED`⇒known `errorCategory`+null shareLink) reusing `isShareLink` and the existing `SHARE_LINK_UNEXPECTED_RESPONSE_MESSAGE` sentinel; `bulkCreateLinks(request)` → `POST /bulk/share-links`.
- `components/share/BulkShareLinksDialog.tsx` (new) — shared-settings dialog (permission default VIEW, optional name/expiry/maxAccessCount/password/allowedIps); on all-created toast+close, on partial failure stays open and renders failed rows grouped by `errorCategory`.
- `pages/FileBrowser.tsx` — a "Share" icon in the selected-items action strip, shown only when `canWrite` AND there is ≥1 selected **document** (`selectedDocumentIds` = selected nodes with `nodeType === 'DOCUMENT'`, D4); folders are excluded before submit; the dialog receives document IDs + the excluded count for copy.

### Schema
- **None.** No migration, no new dependency.

## Tests added

- `BulkShareLinkServiceTest.java` (new, 9) — null/empty + null-only rejection; dedupe first-seen (one `createShareLink` per distinct ID, order preserved); CREATED carries the link; `NoSuchElementException`+`ResourceNotFoundException`→`NODE_NOT_FOUND` (and run continues); `SecurityException`→`NO_PERMISSION`; `IllegalArgumentException`→`VALIDATION_ERROR` (message does not echo the invalid IP); `RuntimeException("USER_PII…PROBE")`→`INTERNAL_ERROR` sanitized (no probe leak, class name only).
- `BulkOperationControllerSecurityTest.java` (+3, +`@MockBean BulkShareLinkService`) — `POST /bulk/share-links`: unauth 401, `ROLE_USER` 403, `ROLE_EDITOR` 200 with `$.bulkShareLinkCreateResults.rows` array.
- `shareLinkService.test.ts` (+7) — posts exact payload; parses mixed CREATED/FAILED; rejects HTML fallback, missing wrapper, CREATED-without-shareLink, FAILED-without-errorCategory, unknown status, unknown errorCategory.
- `BulkShareLinksDialog.test.tsx` (new, 4) — submit disabled at 0 docs; all-created success+close; partial-failure stays-open + per-category failed groups; service rejection toast.

## Local verification

```
shareLinkService.test.ts + BulkShareLinksDialog.test.tsx ... 23/23 PASS
react-scripts build (CI=true) ............................... success (ESLint clean)
```

Backend tests (`BulkShareLinkServiceTest`, `BulkOperationControllerSecurityTest`) ship via the Surefire glob and run in CI (no local Docker/mvnw on this box).

## Decisions / notes

- D1 READ-reuse, D2 `/api/v1/bulk/share-links`, D3 dedupe first-seen, D4 documents-only (client-filtered), D5 CREATED row carries `ShareLinkResponse` (copy-all deferred), D6 no new audit channel — all applied as gate-recommended.
- Architecture: `BulkShareLinkService` carries the `ShareLink` **entity** per row; the controller maps to `ShareLinkResponse` — the service has no controller dependency.
- Verified premises: `createShareLink` is `@Transactional` (`ShareLinkService:54`) → self-call warning valid; `READ` check at `:59`; `validateAllowedIps`→`IllegalArgumentException` at `:389`.

## CI Follow-Up

```
Run id:        <pending>
Head SHA:      <pending>
Conclusion:    <pending — gh run view authority per feedback_gh_run_watch_unreliable>
```
