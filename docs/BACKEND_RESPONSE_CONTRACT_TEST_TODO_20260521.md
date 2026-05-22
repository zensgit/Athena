# Backend Response-Contract Test TODO

Date: 2026-05-21

## Purpose

The frontend service response-shape guard track is closed; the only authoritative wire-shape validator was the Frontend E2E Core Gate, which discovered two real backend drift sites during the closeout phase (`path: null` on search-result items; `queryCriteria: null` and `size: null` on folder browsing endpoints). To stop relying on E2E roundtrips as the canonical wire-contract test, this document seeds a backend track of Spring-side response-contract tests.

Scope: planning inventory only. No Java or TypeScript source changes. Output ranks the **Top 10 endpoint groups** most likely to drift, with a recommended test type per group.

## Risk-ranking criteria

Each group is scored on five axes:

1. **Trace history** — whether the group already triggered a documented frontend guard trace-correction. Strongest signal; proven drift.
2. **Consumer breadth** — how many frontend services / pages depend on it. Larger blast radius if shape drifts.
3. **Nullable-field density** — count of `String` / `Integer` / `Long` fields without `@NotNull` plus all `LocalDateTime` (Jackson can serialize as array form).
4. **Collection / envelope complexity** — presence of `Page<T>` / `List<T>` / `Map<String, List<T>>` / nested inline records.
5. **Endpoint count** — cumulative attack surface within the controller.

Trace history dominates the ranking; it is the only axis with empirical drift evidence.

## Test-type framework

The doc recommends one of three test shapes per group. Pick by what the group most needs to lock:

- **MockMvc** (`@WebMvcTest` + `MockMvc.perform(...).andExpect(jsonPath(...))`) — pins endpoint path, HTTP status, JSON field set, null serialization. Best for the highest-risk groups where one wire-shape lock prevents many downstream consumer breakages.
- **Service slice** (`@WebMvcTest` plus real Jackson roundtrip via `@Autowired ObjectMapper` and DTO `serialize → JsonNode → assert`) — best for DTO-heavy groups where the JSON-serialization itself is the at-risk surface (timestamp format, optional vs nullable field shape).
- **Fixture only** — backend produces a canonical JSON fixture; frontend tests load and validate against it. Cheapest; right for stable groups where the goal is "checkpoint the wire" without exhaustive endpoint coverage.

## Top 10 endpoint groups (ranked)

### 1. Folder browsing — `FolderController` (`/api/v1/folders`)

- **Frontend consumer:** `nodeService.ts` (folder-tree + file-browser core); ~10 methods. Powers FolderTree, FileBrowser, PDF preview file selection, and indirectly every page that opens a folder. Highest blast-radius group on the frontend.
- **DTO / record:** Inline records in `FolderController.java:339-452`:
  - `FolderResponse` (19 fields; defines `queryCriteria: Map<String, Object>`; **does not define `size`**) — returned by `/folders/roots` (`List<FolderResponse>`) and most folder-CRUD endpoints.
  - `NodeResponse` (15 fields; defines `size: Long`; **does not define `queryCriteria`**) — returned inside `Page<NodeResponse>` on `/folders/{id}/contents` and inside `FolderContentsResponseDto.contents`.
  - `FolderContentsResponseDto` (wraps `FolderResponse` + `List<NodeResponse>` + summary counters).
- **Nullable / timestamp / envelope risk:** `FolderResponse` — 6-7 nullable `String`s, 2 `LocalDateTime`s (`createdDate`, `lastModifiedDate`), `queryCriteria` is `null` for non-smart folders. `NodeResponse` — 7 nullable `String`s, 2 `LocalDateTime`s, `size: Long` is `null` for folder items inside a contents listing. `Page<NodeResponse>` page envelope on `/contents`.
- **Trace-corrected:** **Yes — twice, on two different endpoints / two different DTOs.**
  - `queryCriteria: null` on `FolderResponse` items returned by `/folders/roots` (commit `b35f0fa`).
  - `size: null` on `NodeResponse` items returned by `/folders/{id}/contents` (commit `4582588`).
- **Recommended test:** **MockMvc**, split into two endpoint-scoped assertions to avoid conflating the two DTOs:
  - `/folders/roots` — pin the JSON as a flat array of `FolderResponse`, assert each item carries the 19 `FolderResponse` fields, and assert `queryCriteria` serializes as JSON `null` (not omitted) when the source is null. Do **not** assert `size` here; `FolderResponse` has no such field.
  - `/folders/{id}/contents` — pin the `Page<NodeResponse>` envelope (`content` / `pageable` / `totalElements` / `totalPages` / `size` / `number` / `first` / `last`), and assert each `content[*]` carries the 15 `NodeResponse` fields with `content[*].size` serializing as JSON `null` (not omitted) for folder rows. Optionally assert the parallel `FolderContentsResponseDto` shape when that variant is requested.

### 2. Search — `SearchController` (`/api/v1/search`)

- **Frontend consumer:** `nodeService.ts` (`searchNodes`, `searchNodesEnvelope`, `getAdvancedSearchStats`, `getAdvancedSearchPivotStats`, `findSimilar`, `getSearchFacets`, etc.); also `savedSearchService.ts`, `peopleService.ts`. Heavy E2E exposure.
- **DTO / record:** `SearchResult` at `ecm-core/src/main/java/com/ecm/core/search/SearchResult.java` (**31 declared fields** in the current source — the full set is enumerated below; an earlier raw-scan said 22, which missed the RM-disclosure block, the preview-failure block, and `correspondent`). Jackson also serializes the derived `getFileSizeFormatted()` getter as `fileSizeFormatted`, so the current wire shape has **32 properties**. 24 inline records in `SearchController.java` for envelope shapes (`SearchQueryEnvelopeResponse`, `AdvancedSearchPivotStatsResponse`, etc.).

  Full current `SearchResult` declared field set (31): `id, name, description, path, nodeType, parentId, mimeType, fileSize, currentVersionLabel, createdBy, createdDate, lastModifiedBy, lastModifiedDate, score, highlights, matchFields, highlightSummary, tags, categories, correspondent, record, declaredBy, declaredAt, declaredVersionLabel, declarationComment, recordCategoryId, recordCategoryName, recordCategoryPath, previewStatus, previewFailureReason, previewFailureCategory`. Current derived wire-only property: `fileSizeFormatted`.
- **Nullable / timestamp / envelope risk:** ~20 nullable `String`s out of 31 fields on `SearchResult` (id and name are the only consistently non-null identifiers); `fileSize: Long` nullable; 2 `LocalDateTime`s (`createdDate`, `lastModifiedDate`); 4 collection fields (`highlights: Map<String, List<String>>`, `matchFields: List<String>`, `tags: List<String>`, `categories: List<String>`); 2 primitives (`score: float`, `record: boolean`). `Page<SearchResult>` + `Map<String, List<FacetValue>>` for facets. Pivot stats include a nested matrix.
- **Trace-corrected:** **Yes.** `path: null` on search-result items (commit `488d830`).
- **Recommended test:** **MockMvc.** Pin the **current full `SearchResult` wire contract**: the 31 declared fields plus `fileSizeFormatted` (not the stale 22-field shape that earlier raw scans suggested), including which strings are nullable, `fileSize` nullable, and the four collection fields. Pin envelope sub-fields (`results` / `facets` / `suggestions` / `stats` / `pivot`) as optional + nullable. Lock the response shape for `/search/query` with `include: ['results','facets','suggestions','stats','pivot']`. If `SearchResult` gains a field or getter that changes JSON output, the test must be updated in the same change — that is the whole point of locking the field set.

### 3. Node CRUD + relations — `NodeController` (`/api/v1/nodes`)

- **Implementation status:** In progress slice 3 locks the core read endpoints only: `/nodes/{id}` and `/nodes/{id}/children`. Relation, lock/checkout, version-history, and permission endpoints remain separate follow-up slices.
- **Frontend consumer:** `nodeService.ts` (20+ methods). Central node management; touches every node-detail page, version history, checkout/checkin, renditions, relations.
- **DTO / record:**
  - Primary read DTO: **`NodeDto`** at `ecm-core/src/main/java/com/ecm/core/dto/NodeDto.java` — returned by `/nodes/{id}`, `/nodes/by-path`, and (wrapped in `Page<NodeDto>`) by `/nodes/{id}/children` (`NodeController.java:107-113`), plus the create/update/move/copy/aspect endpoints.
  - 9 inline relation records in `NodeController.java:1064-1163` — `NodeRelationsSummaryDto`, `NodeRelationNodeRefDto`, `NodeRelationEdgeDto`, `NodeRenditionRelationSummaryDto`, `NodeRenditionRelationDto`, `NodeCheckoutRelationDto`, `NodeCheckoutGraphNodeDto`, `NodeCheckoutGraphEdgeDto`, `NodeCheckoutGraphDto`.
  - Imported `VersionDto` (7 nullable strings, 1 `LocalDateTime`) — used on `/relations/versions`.
- **Nullable / timestamp / envelope risk:** `NodeDto` carries the same per-entity nullable risks as `FolderController.NodeResponse` (description / path / nodeType / lockedBy / size / etc.), though they are distinct record types — Jackson serializes each independently. 6-8 nullable strings per relation DTO; `LocalDateTime` on `checkoutDate` / `previewLastUpdated` / `createdDate`; `Page<NodeDto>` envelope on `/children`; `List<NodeRelation*>` envelopes and `Page<VersionDto>` on the relation paths.
- **Trace-corrected:** **Indirect.** The `4582588` fix on `/folders/{id}/contents` proved that the underlying `Node.size` is `null` for folder rows. `/nodes/{id}/children` exposes the **same nullable `size` risk on the same entity field, but via a different wire DTO (`NodeDto`, not `FolderController.NodeResponse`)**, so the contract test must lock the shape on `NodeDto` independently — the FolderController test does not cover it.
- **Recommended test:** **MockMvc.** Pin `/nodes/{id}` (single `NodeDto`) and `/nodes/{id}/children` (`Page<NodeDto>`) field sets including all the optional nullable-string fields and `size: null` on folder rows. Pin relations envelopes (`/relations/parents`, `/relations/sources`, `/relations/targets`, `/relations/versions`, `/relations/checkout-graph`). Cover the inline-record problem by exporting these to top-level DTOs in a future scope, or test each inline record via dedicated MockMvc cases.

### 4. Preview diagnostics — `PreviewDiagnosticsController` (`/api/v1/preview/diagnostics`)

- **Frontend consumer:** `previewDiagnosticsService.ts` — **61 JSON methods**, the largest single-service surface in the frontend; called from diagnostics dashboards and preview failure flows.
- **DTO / record:** Many — exact count not enumerated in the orientation scan. Failure DTOs (`PreviewQueueFailureDto`), summary DTOs, async-export-task lifecycle DTOs, rendition resource DTOs.
- **Nullable / timestamp / envelope risk:** High suspected — failure-reason / failure-category / status strings tend to be nullable; rendition resource has ~20 fields with timestamps and URLs; async-task envelopes carry queue/run/complete dates that are nullable until terminal.
- **Trace-corrected:** Not directly, but the closest cousin (search-results) was; the preview-by-search async-tail (already guarded in the frontend) shares the same async-task envelope pattern.
- **Recommended test:** **Service slice.** 73 endpoints is too many for endpoint-by-endpoint MockMvc; instead, pick the ~6-8 highest-traffic endpoints (failures list, summary, async-task lifecycle) and pin those with MockMvc; everything else gets a fixture-only contract.

### 5. Records management — `RecordsManagementController` (`/api/v1/records/*`)

- **Frontend consumer:** `recordsManagementService.ts` — **37 JSON methods**; called from RM admin dashboards, declarations, activity audits, contributor reports.
- **DTO / record:** Activity / audit / contributor records (specific DTOs not enumerated in orientation). RM is the most data-domain-rich subsystem (file plans, categories, dispositions, holds).
- **Nullable / timestamp / envelope risk:** Suspected high — activity records carry `declaredBy` / `declaredAt` / `recordCategoryPath` strings that may be null; contributor reports aggregate timestamps; activity-breakdown / activity-timeline have date-bucket envelopes.
- **Trace-corrected:** No documented trace yet.
- **Recommended test:** **Service slice.** Test the activity / report / contributor envelopes with serialized round-trips; RM declarations probably warrant MockMvc on the create/declare endpoints to lock the declared-by/at field shape.

### 6. Document operations — `DocumentController` (`/api/v1/documents`)

- **Frontend consumer:** `nodeService.ts` (document-specific methods: `downloadDocument`, version listings, `checkoutDocument`, `cancelCheckoutDocument`, `checkinDocument`, preview/annotation endpoints).
- **DTO / record:** Heavy reliance on imported DTOs — `VersionDto` (14 fields, 7 nullable strings, 1 `LocalDateTime` at `ecm-core/.../dto/VersionDto.java`), `CheckoutInfoDto` (~8 fields, 3-4 nullable strings, 1 `LocalDateTime` at `ecm-core/.../dto/CheckoutInfoDto.java`).
- **Nullable / timestamp / envelope risk:** Medium — version label and checkout-user strings frequently nullable; `LocalDateTime` for createdDate / checkoutDate. `List<VersionDto>` + `Page<VersionDto>` envelopes on `/documents/{id}/versions`.
- **Trace-corrected:** Indirect via NodeController's `size: null` for the document-as-node path.
- **Recommended test:** **MockMvc** for `/documents/{id}/versions` and `/documents/{id}/checkout-info` (the two pages with most consumer use); **fixture-only** for the upload/download/conversion endpoints (Blob/void OOS but their JSON sidecar responses still benefit from a checkpoint).

### 7. Ops recovery — `OpsRecoveryController` (`/api/v1/ops/recovery`)

- **Frontend consumer:** `opsRecoveryService.ts` — 25 JSON methods (guard inventory); 8 Blob/download OOS. Used in admin recovery dashboards.
- **DTO / record:** Recovery history records, async-export-task DTOs (lifecycle: QUEUED / RUNNING / COMPLETED / FAILED / TIMEOUT / EXPIRED), filter DTOs.
- **Nullable / timestamp / envelope risk:** Async-task envelopes have `error?` / `message?` / `startedAt?` / `finishedAt?` / `filename?` — typical of the async-tail pattern; history paging carries queue/run/actor dates.
- **Trace-corrected:** No direct trace; the structurally-identical pattern in `previewDiagnosticsService` async-tail was guarded without drift.
- **Recommended test:** **Service slice.** Pin one async-task lifecycle endpoint with MockMvc; the rest covered by fixture-only since the async-task DTO is the central reusable shape.

### 8. Workflow — `WorkflowController` (`/api/v1/workflows`)

- **Frontend consumer:** `workflowService.ts` — 23 JSON methods; called from workflow definition UI, task pages, approval flows.
- **DTO / record:** Workflow definitions, instances, tasks, history. State strings (`PENDING` / `IN_PROGRESS` / `COMPLETED` / `CANCELLED`) likely nullable until transition; assignee strings nullable.
- **Nullable / timestamp / envelope risk:** Medium — typical state-machine DTOs with assignment + date fields.
- **Trace-corrected:** No documented trace.
- **Recommended test:** **Fixture only** — workflow shape is stable in practice; capture canonical JSON for definition / instance / task / history-event and let consumer tests assert against them. Promote to MockMvc only if a drift surfaces.

### 9. Mail automation — `MailAutomationController` (`/api/v1/integration/mail` and related)

- **Frontend consumer:** `mailAutomationService.ts` — 26 JSON methods; called from `/admin/mail` UI (account CRUD, rules, scheduler, retention, runtime metrics).
- **DTO / record:** Mail account, rule, fetch summary, runtime metrics, replay result, runtime error stat. Many of these were captured in the frontend predicates already (`MailAccount`, `MailRule`, `MailFetchSummary`, etc.), so the shapes are well-mapped.
- **Nullable / timestamp / envelope risk:** Medium — `lastFetchAt?`, `lastFetchStatus?`, `lastFetchError?` on account; `nextAttemptAt?` on processed-mail diagnostics; OAuth fields often nullable depending on provider.
- **Trace-corrected:** No documented trace, but Phase 5 Mocked exposed mock thinness on the closely-related `/api/v1/nodes/{id}` path (different controller — DocumentController/NodeController — but illustrates that the mocked harness here can lag behind contract).
- **Recommended test:** **MockMvc** for `listAccounts` / `listRules` / `getRuntimeMetrics` (the three highest-traffic reads); **fixture-only** for the diagnostic-replay and provider-preset endpoints.

### 10. Permissions — `PermissionController` (or permission endpoints under `NodeController` / `PermissionTemplateController`)

- **Frontend consumer:** `nodeService.ts` (permissions section: `getPermissions`, `getPermissionDiagnostics`, `getPermissionSets`, `getPermissionSetMetadata`, `setPermission`, etc.) + `permissionTemplateService.ts` (6 JSON methods).
- **DTO / record:** Permission record (group/role/user + permission level), permission decision (with reasoning), permission set metadata, inheritance-path entries, ACL templates.
- **Nullable / timestamp / envelope risk:** Medium-high — inheritance paths contain nested folder references that may be null at the root; permission-decision DTOs include nullable `reason` / `denyReason` / `inheritedFrom`; sets metadata serializes enum levels.
- **Trace-corrected:** No documented trace — the permissions sub-slice was the last node-service slice (`9976821`, run `26226880018`) and closed clean. But the predicate is fresh and not yet stress-tested; locking the wire now is cheap insurance.
- **Recommended test:** **MockMvc** for `GET /nodes/{id}/permissions` and the diagnostics endpoint (these have the most complex inheritance/decision JSON); **fixture-only** for the set-metadata listing.

## Recommended first-3 sequencing

If the gate opens this track, the first three slices should be the proven-drift groups, in this order:

1. **FolderController MockMvc** — directly tests the two existing trace-corrections, split by endpoint and DTO: `queryCriteria: null` on `FolderResponse` items returned by `/folders/roots`, and `size: null` on `NodeResponse` items returned by `/folders/{id}/contents`. Pins the contract that the frontend predicates were widened to match. Smallest scope of the top 3.
2. **SearchController MockMvc** — pins the current `SearchResult` wire contract (31 declared fields plus `fileSizeFormatted`; the RM-disclosure block, the preview-failure block, and `correspondent` are all in scope), plus envelope optional+nullable sub-fields. Larger scope than FolderController because of the field-count and envelope variety, but the documented drift is one field (`path`).
3. **NodeController MockMvc** — broader (9 inline relation records + `NodeDto` + 39 endpoints). The `size: null` risk on `/nodes/{id}/children` is structurally the same as the folder-contents fix but lives on `NodeDto`, not `FolderController.NodeResponse`, so it needs its own lock. This slice warrants extracting inline records to top-level DTOs first if reuse is desired; otherwise, MockMvc per inline record.

Once these three lock, the trace-correction history is fully covered. The remaining 7 groups become "harden against new drift" rather than "fix known drift."

## Honest gaps in this scan

- DTO field counts for `RecordsManagementController`, `OpsRecoveryController`, `PreviewDiagnosticsController`, `WorkflowController`, `MailAutomationController`, `PermissionController` were not deep-counted. The ranking uses surface signals (endpoint count, frontend method count, structural similarity to other groups) rather than per-field nullability. A follow-up read-only scan focused on those controllers' DTO definitions would tighten the ranking.
- Several frontend services (records, ops-recovery, etc.) have explicit Java DTO sources that should be inspected before opening a slice — the predicate inventories on the frontend already capture the wire shape. An initial grep did not find local `@JsonInclude` / `spring.jackson.default-property-inclusion` overrides, but each slice should still verify the target `ObjectMapper` behavior before asserting `null` vs omitted fields.
- The recommendation does not cover infrastructure controllers (auth/bootstrap), pure pass-through proxies, or admin-only endpoints with no UI consumer; the assumption is the response-contract risk maps to UI failure modes.

## Constraints honored

- Read-only scan; no Java or TS source modified.
- This is a docs-only planning artefact for gate review before implementation.
