# Bulk Share-Link Creation - Adjudication and Design

Date: 2026-05-25
Status: read-only brief, awaiting gate
Scope type: product-capability slice

## 1. Context

`docs/PRODUCT_CAPABILITY_DISCOVERY_REFRESH2_20260525.md` ranked bulk document share-link creation as the next default candidate after the version-history CSV export slice. The verified product gap is narrow:

- Operators can multi-select nodes in the file browser.
- Operators can create one share link for one document through the existing share-link manager.
- There is no endpoint or UI path to create share links for N selected documents with per-row partial success.

This brief does not implement the feature. It records the current code facts, adjudicates the permission-contract question, and defines the minimum implementation slice for gate review.

## 2. Primary-source facts

### Existing single-create backend path

- `ecm-core/src/main/java/com/ecm/core/controller/ShareLinkController.java:31` maps both `/api/share` and `/api/v1/share`.
- `ShareLinkController.java:40-56` exposes `POST /nodes/{nodeId}` and returns `201 Created` with `ShareLinkResponse.from(shareLink)`.
- `ShareLinkController.java:211-218` defines `CreateShareLinkRequestDto` with `name`, `expiryDate`, `maxAccessCount`, `permissionLevel`, `password`, and `allowedIps`.
- `ShareLinkController.java:230-267` defines `ShareLinkResponse`, including the generated `token`, node metadata, permission level, password/IP flags, and validity.

### Existing backend permission semantics

- `ecm-core/src/main/java/com/ecm/core/service/ShareLinkService.java:55-88` implements `createShareLink(UUID nodeId, CreateShareLinkRequest request)`.
- `ShareLinkService.java:58-60` checks `securityService.hasPermission(node, PermissionType.READ)` before creating a link.
- The service comment says "permission to share", but the executable contract is `READ`.
- Update/deactivate/delete are intentionally stricter: `ShareLinkService.java:169-216` requires creator or `CHANGE_PERMISSIONS`.

This is the central contract tension for this slice. Bulk create must not silently change the single-create backend contract.

### Existing frontend permission gate

- `ecm-frontend/src/components/share/ShareLinkManager.tsx:57-59` computes `canWrite` from `ROLE_ADMIN` or `ROLE_EDITOR`.
- `ShareLinkManager.tsx:111-128` blocks single-create in the UI when `!canWrite`.
- `ecm-frontend/src/components/browser/FileList.tsx:123` uses the same role-based `canWrite`.
- `FileList.tsx:1371-1377` shows the single-document `Share` context-menu item only for document nodes and `canWrite`.

The frontend UX contract is therefore already stricter than the backend service contract. That mismatch exists today and is not caused by the bulk feature.

### Existing bulk surface

- `ecm-core/src/main/java/com/ecm/core/controller/BulkOperationController.java:35` owns `/api/v1/bulk`.
- Existing endpoints cover move, copy, delete, restore, and metadata: `BulkOperationController.java:48-87`.
- `BulkOperationController.java:50-84` gates mutating bulk operations with `hasAnyRole('ADMIN', 'EDITOR')`.
- There is no share-link bulk endpoint.

### Existing frontend selection surface

- `ecm-frontend/src/pages/FileBrowser.tsx:144` reads `selectedNodes` from the node slice.
- `FileBrowser.tsx:691-719` already renders selected-count actions for download, metadata, and delete.
- `FileBrowser.tsx:1197-1205` mounts a bulk dialog (`BulkMetadataDialog`) with `nodeIds={selectedNodes}`.
- `ecm-frontend/src/components/browser/FileList.tsx:1255-1258` wires DataGrid checkbox selection to the same `selectedNodes`.

The natural UI entry is the selected-items action strip in `FileBrowser`, not the row context menu.

## 3. Adjudication

### Does this conflict with a closed track?

No. This is not frontend service guard work, backend response-contract testing, sensitive-data logging, ADR-001 storage routing, Microsoft OAuth revoke, or RM preset-delivery polish.

It is the next product-capability gap from the refreshed discovery doc: "bulk a frequent single operation."

### Permission contract ruling

Recommended ruling:

- Backend bulk rows reuse the existing backend `READ` create contract by calling `ShareLinkService.createShareLink(...)` for each row.
- Frontend UI entry uses the same `canWrite` gate as the existing single-document share action.
- Do not tighten backend create permission to `CHANGE_PERMISSIONS` or role-only in this slice.
- Do not loosen the existing frontend single-create UI gate in this slice.

Reasoning:

- Reusing `ShareLinkService.createShareLink` preserves the already-shipped API contract.
- The UI gate remains consistent with the existing user-facing share workflow.
- Changing either side is a separate authorization product decision, not a bulk-create implementation detail.

### Endpoint ruling

Recommended endpoint:

`POST /api/v1/bulk/share-links`

Reasoning:

- The operation is a bulk operation over selected node IDs.
- `BulkOperationController` already owns `/api/v1/bulk` and uses `ADMIN|EDITOR` controller-level gating for mutating bulk operations.
- The payload shape is not the same as the single-node `/api/v1/share/nodes/{nodeId}` path because it has `nodeIds` and per-row results.

Rejected alternative:

`POST /api/v1/share/bulk`

It is plausible, but it mixes a batch selected-node action into the single-link management controller and weakens the existing `/bulk` grouping.

## 4. Proposed backend design

### Controller

Add one endpoint to `BulkOperationController`:

```java
@PostMapping("/share-links")
@PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
public ResponseEntity<BulkShareLinkCreateResponse> bulkCreateShareLinks(
    @RequestBody BulkShareLinkCreateRequest request
)
```

The controller should delegate to a dedicated service, not loop inline.

### Request

```java
public record BulkShareLinkCreateRequest(
    List<UUID> nodeIds,
    String name,
    LocalDateTime expiryDate,
    Integer maxAccessCount,
    SharePermission permissionLevel,
    String password,
    String allowedIps
) {}
```

All share settings are shared across rows in v1. Per-row custom names, expiry dates, passwords, or permissions are out of scope.

### Response

Recommended shape:

```java
public record BulkShareLinkCreateResponse(BulkShareLinkCreateResults bulkShareLinkCreateResults) {}

public record BulkShareLinkCreateResults(List<BulkShareLinkCreateResult> rows) {}

public record BulkShareLinkCreateResult(
    UUID nodeId,
    BulkShareLinkCreateStatus status,
    ShareLinkController.ShareLinkResponse shareLink,
    BulkShareLinkCreateErrorCategory errorCategory,
    String message
) {}
```

Status:

- `CREATED`
- `FAILED`

Error category:

- `NODE_NOT_FOUND`
- `NO_PERMISSION`
- `VALIDATION_ERROR`
- `INTERNAL_ERROR`

Invariants:

- `CREATED` rows carry a non-null `shareLink`, null `errorCategory`, and null `message`.
- `FAILED` rows carry null `shareLink`, non-null `errorCategory`, and a sanitized `message`.
- `errorCategory` is never set on `CREATED`.
- `INTERNAL_ERROR` messages include the exception class simple name and never include `ex.getMessage()`.

### Transaction strategy

Do not add `bulkCreateShareLinks` to `ShareLinkService` and call `this.createShareLink(...)`. That would be a same-bean self-call and would bypass the existing `@Transactional` proxy on `createShareLink`.

Recommended pattern:

- Add a dedicated orchestrator service, for example `BulkShareLinkService`.
- Inject the proxied `ShareLinkService`.
- Annotate the orchestrator with `@Transactional(propagation = Propagation.NOT_SUPPORTED)` or keep it non-transactional.
- For each deduped node ID, call `shareLinkService.createShareLink(nodeId, request)` through the injected service.
- Catch classified row exceptions and continue.

This preserves per-row transaction behavior and mirrors the failure-isolation discipline from recent bulk slices without copying their parent/child transaction pattern unnecessarily.

### Input normalization

Recommended:

- Reject null `nodeIds`, empty `nodeIds`, and null-only lists before per-row work.
- Drop null row IDs before dedupe.
- Deduplicate with first-seen order.

Reasoning:

- A duplicate pasted ID should not create two distinct active share links for the same document in one operator action.
- If future audit requirements need "one output row per pasted token," that is a separate traceability decision and should change both frontend and backend together.

### Row classification

Recommended mapping:

- `ResourceNotFoundException` -> `NODE_NOT_FOUND`
- `SecurityException` -> `NO_PERMISSION`
- `IllegalArgumentException` from request validation, allowed IP validation, or enum/value validation -> `VALIDATION_ERROR`
- Any other `RuntimeException` -> `INTERNAL_ERROR`, sanitized

Logging:

- For row-level internal failures, log the node ID and `ex.getClass().getSimpleName()`.
- Do not pass raw `Throwable` to SLF4J in row-level partial-success logging.
- Do not include `ex.getMessage()` in row result messages.

### Audit

Recommended minimum:

- Rely on existing `ShareLinkService.createShareLink` per-row `log.info` for successful creates in v1.
- Do not introduce a new audit channel unless an existing bulk audit helper can be used without widening scope.

Bulk operation history integration can be a follow-up if product needs a centralized "bulk share links" ledger.

## 5. Proposed frontend design

### Service

Extend `ecm-frontend/src/services/shareLinkService.ts`:

```ts
export interface BulkCreateShareLinksRequest extends CreateShareLinkRequest {
  nodeIds: string[];
}

export interface BulkCreateShareLinksResponse {
  bulkShareLinkCreateResults: {
    rows: BulkCreateShareLinkResult[];
  };
}

export interface BulkCreateShareLinkResult {
  nodeId: string;
  status: 'CREATED' | 'FAILED';
  shareLink?: ShareLink | null;
  errorCategory?: 'NODE_NOT_FOUND' | 'NO_PERMISSION' | 'VALIDATION_ERROR' | 'INTERNAL_ERROR' | null;
  message?: string | null;
}
```

Add:

```ts
async bulkCreateLinks(request: BulkCreateShareLinksRequest): Promise<BulkCreateShareLinksResponse> {
  const result = await api.post<unknown>('/bulk/share-links', request);
  return assertBulkCreateShareLinksResponse(result);
}
```

Guard requirements:

- Reuse `isShareLink` for successful row payloads.
- Reject HTML fallback, null raw, missing wrapper, non-array rows, illegal `status`, illegal `errorCategory`, `CREATED` without `shareLink`, and `FAILED` without `errorCategory`.
- Keep the existing `SHARE_LINK_UNEXPECTED_RESPONSE_MESSAGE` or add a dedicated bulk sentinel with the same style.

### UI entry

Recommended:

- Add a selected-items action in `FileBrowser.tsx` next to batch download, bulk metadata, and delete.
- Show it only when `canWrite` and at least one selected node is a document.
- Submit only selected document IDs.
- If selection includes folders, do not send folders; show copy that the bulk share action applies to selected documents only.

Reasoning:

- The existing single-document `Share` action is already context-menu based and document-only.
- The selected-items toolbar is where existing bulk actions live.

### Dialog

Add a focused `BulkShareLinksDialog` or equivalent component.

Fields:

- Permission level: shared across all rows, default `VIEW`.
- Name prefix or name: optional. Recommended v1 copy: "Name (optional, applied to every link)"; do not generate per-document names.
- Expiry date: optional.
- Max access count: optional.
- Password: optional.
- Allowed IPs: optional.

Behavior:

- On submit, call `shareLinkService.bulkCreateLinks`.
- Toast success, partial, or failure based on result counts.
- Render failed rows grouped by `errorCategory`.
- Render created links with token/link copy affordance only if this can reuse an existing copy helper cheaply; otherwise show counts and leave detailed link management to the per-document share manager.
- Clear selection only if all rows created; for partial failure, keep selection so the operator can inspect/retry.

The "copy all created links" affordance is useful but optional for v1. If implemented, it must not block the main slice.

## 6. Tests

### Backend service tests

Suggested new test class:

`ecm-core/src/test/java/com/ecm/core/service/BulkShareLinkServiceTest.java`

Coverage:

- Creates one row per distinct node ID and calls `ShareLinkService.createShareLink` for each distinct ID.
- Rejects empty and null-only `nodeIds` before any per-row work.
- Dedupes duplicate IDs first-seen.
- Continues after a missing node row and creates later rows.
- Maps `SecurityException` to `NO_PERMISSION`.
- Maps allowed-IP validation `IllegalArgumentException` to `VALIDATION_ERROR`.
- Sanitizes internal error row messages: injected `RuntimeException("USER_PII_FROM_EXCEPTION_LEAK_PROBE")` must not appear in output.
- Does not pass raw `Throwable` to row-level logging if log capture is practical; otherwise lock output sanitization.

### Backend controller tests

Add or extend controller tests for:

- `POST /api/v1/bulk/share-links` request/response shape.
- Wrapper path `$.bulkShareLinkCreateResults.rows`.
- `CREATED` row includes `shareLink.token`.
- `FAILED` row carries `errorCategory` and null `shareLink`.
- `nodeIds` omitted or empty returns 400.

Security:

- Unauthenticated -> 401.
- Non-editor/non-admin -> 403.
- Admin/editor -> reaches controller.

The existing share-link controller security test is for `/api/v1/share/**`; this new endpoint lives under `/api/v1/bulk/**`, so it should be added to the bulk security test surface or a focused new test.

### Frontend service tests

Extend `ecm-frontend/src/services/shareLinkService.test.ts`:

- Posts to `/bulk/share-links` with exact payload.
- Accepts valid wrapper with `CREATED` and `FAILED` rows.
- Rejects HTML fallback.
- Rejects illegal row combinations:
  - `CREATED` without `shareLink`
  - `FAILED` without `errorCategory`
  - unknown `status`
  - unknown `errorCategory`

### Frontend UI tests

Add tests around the dialog and selected toolbar:

- Bulk share action visible only for `canWrite` and selected document rows.
- Folders are filtered out before submit.
- Submit sends selected document IDs and shared settings.
- All-created result shows success copy.
- Partial result shows warning copy and failed row groups.
- Service rejection leaves the dialog open and shows an error.

## 7. Gate decisions needed

D1. Permission contract:

Recommended: backend bulk reuses `ShareLinkService.createShareLink` and therefore keeps `READ`; frontend entry remains `canWrite`.

D2. Endpoint:

Recommended: `POST /api/v1/bulk/share-links`.

D3. Duplicate IDs:

Recommended: dedupe first-seen and emit one row per unique non-null node ID.

D4. Folder IDs:

Recommended: frontend sends document IDs only. Backend does not add a new document-only validator unless the existing single-create service already rejects folders. If gate wants backend document-only semantics, that is a deliberate tightening and must be tested against the existing single-create behavior.

D5. Created-link output:

Recommended: each `CREATED` row includes `ShareLinkResponse`. UI may show counts first; copy-all-links can be optional if it does not widen scope.

D6. Audit:

Recommended: no new audit channel in v1; rely on existing per-row create logging. Bulk history integration is a follow-up.

## 8. Out of scope

- Bulk deactivate, reactivate, delete, extend, or rotate share links.
- Per-row custom share-link settings.
- CSV import/export of share links.
- Background worker, progress polling, or async queue.
- Schema migration.
- Changing the single-create `READ` backend permission contract.
- Changing the existing frontend single-create `canWrite` gate.
- Changing public share-link redeem behavior.
- Changing password hashing, token generation, IP validation, or access-log behavior.
- Folder sharing expansion.
- `.env`, YAML, docker-compose, changelog, Logback, or unrelated RM/mail/storage code.

## 9. Verification plan for implementation slice

Local targeted checks:

- `cd ecm-core && ./mvnw -Dtest=BulkShareLinkServiceTest,ShareLinkController*Test,BulkOperationController*Test test` or the exact focused test names created/extended.
- `cd ecm-frontend && npm test -- --watchAll=false src/services/shareLinkService.test.ts` plus the new dialog/page tests.
- Existing share-link frontend service tests.
- `cd ecm-frontend && npm run lint`.
- `cd ecm-frontend && CI=true npm run build`.
- `git diff --check -- . ':!.env'`.

CI:

- Push code/test commit plus verification docs.
- Use `gh run view` conclusion as the authoritative gate.
- On failure, diagnose the single concrete root cause and fix narrowly.
- After 7/7 green, append CI follow-up and commit `[skip ci]`.

## 10. Recommended implementation cadence

1. `fix(core): create share links in bulk`
   - backend service/controller + frontend service/dialog/page + tests in one code commit.
2. `docs(core): record bulk share-link design verification`
   - this brief plus implementation verification doc.
3. Optional `test(core): ...`
   - only if CI exposes a fixture or contract alignment issue.
4. `docs(core): record CI for bulk share-link creation [skip ci]`
   - only after final 7/7 success.

