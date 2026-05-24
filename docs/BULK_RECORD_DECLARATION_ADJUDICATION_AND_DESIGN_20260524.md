# Bulk Record Declaration — Memory Adjudication + Implementation Brief

Date: 2026-05-24

## Purpose

Top 3 candidate #3 from `docs/PRODUCT_CAPABILITY_DISCOVERY_20260524.md`. The candidate was flagged with a memory-conflict caveat: `project_rm_preset_delivery_closeout` warns *"PR-95..121 declared done 2026-04-23; don't auto-pick more polish, open a new capability"*. The gate asked for a **read-only adjudication before any slice opens**: is bulk record declaration with category assignment inside the forbidden RM-preset-delivery polish zone, or is it a separable new capability on the declaration subsurface?

This document does the adjudication first (§1-2), then conditional on a pass, scopes the slice (§3+). It does not write code; it does not commit.

**Brief revision history**

- v1 (initial): adjudication + slice scope with three open issues (see v2 changes).
- v2 (post-gate findings 1/2/3): adjudication **accepted by gate**. Three brief-level findings applied:
  1. Removed legal-hold-style "parent-before-row InOrder" test (no parent entity here); replaced with `declareRecordsBulkContinuesAfterFailedRowAndPreservesOrder` with three independent property locks (§6).
  2. Pinned semantics: an already-declared row with a non-null request.categoryId is `SKIPPED_ALREADY_DECLARED` and does **not** apply the category — mirrors single-row `RecordsManagementService.java:510-512` verbatim. Cross-row bulk-assign-category is a separate capability and is OOS (§4, §6, §9).
  3. `SKIPPED_ALREADY_DECLARED` is a row **status** with `errorCategory === null`, not an error category. The set of error categories is fixed at `{NODE_NOT_FOUND, NODE_NOT_VISIBLE, INTERNAL_ERROR}` (§3, §5).
- v3 (post-gate findings 1/2/3 round-2):
  1. **Blocker fixed**: §4 helper return protocol. v2's helper returned `RecordDeclarationDto` and the orchestrator used `dto == null` as the SKIPPED signal. But Finding 2's lock forces the already-declared branch to return `toDto(document)` (non-null), so v2 would have misclassified every already-declared row as `DECLARED`. v3 introduces a tagged internal record `DeclareOneOutcome(Status status, RecordDeclarationDto declaration)` so status is signalled in-band; the orchestrator switches on `outcome.status()`. Both `DECLARED` and `SKIPPED_ALREADY_DECLARED` carry a non-null `declaration`.
  2. Endpoint anchor corrected: existing single-row route is `PUT /api/v1/nodes/{nodeId}/record` (`RecordsManagementController.java:523`), not `POST /api/v1/records/{nodeId}/declare` as v2 claimed.
  3. Security wording corrected to **admin-only**: class-level `@PreAuthorize("hasRole('ADMIN')")` (`RecordsManagementController.java:40`) + service-side `requireAdmin()` (multiple call sites starting `RecordsManagementService.java:184`). No manager role admits today. Security test matrix narrowed accordingly.
- v3.1 (post-v3 non-blocking note, applied before implementation): orchestrator must guard `deduped.isEmpty()` AFTER the null-filter pass, not only `request.nodeIds().isEmpty()` BEFORE it. A caller passing `{ "nodeIds": [null] }` (or a list of only null entries) would otherwise produce an empty `rows: []` 200 response, bypassing the UI parser. Fix: after the dedupe loop, throw `IllegalArgumentException("nodeIds must contain at least one non-null entry")` when `deduped.isEmpty()`. Controller test matrix extended from `empty array returns 400` to two cases: `empty array returns 400` AND `null-only array returns 400`. See §4 orchestrator and §6 controller tests.

## 1. Memory citation and current text

`project_rm_preset_delivery_closeout` (banked memory, 31 days old at time of this adjudication — verified against current code below before asserting):

> Codex published `docs/P5_PR122_RM_PRESET_DELIVERY_MILESTONE_CLOSEOUT_*` on 2026-04-23 declaring the RM preset delivery / operator chain functionally closed. **The shipped envelope covers**: backend schedule/delivery/ledger, unified CSV + schedule across all 7 `RmReportPresetKind` values, frontend service + dialog + page wiring + operator polish, scheduled-delivery health card with four actionable drilldowns, and mocked + full-stack e2e across the full loop.
>
> **Why:** Auto-approving polish slices on this chain indefinitely is drift. PR-122 is Codex's own signal to stop.
>
> **How to apply:** ... do not pick another preset-delivery polish slice ... a new-capability Codex delivery (e.g., email channel) should be integrated normally.

The memory's "shipped envelope" lists FIVE concrete surfaces, all under the `RmReportPreset*` namespace.

## 2. Adjudication — is bulk-declare polish or new capability?

### Evidence: code-level surface separation

| Surface | Files | Forbidden by memory? |
|---|---|---|
| **RM Preset Delivery (PR-95..121 closed surface)** | `ecm-core/src/main/java/com/ecm/core/controller/RmReportPresetController.java` (`@RequestMapping("/api/v1/records/report-presets")` per `:42`), `ecm-core/src/main/java/com/ecm/core/service/RmReportPresetService.java`, `ecm-core/src/main/java/com/ecm/core/service/RmReportPresetDeliveryService.java`, `ecm-core/src/main/java/com/ecm/core/entity/RmReportPreset.java`, `RmReportPresetExecution.java`, `RmReportPresetRepository.java`, `RmReportPresetExecutionRepository.java` | **Yes** — these are the literal scoped artefacts of the closeout |
| **RM Declaration (untouched subsurface)** | `ecm-core/src/main/java/com/ecm/core/service/RecordsManagementService.java:501-543` (`declareRecord(UUID, DeclareRecordRequest)`), `:545-568` (`assignRecordCategory(UUID, UUID)`), `:570+` (`undeclareRecord`); `ecm-core/src/main/java/com/ecm/core/controller/RecordsManagementController.java:525-549` (declare/undeclare/assign-category endpoints); `RecordDeclarationDto` / `DeclareRecordRequest` records at `RecordsManagementService.java:3566-3569` | **No** — the declaration flow is its own subsurface that the closeout doc never named |

The split is structural, not nominal:

- `RmReportPresetController` is its own controller, its own `@RequestMapping("/api/v1/records/report-presets")`, its own service. Every endpoint there is "schedule / execute / deliver a preset". None of them declare a document as a record.
- `RecordsManagementController` is a different controller serving the broader RM surface (activity feeds, audit, file-plans, categories, declarations, reports). Bulk-declare would extend its `declareRecord` endpoint, not anything under `report-presets`.
- The memory's "shipped envelope" enumerates: schedule/delivery/ledger, CSV+schedule across `RmReportPresetKind`, frontend dialog wiring, scheduled-delivery health card, e2e. **None of these are declaration operations.**

### Evidence: discovery doc framing

The product-capability discovery (`docs/PRODUCT_CAPABILITY_DISCOVERY_20260524.md` §"Bulk record declaration") characterized this as "the declaration workflow (getting documents INTO the records system in the first place)", explicitly distinguished from "the preset-delivery workflow (already-declared records' scheduled reports)". The two subdomains do not share code paths.

### Evidence: page coexistence is a UX concern, not a forbidden-zone trigger

The frontend `RecordsManagementPage.tsx` does host BOTH preset-delivery UI and declaration / activity / category UI — they share a page. Adding a bulk-declare control to this same page is unavoidable. The memory's "polish" scope refers to **code surface**, not **UI page**. A new section on a shared page is acceptable so long as the slice does not modify, restructure, or extend the preset-delivery section's existing behavior.

### Adjudication

**Recommended verdict: bulk record declaration is OUTSIDE the forbidden RM-preset-delivery polish zone.** It is a new capability on the declaration subsurface (`RecordsManagementService.declareRecord` + `assignRecordCategory`), not a polish slice on `RmReportPreset*`. The memory's intent is preserved by an explicit OOS clause forbidding any touch of the `RmReportPreset*` files.

The slice should proceed only if:

1. Gate accepts this adjudication.
2. The slice's OOS list (§5 below) is enforced verbatim, especially the no-touch ring around the preset-delivery files.
3. Any UI added to `RecordsManagementPage.tsx` is in a new section (e.g., a "Bulk declarations" affordance near a records list / browser) and does not alter the existing preset-delivery section.

If the gate disagrees and reads "polish" broadly enough to cover any RM-page UI change, the slice should not open and we pivot to a non-RM next candidate (e.g., Microsoft OAuth revoke from the earlier discovery).

## 3. Scope (conditional on adjudication pass)

### In scope

- Backend: extend `RecordsManagementService` and `RecordsManagementController` with a new bulk-declare endpoint that accepts a list of `(nodeId, optional categoryId, optional comment)` triples (or a flat list of `nodeIds` + shared `categoryId` + shared `comment`; chosen below in §4).
- Per-row partial-success semantics using the same `TransactionTemplate.execute(REQUIRES_NEW)` pattern shipped in `SiteInvitationService` (2026-05-24) and `LegalHoldService` (2026-05-24). Spring proxy + same-bean `@Transactional(REQUIRES_NEW)` does NOT engage; explicit `TransactionTemplate` is mandatory. **No outer transaction** wrapping the bulk apply. **Rationale for REQUIRES_NEW per row here is row-level failure isolation, NOT parent-FK visibility** — bulk-declare has no parent entity; every Document already exists and was committed long before this call. The legal-hold parent-before-row InOrder pattern does not transfer (see §6 for the correct test shape).
- **Per-row outcome model** (carries no `ALREADY_DECLARED` error category — Finding 3):
  - Status ∈ {`DECLARED`, `SKIPPED_ALREADY_DECLARED`, `FAILED`}.
  - `errorCategory ∈ {NODE_NOT_FOUND, NODE_NOT_VISIBLE, INTERNAL_ERROR}` applies **only** when `status === FAILED`.
  - For `DECLARED` and `SKIPPED_ALREADY_DECLARED`, `errorCategory` is `null` and `errorMessage` is `null`.
- Per-row `BulkDeclareResult { nodeId, status, declaration: RecordDeclarationDto | null, errorCategory: BulkDeclareErrorCategory | null, errorMessage: String | null }`. `declaration` is populated for both `DECLARED` and `SKIPPED_ALREADY_DECLARED` (the latter mirrors single-row `RecordsManagementService.java:511` which returns `toDto(document)`); `declaration` is `null` for `FAILED`. Wrapper `BulkDeclareResults { rows: List<BulkDeclareResult> }`. Response shape `BulkDeclareResponse`.
- Frontend `RecordsManagementPage.tsx` — new "Bulk Declare" section / dialog, with paste-N-UUIDs textarea (using a copy of the `parseUuidList` helper or extracting it to a shared helper if economical), shared category dropdown (reusing the existing record-categories list endpoint), shared comment field. Aggregate result toast + partial-failure Alert with per-category rows, mirroring the legal-hold pattern.
- Frontend `recordsManagementService.ts` — new `createBulkDeclarations` method with predicate guarding `BulkDeclareResults` shape, separate sentinel for HTML-fallback diagnostics.
- Tests: per-row partial-success backend tests; controller body tests; security test for the new endpoint (admin gate consistent with existing `declareRecord`); frontend service-shape tests; page-flow tests.

### Out of scope — non-negotiable

**The slice MUST NOT touch any of the following files** (the memory-protected RM preset-delivery polish zone):

- `ecm-core/src/main/java/com/ecm/core/controller/RmReportPresetController.java`
- `ecm-core/src/main/java/com/ecm/core/service/RmReportPresetService.java`
- `ecm-core/src/main/java/com/ecm/core/service/RmReportPresetDeliveryService.java`
- `ecm-core/src/main/java/com/ecm/core/entity/RmReportPreset.java`
- `ecm-core/src/main/java/com/ecm/core/entity/RmReportPresetExecution.java`
- `ecm-core/src/main/java/com/ecm/core/repository/RmReportPresetRepository.java`
- `ecm-core/src/main/java/com/ecm/core/repository/RmReportPresetExecutionRepository.java`
- The "report-presets" sections of `recordsManagementService.ts` (preset CRUD methods, telemetry calls, scheduled-delivery-now action). Add new bulk-declare methods only; do not refactor, rename, or move existing preset methods.
- The preset-delivery section of `RecordsManagementPage.tsx` (the scheduled-delivery health card, the report-presets list/dialog, the four actionable drilldowns). Bulk-declare UI lives in a new section.
- Any `docs/P5_PR122_RM_PRESET_DELIVERY_MILESTONE_CLOSEOUT_*` files.

Also out of scope:

- Email notification on bulk declaration completion (would re-open the email channel question).
- CSV / file upload of node IDs.
- Bulk undeclare (release direction). One direction at a time.
- Background async worker (synchronous per-row).
- Schema change. The existing `rm:record` aspect + `RecordCategory` lookup tables are sufficient.
- Modifying `DeclareRecordRequest` or `RecordDeclarationDto` shape — adding a new bulk-aware DTO is fine; mutating the single-row DTO is not.
- `.env`, `application*.yml`, `docker-compose*`, `logback-spring.xml`.

## 4. Backend design

### Request shape — decision

Two viable shapes:

- **Option A (recommended): single request body** `BulkDeclareRequest { nodeIds: List<UUID>, categoryId: UUID?, comment: String? }`. The category and comment apply to all rows. Mirrors the pattern shipped in `LegalHoldService.CreateLegalHoldRequest` (with `nodeIds`) — same UX shape, same per-row partial-success semantics.
- Option B: list of per-row request objects, each carrying its own optional `categoryId` / `comment`. More flexible; but no operator workflow today asks for per-row variance — operators batch-declare "all 2023 contracts" with one category.

Recommend Option A. If a future request asks for per-row variance, extend to a `rows: List<BulkDeclareRowRequest>` shape behind the same `/bulk-declare` route.

### Endpoint

Recommend a **new endpoint** rather than overloading the existing `declareRecord`:

- `POST /api/v1/nodes/bulk-declare` — clean URL, can carry a different response DTO without breaking single-row callers, and is easier to security-test in isolation. Pluralized `/nodes/bulk-declare` (not `/node/...`) mirrors the existing `/api/v1/nodes/...` namespace that `RecordsManagementController` already declares.
- Existing single-row route `PUT /api/v1/nodes/{nodeId}/record` (`RecordsManagementController.java:523`) stays unchanged for single-row callers. v3 corrects v2's misquoted endpoint anchor.

### Service method

Mirror `LegalHoldService.createHold(...)` orchestration pattern. Concrete code outline (do not stamp into code without the verification doc):

```java
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public BulkDeclareResponse declareRecordsBulk(BulkDeclareRequest request) {
    // request shape guards
    if (request == null || request.nodeIds() == null || request.nodeIds().isEmpty()) {
        throw new IllegalArgumentException("nodeIds must contain at least one entry");
    }
    requireAdmin();  // mirror existing declareRecord gate (RecordsManagementService.java:502)

    LinkedHashSet<UUID> deduped = new LinkedHashSet<>();
    for (UUID id : request.nodeIds()) {
        if (id != null) deduped.add(id);
    }
    if (deduped.isEmpty()) {
        // v3.1 non-blocking-note guard: a caller passing { "nodeIds": [null] }
        // (or a list of only-null entries) would otherwise produce an empty
        // rows=[] 200 response, bypassing the UI parser's own dedupe+filter.
        // Reject at the boundary so the failure mode is a 400 with a clear
        // diagnostic, not a silent no-op success.
        throw new IllegalArgumentException("nodeIds must contain at least one non-null entry");
    }

    List<BulkDeclareResult> rows = new ArrayList<>(deduped.size());
    for (UUID nodeId : deduped) {
        try {
            DeclareOneOutcome outcome = bulkRowTransactionTemplate.execute(status ->
                declareOneRow(nodeId, request.categoryId(), request.comment())
            );
            // outcome is never null — declareOneRow returns a tagged outcome OR throws.
            switch (outcome.status()) {
                case DECLARED ->
                    rows.add(BulkDeclareResult.declared(nodeId, outcome.declaration()));
                case SKIPPED_ALREADY_DECLARED ->
                    rows.add(BulkDeclareResult.skippedAlreadyDeclared(nodeId, outcome.declaration()));
            }
        } catch (NodeNotFoundForDeclareException ex) {
            rows.add(BulkDeclareResult.failed(nodeId, BulkDeclareErrorCategory.NODE_NOT_FOUND, FIXED_COPY_NODE_NOT_FOUND));
        } catch (NodeNotVisibleForDeclareException ex) {
            rows.add(BulkDeclareResult.failed(nodeId, BulkDeclareErrorCategory.NODE_NOT_VISIBLE, FIXED_COPY_NODE_NOT_VISIBLE));
        } catch (RuntimeException ex) {
            log.debug("Bulk declare per-row internal error: class={}", ex.getClass().getSimpleName());
            rows.add(BulkDeclareResult.failed(nodeId, BulkDeclareErrorCategory.INTERNAL_ERROR,
                FIXED_COPY_INTERNAL + " (" + ex.getClass().getSimpleName() + ")."));
        }
    }
    return new BulkDeclareResponse(new BulkDeclareResults(rows));
}

// Internal in-band signal between helper and orchestrator. Not part of the
// HTTP response; SKIPPED_ALREADY_DECLARED here maps to the public response
// status of the same name, DECLARED to public DECLARED. FAILED is NEVER
// represented in this outcome — failures propagate as typed exceptions that
// the orchestrator catches above. Two-arity record keeps the helper total:
// every non-throwing path returns a non-null outcome with a non-null
// declaration DTO, matching the §3 row-shape invariant that both terminal
// success statuses carry a non-null declaration.
private record DeclareOneOutcome(Status status, RecordDeclarationDto declaration) {
    enum Status { DECLARED, SKIPPED_ALREADY_DECLARED }
}

private DeclareOneOutcome declareOneRow(UUID nodeId, UUID categoryId, String comment) {
    // Mirror single-row semantics at RecordsManagementService.java:501-543 verbatim.
    //
    // 1. Load + visibility/working-copy/checked-out guards (same as :503-509).
    //    A miss / not-visible throws NodeNotFoundForDeclareException /
    //    NodeNotVisibleForDeclareException; orchestrator catches and maps.
    //    These are NEW typed exceptions wrapped around the existing single-row
    //    throw sites; rationale: the orchestrator must distinguish them from
    //    generic RuntimeException (INTERNAL_ERROR).
    //
    // 2. If already declared (RecordsManagementService.java:510-512):
    //       return new DeclareOneOutcome(SKIPPED_ALREADY_DECLARED, toDto(document));
    //    DO NOT apply request.categoryId here. DO NOT write request.comment here.
    //    Single-row returns immediately at :511 without touching the :525-528
    //    categoryId block; bulk MUST behave identically (Finding 2). If an
    //    operator wants to add or change a record's category on already-declared
    //    nodes, they call the existing assignRecordCategory endpoint. Surfacing
    //    a CATEGORY_ASSIGNED status here would be a distinct bulk-assign-category
    //    capability and is OUT OF SCOPE for this slice (§3).
    //
    // 3. Else (single-row :513+ path):
    //    Call the shared non-annotated helper that BOTH declareRecord(...)
    //    public method and this helper invoke. Same-bean self-call to the
    //    @Transactional public method would NOT proxy through the transaction
    //    interceptor, so the bulk path must call into a non-annotated method
    //    that does the aspect/property/save work. If categoryId != null, the
    //    helper applies it through the same applyRecordCategory(...) call that
    //    single-row uses at :527. The shared helper writes ONE
    //    documentRepository.save (the existing :532 site). Then return
    //    new DeclareOneOutcome(DECLARED, toDto(saved)).
}
```

**Why an internal `DeclareOneOutcome` and not `BulkDeclareResult` directly (Finding 1 / v3 Blocker resolution)**

v2's helper returned `RecordDeclarationDto` and the orchestrator used `dto == null` as the `SKIPPED_ALREADY_DECLARED` signal. That conflicted with §3's invariant that `SKIPPED_ALREADY_DECLARED` carries a **non-null** `declaration` (mirroring single-row `:511`'s `toDto(document)` return). Result: every already-declared row would have been mis-classified as `DECLARED`.

Two viable v3 shapes:

- **Tagged in-band outcome (chosen)**: `DeclareOneOutcome(Status status, RecordDeclarationDto declaration)`. The orchestrator owns the public `BulkDeclareResult` factory calls, keeping `FAILED` exclusively in the catch blocks where the typed exception already names the error category.
- Alternative: have the helper return `BulkDeclareResult` directly (success and skip cases) and re-throw on failures. Rejected — pushes `nodeId` into the helper twice (it's already in scope via the closure), forces the helper to know about the public response DTO shape, and entangles the helper with the `BulkDeclareErrorCategory` enum that only the orchestrator uses.

The chosen shape keeps the helper total over the two success branches (always returns) and the orchestrator total over all three response statuses (always emits exactly one row per input UUID, success/skip/fail).

**Critical Spring transaction concern (carried forward from legal-hold slice):** the orchestrator must NOT be `@Transactional` and must NOT chain through a `@Transactional` self-call. Per-row `bulkRowTransactionTemplate.execute(REQUIRES_NEW)` is the only correct mechanism — here the rationale is **failure isolation between rows** (a thrown exception inside row N's REQUIRES_NEW boundary rolls back only that row and lets row N+1 proceed in a fresh transaction). There is no parent-FK visibility problem because there is no parent entity to commit before the row work begins. **The legal-hold `createHoldOrchestratorParentBeforeRowItem` InOrder pattern does NOT transfer.** The equivalent test for this slice locks three independent properties; see §6 (`declareRecordsBulkContinuesAfterFailedRowAndPreservesOrder`).

### Error message sanitization

Per `feedback_sanitize_throwable_cause_for_log_emission`:

- `errorMessage` in `BulkDeclareResult` MUST be a fixed copy + `ex.getClass().getSimpleName()`. Never echo `ex.getMessage()` to the response.
- Server-side log uses `log.debug` with only the class name. Never pass raw `Throwable` to SLF4J.
- Probe-shaped test: inject a `RuntimeException("BULK_DECLARE_USER_PII_PROBE")` and assert the probe substring absent from both the response `errorMessage` and the captured log emission.

### Security gate

The existing controller and service are **admin-only**, not admin/manager (v3 corrects v2's misstatement):

- Controller class-level `@PreAuthorize("hasRole('ADMIN')")` (`RecordsManagementController.java:40`) — every endpoint on `RecordsManagementController`, including the new bulk-declare route, is gated by `ROLE_ADMIN` before any handler runs.
- Service-side `requireAdmin()` is called from every mutation method (`RecordsManagementService.java:184, 193, 203, 232, 254, 284, 310, 340, 350, 378, ...` — pattern repeats). Bulk-declare must call `requireAdmin()` once at the top of the public orchestrator, before any per-row work. This produces an `AccessDeniedException` (or equivalent) for callers who bypassed the controller `@PreAuthorize` (e.g. internal callers via the service bean directly).

`RecordsManagementControllerSecurityTest` cases for the new endpoint — narrowed to the admin-only matrix (no manager case; there is no manager role to admit):

- Unauthenticated → 401.
- Authenticated non-`ROLE_ADMIN` (e.g. `ROLE_USER`, `ROLE_RECORDS_OFFICER` if such a role exists in the codebase — check current `SecurityConfig` / existing security tests for any sibling roles that should be in the denied matrix) → 403.
- Authenticated `ROLE_ADMIN` → 200, response body validated for the wrapper shape.

If a future PR introduces a records-manager role with reduced privileges (none today), the bulk-declare gate is widened in that PR, not here.

## 5. Frontend design

### Service additions

`recordsManagementService.ts` gains:

- TypeScript types: `BulkDeclareRequest`, `BulkDeclareResponse`, `BulkDeclareResults`, `BulkDeclareResult`, `BulkDeclareErrorCategory`.
- A new sentinel constant `RECORDS_MANAGEMENT_BULK_DECLARE_UNEXPECTED_RESPONSE_MESSAGE` (separate from any existing RM sentinel so Phase 5 Mocked HTML-fallback drift on the new route is debuggable from the error text alone).
- A predicate `isBulkDeclareResponse(value)` + `assertBulkDeclareResponse(value)` mirroring the patterns in `legalHoldService.ts:assertLegalHoldDetailBulkCreate`. The predicate enforces the row-shape invariants that flow from Finding 3 (so wire-shape drift is caught early instead of corrupting the UI):
  - `status === 'DECLARED'` ⇒ `declaration != null && errorCategory == null && errorMessage == null`
  - `status === 'SKIPPED_ALREADY_DECLARED'` ⇒ `declaration != null && errorCategory == null && errorMessage == null`
  - `status === 'FAILED'` ⇒ `declaration == null && errorCategory ∈ {'NODE_NOT_FOUND','NODE_NOT_VISIBLE','INTERNAL_ERROR'} && typeof errorMessage === 'string' && errorMessage.length > 0`
  - Any other combination (including `status === 'FAILED'` with `errorCategory === 'ALREADY_DECLARED'`, which would be a serialization bug) is rejected with the dedicated sentinel.
- Service method `createBulkDeclarations(request): Promise<BulkDeclareResponse>`.

### Page surface

A NEW section on `RecordsManagementPage.tsx` — call it "Bulk declare records" — with:

- Textarea for paste-N-UUIDs. Use `parseUuidList` (extract to a shared utility module if economical; alternatively duplicate within the page module since it's tiny). The parser is the same 8-4-4-4-12 generic UUID regex (NOT v4-only) shipped in `LegalHoldsPage.tsx` 2026-05-24.
- Category dropdown — reuses the existing categories list (cache hit; no new endpoint).
- Optional comment field.
- Submit button enabled only when ≥1 UUID parsed.
- Aggregate result toast: DECLARED / SKIPPED_ALREADY_DECLARED / FAILED counts.
- On partial failure: stay open, drain successful UUIDs from textarea, render per-category failed-rows Alert (data-testid hooks per `BulkDeclareErrorCategory`).

**The section must be visually separated from the preset-delivery section.** Recommended placement: under the existing declaration / records list section, before or after the existing single-row declare action. Do NOT add the bulk button into any preset-delivery section.

### Page-test surface

`RecordsManagementPage.test.tsx` (new tests if file exists, new file if not) covering:

- Bulk dialog all-declared closes + toast (aggregate counts shown).
- Partial failure stays open + drains successful UUIDs from the textarea + renders the failed-rows Alert grouped by `errorCategory` (`NODE_NOT_FOUND` / `NODE_NOT_VISIBLE` / `INTERNAL_ERROR`).
- `SKIPPED_ALREADY_DECLARED` rows are surfaced as a separate **soft-skip** group, not under the failed-rows Alert and not under "declared". Distinct visual treatment (e.g. info-color chip) so operators can see "these N nodes were already records and were not touched". Important: the test asserts these rows are NOT placed under any `errorCategory` heading — they have `errorCategory === null` per §3.
- `parseUuidList` unit tests only if the helper is extracted from `LegalHoldsPage.tsx` into a shared utility; if duplicated locally, rely on the legal-hold page's existing parser tests.

## 6. Tests

### Backend

- `RecordsManagementServiceTest.java`: extend with these named tests:
  - `declareRecordsBulkAllDeclared` — every row ends `DECLARED`; `documentRepository.save(...)` is called exactly once per row; each result row carries a non-null `declaration`.
  - `declareRecordsBulkAlreadyDeclaredSkipped` — rows already in `rm:record` state end `SKIPPED_ALREADY_DECLARED` with `errorCategory=null`, `errorMessage=null`, `declaration != null` (the DTO of the already-declared document). `documentRepository.save(...)` is **not** called on those rows (mirrors single-row `:510-512` semantics).
  - **`declareRecordsBulkAlreadyDeclaredWithCategoryRequestStillSkipsCategoryAssignment`** (Finding 2 lock) — input: a single already-declared node + a non-null `request.categoryId`. Asserts: result row is `SKIPPED_ALREADY_DECLARED`; `applyRecordCategory(...)` / `documentRepository.save(...)` are **not** invoked; no `RM_RECORD_CATEGORY_ASSIGNED` audit event is emitted; the row's `declaration.categoryId` is whatever the document already had, not the request's new id.
  - `declareRecordsBulkMissingNodeFailedPartial` — middle row throws `NodeNotFoundForDeclareException`; subsequent rows still execute and end `DECLARED`. The failed row carries `errorCategory=NODE_NOT_FOUND`.
  - `declareRecordsBulkInternalErrorSanitisedMessage` — inject a `RuntimeException("BULK_DECLARE_USER_PII_PROBE")` from the shared helper. Assert: response `errorMessage` does NOT contain the probe substring; **does** contain the exception's `getClass().getSimpleName()`; no SLF4J emission captures the raw exception.
  - **`declareRecordsBulkContinuesAfterFailedRowAndPreservesOrder`** (REPLACES the legal-hold-style parent-before-row test — Finding 1). Locks three independent properties:
    1. **Per-row TransactionTemplate engagement**: with N deduped rows, `verify(transactionManager, times(N)).getTransaction(any(TransactionDefinition.class))`. This is the load-bearing assertion that each row gets its own REQUIRES_NEW boundary.
    2. **Failure does not abort the run**: for input rows `[ok1, fail2, ok3]` (where `fail2` throws inside its `bulkRowTransactionTemplate.execute(...)`), the response carries three result rows in the same input order, with positions 0 and 2 both `DECLARED` and position 1 `FAILED`.
    3. **`documentRepository.save(...)` is invoked only on DECLARED rows**: for the same `[ok1, fail2, ok3]` input, `verify(documentRepository, times(2)).save(any(Document.class))`. The failed row's `applyOneItem` exits before `:532`, so no save site fires.
  - Constructor signature update if `RecordsManagementService` does not already inject `PlatformTransactionManager`. Test setup adds `lenient().when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus())` per the legal-hold align-fix lesson (Mockito strict-stub mode plus per-test path variance).
- `RecordsManagementControllerTest.java`: body-shape test for the new endpoint, partial-failure returns 200 not 400, empty array returns 400, **null-only array returns 400** (v3.1 — `{ "nodeIds": [null] }` must hit the post-dedupe guard, not silently produce `rows: []`).
- `RecordsManagementControllerSecurityTest.java`: unauth / non-admin / admin cases for the new endpoint.

### Frontend

- `recordsManagementService.test.ts`: ≥5 tests on the new method:
  - happy-path wrapper parse (`DECLARED` × N rows).
  - HTML-fallback (response body looks like SPA `index.html`) throws the dedicated `RECORDS_MANAGEMENT_BULK_DECLARE_UNEXPECTED_RESPONSE_MESSAGE` sentinel.
  - `SKIPPED_ALREADY_DECLARED` row with `errorCategory === null && errorMessage === null && declaration != null` accepted.
  - **Shape-drift rejection** on each illegal status×errorCategory combination — explicitly including:
    - `status === 'FAILED'` with `errorCategory === 'ALREADY_DECLARED'` (Finding 3 — there is no such error category; predicate must reject).
    - `status === 'SKIPPED_ALREADY_DECLARED'` with non-null `errorCategory`.
    - `status === 'DECLARED'` with non-null `errorMessage`.
  - Unknown `errorCategory` string (e.g. backend adds a new variant) rejected with the sentinel.
- `RecordsManagementPage.test.tsx`: ≥4 tests for the new bulk section:
  - all-declared closes the dialog + emits an aggregate toast with the correct count.
  - partial-failure stays open + drains successful UUIDs from the textarea + renders the `errorCategory`-grouped Alert.
  - `SKIPPED_ALREADY_DECLARED` rows appear in their own soft-skip group, not under any `errorCategory` heading.
  - submit gated on `parseUuidList(textarea) ≥ 1` (no UUIDs → submit disabled).

## 7. Verification

Same local-then-CI pattern as the prior two slices:

```bash
# Backend targeted (Docker-blocked locally; CI is the gate)
./ecm-core/mvnw -Dtest='RecordsManagementServiceTest,RecordsManagementControllerTest,RecordsManagementControllerSecurityTest' test

# Frontend targeted
cd ecm-frontend
CI=true npm test -- --runTestsByPath \
  src/services/recordsManagementService.test.ts \
  src/pages/RecordsManagementPage.test.tsx \
  --watchAll=false
npm run lint
CI=true npm run build

git diff --check -- . ':!.env'
```

7-job CI gate (Backend Verify, Frontend Build & Test, Phase C Security, Acceptance Smoke, Property Encryption Closeout, Phase 5 Mocked Regression, Frontend E2E Core).

Anticipated align fixes: per past two slices, expect at least one CI #1 failure on either Mockito strict-stub (pre-empt by reviewing each new test against the `feedback_per_slice_fix_commit_stages_code_and_test` discipline + the new "guard-path tests must not pre-stub dependencies not called" lesson from 2026-05-24) or Liquibase / migration / schema validation. If the slice adds NO migration (recommended — see §3 OOS), Liquibase risk drops to near zero.

## 8. Commit sequence

Per the established `fix(core) → docs(core) → align(if needed) → docs(core) [skip ci]` cadence.

## 9. Decision points

| Decision | Recommendation | Why |
|---|---|---|
| Forbidden-zone adjudication | **Bulk-declare is OUTSIDE the RM-preset-delivery polish zone.** | Memory's "shipped envelope" lists only `RmReportPreset*` surfaces; `declareRecord` lives in a different controller / service / package |
| Request shape | **Single body** `{ nodeIds, categoryId?, comment? }`, shared across all rows | Mirrors `LegalHoldService` shipped pattern; per-row variance OOS for v1 |
| Endpoint | **New** `POST /api/v1/nodes/bulk-declare` (mirrors the existing `/api/v1/nodes/...` namespace; v3 corrects v2's `/records/...` placeholder which did not match the existing controller route prefix) | Separate URL, separate response DTO, clean security test surface |
| Helper return protocol (Finding 1 / v3 Blocker) | **Tagged `DeclareOneOutcome(Status, RecordDeclarationDto)`** record; orchestrator switches on status | v2's `dto == null` signal collided with §3's invariant that `SKIPPED_ALREADY_DECLARED` carries a non-null DTO; in-band tag avoids that collision |
| Security gate (Finding 3 / v3) | **Admin-only** — `@PreAuthorize("hasRole('ADMIN')")` + service `requireAdmin()`; security test matrix is `{unauth → 401, non-admin → 403, admin → 200}`, no manager case | Current codebase has no records-manager role at `RecordsManagementController.java:40` or `RecordsManagementService.java:184+`; admitting one in tests would over-broaden the gate |
| Outer transaction | **NOT_SUPPORTED** orchestrator; one `bulkRowTransactionTemplate` (REQUIRES_NEW) for per-row work; no parent template because there is no parent insert | Failure isolation only; no parent-FK visibility concern |
| `ALREADY_DECLARED` modeling (Finding 3) | **`Status.SKIPPED_ALREADY_DECLARED`** with `errorCategory === null` | It is not an error; conflating it with the error-category union breaks predicate symmetry on the frontend |
| Error categories (closed set, Finding 3) | **`{NODE_NOT_FOUND, NODE_NOT_VISIBLE, INTERNAL_ERROR}`** — `ALREADY_DECLARED` is NOT in this set | A row's `errorCategory` is non-null iff `status === FAILED` |
| Already-declared row + non-null `request.categoryId` (Finding 2) | **No-op on category**; row is `SKIPPED_ALREADY_DECLARED`; declaration DTO returns the document's existing category, not the request's | Verbatim mirror of single-row `RecordsManagementService.java:510-512` early-return; cross-row bulk-assign-category is a distinct capability and OOS |
| Order / failure-isolation test (Finding 1) | **`declareRecordsBulkContinuesAfterFailedRowAndPreservesOrder`** with three independent property locks (see §6) | Bulk-declare has no parent entity; legal-hold parent-before-row InOrder does NOT transfer |
| INTERNAL_ERROR sanitization | **Fixed copy + `ex.getClass().getSimpleName()` only**, never `ex.getMessage()`, never raw `Throwable` to SLF4J | `feedback_sanitize_throwable_cause_for_log_emission` |
| UI placement | **New section on `RecordsManagementPage.tsx`**, NOT inside preset-delivery section | Honors the memory's polish-zone intent at the UI level |
| Schema | **No migration** | `rm:record` aspect + existing category lookup tables sufficient |

## 10. What this brief does not commit to

- No code, no test, no migration, no doc beyond this brief itself.
- No commits made.
- Gate adjudication on §2 is the first decision required. If gate disagrees, the slice does not open.
- If gate accepts §2 and authorizes the slice, the executor reads §3-8 and proceeds; the implementation follows the legal-hold slice's commit cadence + align-fix discipline.

## Verification (this brief)

```bash
git status --short
# Expected: M .env + this brief only.

git diff --check -- . ':!.env'
# Expected: clean.

git diff --stat -- 'ecm-core/src/main/java/' 'ecm-core/src/test/' 'ecm-frontend/' 'ecm-core/src/main/resources/'
# Expected: empty (no code touched).
```

Confirmed at time of writing.
