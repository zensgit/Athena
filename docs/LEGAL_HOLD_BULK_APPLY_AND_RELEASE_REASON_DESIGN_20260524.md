# Legal-Hold Bulk-Apply + Structured Release-Reason — Implementation Brief

Date: 2026-05-24

## Context

Top 3 candidate #2 from `docs/PRODUCT_CAPABILITY_DISCOVERY_20260524.md`. The legal-hold subsystem already ships per-row CRUD (`LegalHoldController.java:42-75`) including a multi-node `addItems(holdId, AddHoldItemsRequest)` flow (`LegalHoldService.java:73-96`) and a release endpoint that accepts an optional free-text `comment` (`LegalHoldService.java:107-117`). Two operator gaps remain:

1. **Bulk-apply** — creating a hold + populating it with items requires two HTTP requests + two dialog opens. Operators routinely apply a single hold to N nodes (litigation discovery, regulator request) and the create-then-add UX surfaces as friction.
2. **Structured release reason** — `release_comment` is free-text and often blank; compliance reviewers requesting a hold-release audit get only operator-keyed prose. A structured enum (LITIGATION_ENDED, SCHEDULED_DISPOSITION, REQUEST_BY_REQUESTOR, OTHER) makes the audit trail machine-readable while still permitting an optional comment for context.

The two halves are bundled because they share the same controller, the same dialog page, the same tests, and the same admin/manager security gate. Shipping them together is roughly the same effort as either alone.

## Scope

### In scope

- Backend extension of `createHold` to optionally accept `nodeIds: List<UUID>` in `CreateLegalHoldRequest`. Per-row partial-success semantics for the item-population sub-step (mirrors the bulk-site-invitation slice's `TransactionTemplate.execute(...)` per row pattern).
- Backend new `HoldReleaseReason` enum (LITIGATION_ENDED, SCHEDULED_DISPOSITION, REQUEST_BY_REQUESTOR, OTHER).
- Backend new column `legal_holds.release_reason VARCHAR(32) NULL` — **nullable** so existing RELEASED rows remain legal-valid without backfill.
- Backend `ReleaseLegalHoldRequest` extension: add required `releaseReason: HoldReleaseReason` field; reject release attempts without a reason (HTTP 400) for new releases.
- Backend `LegalHoldDto` + `LegalHoldSummaryDto` extension: add `releaseReason: HoldReleaseReason | null`. Null indicates a legacy release predating this slice.
- Frontend `CreateLegalHoldDialog` extension: add optional "Node IDs" textarea, parse + dedupe via the same helper shape as bulk-site-invitation (`parseUuidList`).
- Frontend `ReleaseLegalHoldDialog`: dropdown required + comment optional; submit disabled until a reason is chosen.
- Frontend `LegalHoldsPage`: show `releaseReason` chip on released holds (or `Legacy release` chip when null).
- Migration `094-add-legal-hold-release-reason.xml` (next free index after `093-add-oauth-credential-revoke-endpoint.xml`).
- Targeted backend tests (service + controller + controller security) + targeted frontend tests (service + page) per the established pattern.

### Out of scope (explicit)

- Schema change to `release_comment` (kept TEXT nullable as-is).
- Per-row role / per-row reason override (single reason applies to the release event).
- Multi-hold bulk release (release one hold at a time; mass-release across N holds is a separate slice if requested).
- New `ReleaseLegalHoldBulkResponse` for any kind of multi-row release — release path is single-row.
- Backfilling existing released holds with a default reason (legacy rows keep `release_reason = NULL`).
- Removing or renaming any existing enum value or DTO field.
- Auto-classification heuristics ("guess the reason from hold name / comment text") — reason is operator-chosen only.
- CSV / file import of node IDs for bulk-apply.
- Background worker for bulk-apply — synchronous per-row save in the same request.
- New email notification on hold release (current behavior: no email; preserve).
- Anything that touches `Folder` / `Node` / `Trash` / `Version` services beyond what the existing `requireLiveNode(...)` invocation already does.
- `.env` / `application*.yml` / `docker-compose*` / `logback-spring.xml`.

## Required reading before code work begins

- `ecm-core/src/main/java/com/ecm/core/entity/LegalHold.java` — entity shape, `HoldStatus` enum, existing nullable fields (`releasedAt`, `releasedBy`, `releaseComment`).
- `ecm-core/src/main/java/com/ecm/core/service/LegalHoldService.java` — full file. Especially `createHold` (`:59-71`), `addItems` (`:73-96`), `releaseHold` (`:107-117`), `requireLiveNode` (`:235-242`), and the existing records at the bottom of the file.
- `ecm-core/src/main/java/com/ecm/core/controller/LegalHoldController.java` — class-level `@PreAuthorize("hasRole('ADMIN')")`. New endpoint(s) inherit this without re-declaring.
- `ecm-core/src/test/java/com/ecm/core/service/LegalHoldServiceTest.java`, `LegalHoldControllerTest.java`, `LegalHoldControllerSecurityTest.java` — for test patterns.
- `ecm-frontend/src/services/legalHoldService.ts` and `.test.ts` — service shape, predicate idiom.
- `ecm-frontend/src/pages/LegalHoldsPage.tsx` — current create + release dialog structure.
- `docs/SITE_INVITATION_BULK_CREATE_DESIGN_VERIFICATION_20260524.md` — direct precedent for the `TransactionTemplate` per-row pattern and partial-success result list. Especially §"Align-fix narrative" — the Mockito strict-stub trap is identical and pre-emptively avoidable here.
- `ecm-core/src/main/resources/db/changelog/changes/092-add-site-invitation-send-tracking.xml` — example of a nullable-column add migration in the active style.
- `ecm-core/src/main/resources/db/changelog/db.changelog-master.xml` (or equivalent index) — the master changelog that includes per-migration files.

## Backend design

### Endpoint surface (no new HTTP routes — both halves are extensions)

Both halves extend existing endpoints with backward-compatible request shape additions. No new routes; no controller class changes beyond the `@RequestBody` record extensions.

| Route | Method | Change |
|---|---|---|
| `POST /api/v1/legal-holds` | extend `CreateLegalHoldRequest` to accept optional `nodeIds: List<UUID>` (default empty / null → identical to current behavior). Existing callers remain compatible; `LegalHoldDto` gains nullable / additive fields (`releaseReason: HoldReleaseReason \| null` and `bulkApplyResults: BulkApplyResults \| null`) — see §"Response DTOs" for the full shape. |
| `POST /api/v1/legal-holds/{holdId}/release` | extend `ReleaseLegalHoldRequest` to require `releaseReason: HoldReleaseReason` (non-null). Reject releases with null reason via HTTP 400. |

**Why no new routes:**

- `POST /legal-holds` already creates holds. Adding optional `nodeIds` is a strict additive change; existing callers continue to work.
- A separate `/legal-holds/with-items` route would have been justified if the create + items shape was incompatible, but `nodeIds` slots cleanly into `CreateLegalHoldRequest`. Two routes for the same operation creates a wider security/test surface for no gain.
- `releaseReason` becoming required is a backward-incompatible change to the request shape, but the production callers of release are all under the same admin role gate and are about to be updated in the same slice (frontend dialog). External API callers — if any — get a deterministic 400 with a clear message; not silent.

### Request DTOs

```java
public record CreateLegalHoldRequest(
    String name,
    String description,
    // NEW: optional. Empty/null/missing → current behavior (no items added).
    // When present, each entry is best-effort applied via the same per-row
    // partial-success semantics as bulk-site-invitation (TransactionTemplate
    // per row, REQUIRES_NEW). Missing nodes / tenant-invisible nodes do NOT
    // roll back the hold creation or the rows that did succeed.
    List<UUID> nodeIds
) {}

public record ReleaseLegalHoldRequest(
    // NEW: required for new release events. Releases attempted without a
    // reason throw IllegalArgumentException → 400. Existing RELEASED rows
    // predating this slice keep release_reason = NULL (no backfill).
    HoldReleaseReason releaseReason,
    String comment
) {}

public enum HoldReleaseReason {
    LITIGATION_ENDED,
    SCHEDULED_DISPOSITION,
    REQUEST_BY_REQUESTOR,
    OTHER
}
```

Place `HoldReleaseReason` as a public top-level enum inside `LegalHold.java` (sibling to `HoldStatus`) so JPA's `@Enumerated(EnumType.STRING)` mapping is straightforward, and to keep enum-of-enum convention with the existing `HoldStatus`.

### Response DTOs

```java
public record LegalHoldSummaryDto(
    UUID id,
    String name,
    String description,
    LegalHold.HoldStatus status,
    long itemCount,
    String createdBy,
    LocalDateTime createdDate,
    String releasedBy,
    LocalDateTime releasedAt,
    // NEW: null when status==ACTIVE OR when hold was released BEFORE this
    // slice landed (legacy rows). Frontend renders "Legacy release" chip
    // when status==RELEASED && releaseReason==null.
    HoldReleaseReason releaseReason
) {}

public record LegalHoldDto(
    // ... existing fields ...
    HoldReleaseReason releaseReason  // NEW
) {}
```

Per-row bulk-apply outcome — same shape as bulk-site-invitation's `BulkInviteResult`, but inlined into the create response rather than a top-level list, because we already return `LegalHoldDto`. The recommended shape is **a wrapper record `BulkApplyResults` containing the rows list**, exposed as `bulkApplyResults: BulkApplyResults | null` (single nullable field on `LegalHoldDto`). Gate review 2026-05-24 corrected an earlier inconsistency in this section where the text said "List<BulkApplyResult>" and the code block defined the wrapper — the wrapper is the canonical shape.

```java
public record LegalHoldDto(
    // ... existing fields including the new releaseReason ...
    BulkApplyResults bulkApplyResults   // null when no nodeIds supplied in the request;
                                        // non-null and populated per-row otherwise.
) {}

public record BulkApplyResults(
    List<BulkApplyResult> rows
) {}

public record BulkApplyResult(
    UUID requestedNodeId,                // raw input UUID; surfaces even on FAILED rows
    Status status,
    LegalHoldItemDto item,               // non-null on ADDED; null on SKIPPED_DUPLICATE / FAILED
    BulkApplyErrorCategory errorCategory,
    String errorMessage
) {
    public enum Status { ADDED, SKIPPED_DUPLICATE, FAILED }
}

public enum BulkApplyErrorCategory {
    NODE_NOT_FOUND,
    NODE_NOT_VISIBLE,
    INTERNAL_ERROR
}
```

**Why the wrapper, not a bare `List<BulkApplyResult>`:** A wrapper record gives future fields (per-batch counters, timing, idempotency token) somewhere to land without changing the top-level DTO field shape. The frontend predicate validates `bulkApplyResults: BulkApplyResults | null` and `bulkApplyResults?.rows` independently; bare `List<BulkApplyResult>` would also work but locks in a flatter shape.

The alternative considered — a separate endpoint that returns the bulk-apply result and leaves `LegalHoldDto` unchanged — was rejected: existing frontend callers already destructure `LegalHoldDto`; adding a nullable optional field is more compatible than splitting the response into two shapes.

### Transactional strategy (orchestration pattern — gate-corrected 2026-05-24)

**Initial draft of this brief was wrong.** I proposed keeping `createHold` `@Transactional` and spawning per-row `REQUIRES_NEW` inner transactions for items. Gate review flagged: `LegalHoldService` is **class-level `@Transactional`** (`:30`), so the outer transaction is engaged through Spring's proxy whenever the method is called externally. Per-row `REQUIRES_NEW` SUSPENDS the outer transaction and starts a fresh DB transaction, and **a freshly-started transaction cannot see writes from the still-uncommitted outer transaction**. `LegalHoldItem.hold_id` is a non-null FK (`LegalHoldItem.java:36`), so inner row inserts would hit FK violations against a parent hold the inner transaction can't see. This is invisible in pure-Mockito unit tests (repositories mocked) but always fires in real DB / integration tests.

**Corrected pattern: parent-first orchestration.**

The orchestrator method itself runs **outside any transaction**. The parent hold is committed in its own independent transaction. Once the parent is committed (and therefore visible to other transactions), per-row item inserts run in independent REQUIRES_NEW transactions that read the now-committed parent through the FK without trouble.

Two `TransactionTemplate` fields on the service:

- `parentHoldTransactionTemplate` — built with default `PROPAGATION_REQUIRED` (it will create a fresh transaction because the orchestrator is non-transactional).
- `bulkRowTransactionTemplate` — built with `PROPAGATION_REQUIRES_NEW` for per-row independent commit.

Both built from the injected `PlatformTransactionManager` in the explicit constructor. (Reuse the same `transactionManager` field; no extra dependency.)

The orchestrator method must NOT inherit the class-level `@Transactional`. Two ways to achieve this:

- **Preferred: annotate the orchestrator explicitly with `@Transactional(propagation = Propagation.NOT_SUPPORTED)`.** Smallest blast radius; keeps the rest of `LegalHoldService` under its existing class-level `@Transactional` semantics. NOT_SUPPORTED tells Spring's proxy: "if a transaction is active, suspend it; run this method outside any transaction; resume the original on exit."
- Alternative: remove the class-level `@Transactional` and re-annotate every other public method explicitly. Larger refactor; not justified for this slice.

Recommended sketch:

```java
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public LegalHoldDto createHold(CreateLegalHoldRequest request) {
    requireAdmin();
    if (request == null || !StringUtils.hasText(request.name())) {
        throw new IllegalArgumentException("Legal hold name is required");
    }

    // Step 1: commit the parent hold in its own transaction.
    LegalHold saved = parentHoldTransactionTemplate.execute(status -> {
        LegalHold hold = new LegalHold();
        hold.setName(request.name().trim());
        hold.setDescription(trimToNull(request.description()));
        return legalHoldRepository.save(hold);
    });
    log.info("Created legal hold {} ({})", saved.getName(), saved.getId());

    // Step 2: per-row apply, only when nodeIds were supplied. Each row runs
    // in REQUIRES_NEW — the parent hold is now committed and visible.
    BulkApplyResults applyResults = null;
    if (request.nodeIds() != null && !request.nodeIds().isEmpty()) {
        applyResults = applyBulkItems(saved.getId(), request.nodeIds());
    }

    // Step 3: read-only reload (visibility-filtered items) inside its own
    // transaction. We rebuild the DTO so the bulkApplyResults field can be
    // attached non-null when items were attempted.
    return readOnlyReloadWithBulkResults(saved.getId(), applyResults);
}

private LegalHoldDto readOnlyReloadWithBulkResults(UUID holdId, BulkApplyResults bulkApplyResults) {
    // Use a read-only TransactionTemplate (or call the existing getHold(holdId)
    // which is already @Transactional(readOnly = true) via its method-level
    // annotation — cross-bean self-call still does not engage the proxy, so
    // either inject a self-reference or use a TransactionTemplate here too).
    // The cleanest path is a small TransactionTemplate.execute with
    // setReadOnly(true) wrapping the same body that getHold(holdId) runs.
    ...
}

private BulkApplyResults applyBulkItems(UUID holdId, List<UUID> nodeIds) {
    List<BulkApplyResult> rows = new ArrayList<>(nodeIds.size());
    for (UUID nodeId : nodeIds) {
        try {
            LegalHoldItemDto added = bulkRowTransactionTemplate.execute(status ->
                applyOneItem(holdId, nodeId)
            );
            if (added == null) {
                rows.add(BulkApplyResult.skippedDuplicate(nodeId));
            } else {
                rows.add(BulkApplyResult.added(nodeId, added));
            }
        } catch (NodeNotFoundForHoldException ex) {
            rows.add(BulkApplyResult.failed(nodeId, BulkApplyErrorCategory.NODE_NOT_FOUND, ...));
        } catch (NodeNotVisibleForHoldException ex) {
            rows.add(BulkApplyResult.failed(nodeId, BulkApplyErrorCategory.NODE_NOT_VISIBLE, ...));
        } catch (RuntimeException ex) {
            log.debug("Bulk apply per-row internal error: holdId={} class={}", holdId, ex.getClass().getSimpleName());
            rows.add(BulkApplyResult.failed(nodeId, BulkApplyErrorCategory.INTERNAL_ERROR, ...));
        }
    }
    return new BulkApplyResults(rows);
}

private LegalHoldItemDto applyOneItem(UUID holdId, UUID nodeId) {
    // Runs inside a REQUIRES_NEW transaction (driven by bulkRowTransactionTemplate
    // in the caller). Loads the now-committed parent hold via repository, then
    // applies the existing per-row logic from addItems(...): existsBy → continue
    // (return null to signal duplicate), requireLiveNode, save LegalHoldItem.
    LegalHold hold = legalHoldRepository.findById(holdId)
        .orElseThrow(() -> new IllegalStateException(
            "Parent hold disappeared between commit and per-row apply: " + holdId
        ));
    if (legalHoldItemRepository.existsByHoldIdAndNodeId(holdId, nodeId)) {
        return null;  // signals SKIPPED_DUPLICATE
    }
    Node node = requireLiveNodeOrTyped(nodeId);  // throws typed exceptions
    LegalHoldItem item = new LegalHoldItem();
    item.setHold(hold);
    item.setNode(node);
    item.setNodeType(node.getNodeType());
    item.setNodePath(node.getPath());
    item.setAddedBy(securityService.getCurrentUser());
    LegalHoldItem savedItem = legalHoldItemRepository.save(item);
    return new LegalHoldItemDto(node.getId(), node.getName(), node.getNodeType().name(),
        node.getPath(), savedItem.getAddedAt(), savedItem.getAddedBy());
}
```

**Why this is correct, restated:**

- The orchestrator `createHold` is `NOT_SUPPORTED` — no outer transaction is active when its body runs.
- `parentHoldTransactionTemplate.execute(...)` creates a fresh transaction, commits the LegalHold, and returns. After execute returns, the parent IS committed in the DB.
- `bulkRowTransactionTemplate.execute(...)` per row creates another fresh transaction (REQUIRES_NEW from a no-transaction starting point = same thing as REQUIRED here, but explicit). Each per-row transaction sees the already-committed parent through the FK.
- A row failure throws inside the template; the template rolls back THAT row's transaction only; the loop continues; previously-committed rows stay committed.
- Read-only reload runs in its own short transaction, projects the DTO.

**Trade-offs accepted:**

- If the orchestrator crashes between Step 1 (parent committed) and Step 3, the operator sees a 5xx response but the parent hold exists with whatever rows committed so far. This is acceptable for an admin tool; the operator reloads the holds page and sees the partial state. The alternative ("compensating delete of the parent on bulk failure") is significantly more complex and rarely useful — if the operator's intent was "create hold with these N nodes" and the orchestrator died, leaving the hold without items is preferable to deleting a hold the operator just created.
- A small race exists between Step 1's commit and Step 2's per-row reads: another transaction could `releaseHold(holdId)` in the gap. The per-row apply would then attempt to add items to a now-RELEASED hold. The fix is the per-row helper's check on hold status; reject with `IllegalStateException` mapped to `INTERNAL_ERROR`. This race is operationally implausible (operators don't release a hold they just created) but documented.

### Release flow

```java
public LegalHoldDto releaseHold(UUID holdId, ReleaseLegalHoldRequest request) {
    requireAdmin();
    LegalHold hold = requireActiveHold(holdId);
    if (request == null || request.releaseReason() == null) {
        throw new IllegalArgumentException("releaseReason is required");
    }
    hold.setStatus(LegalHold.HoldStatus.RELEASED);
    hold.setReleasedAt(LocalDateTime.now());
    hold.setReleasedBy(securityService.getCurrentUser());
    hold.setReleaseReason(request.releaseReason());
    hold.setReleaseComment(trimToNull(request.comment()));
    legalHoldRepository.save(hold);
    log.info(
        "Released legal hold {} ({}) with reason {}",
        hold.getName(),
        hold.getId(),
        request.releaseReason()
    );
    return getHold(holdId);
}
```

### Per-row error sanitization

Mirror the bulk-site-invitation slice's discipline:

- `errorMessage` carries a stable fixed copy + `ex.getClass().getSimpleName()` only. Never echo `ex.getMessage()` to the response.
- Server-side log: `log.debug("Bulk apply per-row internal error: holdId={} class={}", holdId, ex.getClass().getSimpleName())`. Never pass raw `Throwable` to SLF4J (per `feedback_sanitize_throwable_cause_for_log_emission`).

### Schema migration

New file `ecm-core/src/main/resources/db/changelog/changes/094-add-legal-hold-release-reason.xml`. Add to the master changelog index in the same commit.

```xml
<changeSet id="094-add-legal-hold-release-reason" author="athena">
    <addColumn tableName="legal_holds">
        <column name="release_reason" type="varchar(32)">
            <constraints nullable="true"/>
        </column>
    </addColumn>
</changeSet>
```

Length 32 covers all 4 enum names with headroom for future additions (longest current value `SCHEDULED_DISPOSITION` is 21 chars).

**Null-tolerant compatibility:** existing RELEASED rows have `release_reason = NULL` after the migration. The `LegalHoldDto.releaseReason` field surfaces null to the frontend; the page renders a `Legacy release` chip. No backfill runs. Backfill is intentionally OOS — adding a default reason to legacy rows would misrepresent the audit trail; "we don't know why this old hold was released" is the honest signal.

## Frontend design

### `CreateLegalHoldDialog`

- Existing fields stay (`name`, `description`).
- New optional `Node IDs` textarea (multiline, `rows={4}`). Parse via a new exported helper `parseUuidList(raw)` that splits on newline / comma / semicolon, trims, lowercases (UUIDs are case-insensitive in hex digits), drops blanks + duplicates, and validates as a **generic UUID string** (8-4-4-4-12 hex format) — **not** a v4-only regex. Gate review 2026-05-24 corrected the earlier "UUID v4 regex" wording: backend node IDs use `GenerationType.UUID` (`BaseEntity.java:27`) which permits any variant, and historical / imported / test data may legitimately carry non-v4 UUIDs. The frontend must not silently drop non-v4 IDs that the backend would accept; defer existence validation to the backend's `requireLiveNode(...)`. Helper text: `Optional. Newline-, comma-, or semicolon-separated UUIDs. Blank, duplicate, and malformed UUIDs are ignored.` Recommended regex: `/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/` (post-lowercase).
- Submit shows `Create hold` when textarea empty, or `Create hold and apply to N nodes` when populated.
- After submit, the response's `bulkApplyResults` (if non-null) drives the same partial-success Alert pattern as bulk-site-invitation: stay open + drain successful UUIDs + show the failed list in a dedicated Alert when any row failed. If all rows ADDED (no SKIPPED, no FAILED), close the dialog and toast success count.

### `ReleaseLegalHoldDialog`

- Add a `Select` for `Release reason` (4 enum options, MUI `MenuItem` per value, human-readable labels: "Litigation ended", "Scheduled disposition", "Requested by requestor", "Other").
- Existing comment field stays optional.
- Submit disabled until a reason is selected. Submit calls `releaseHold(holdId, { releaseReason, comment })`.
- On HTTP 400 from missing reason (defensive — should not happen because of the client-side disable), surface `Failed to release: a release reason is required.` toast and reopen / preserve dialog state.

### `LegalHoldsPage`

- New `Release reason` column on the released-holds table (or chip overlay on the existing row).
- Released holds with `releaseReason === null` render a muted `Legacy release` chip (e.g. `<Chip label="Legacy release" size="small" variant="outlined" />`).
- Released holds with a known reason render a colored chip per reason: green for `LITIGATION_ENDED`, blue for `SCHEDULED_DISPOSITION`, amber for `REQUEST_BY_REQUESTOR`, default for `OTHER`.

### Service-shape guard + sentinel

- Add `LEGAL_HOLD_BULK_CREATE_UNEXPECTED_RESPONSE_MESSAGE` and `LEGAL_HOLD_RELEASE_UNEXPECTED_RESPONSE_MESSAGE` sentinels (separate so Phase 5 Mocked HTML-fallback drift on either new request shape is debuggable from the error text alone — per `feedback_phase5_mocked_html_fallback`).
- Extend `assertLegalHoldDto` (or equivalent existing helper) to also assert `releaseReason: HoldReleaseReason | null` and the optional `bulkApplyResults` shape (null OR a structurally-valid `BulkApplyResults`).

## Tests

### Backend (Java; CI is the authoritative gate per Docker-blocked mvnw)

| File | New tests |
|---|---|
| `LegalHoldServiceTest.java` | Constructor signature update (inject `PlatformTransactionManager` mock with lenient `getTransaction → SimpleTransactionStatus` to avoid Mockito strict-stub trap; learn from the `inviteBulkEmptyArrayThrows` align fix in `2da7fd6`). New tests: `createHoldWithNodeIdsAllAdded` (all 3 nodes ADDED, BulkApplyResults populated), `createHoldWithNodeIdsPartialFailureDoesNotRollback` (1 node missing → FAILED row, hold + other 2 items committed), `createHoldWithDuplicateNodeIdSkipped` (same UUID twice → second is SKIPPED_DUPLICATE), `createHoldWithoutNodeIdsLeavesBulkApplyResultsNull` (back-compat), `createHoldBulkApplyInternalErrorSanitisedMessage` (probe `USER_PII_PROBE` injected via mock; assert absent from `errorMessage`; class name preserved), `releaseHoldRequiresReleaseReason` (null reason → IllegalArgumentException), `releaseHoldWithReleaseReasonPersistsValue` (verify the enum reaches the saved entity), `releaseHoldNullToleranceLegacyRow` (a hold released before the migration with `release_reason=null` round-trips through `getHold` as `releaseReason=null` without throwing). Plus a `getHoldReturnsBulkApplyResultsNullByDefault` if the caller didn't pass `nodeIds`. |
| `LegalHoldControllerTest.java` | New tests: `createHoldWithNodeIdsBodyShape` (post body includes `nodeIds`; response includes `bulkApplyResults`), `releaseHoldWithoutReasonReturns400`, `releaseHoldWithReasonReturns200WithReleaseReasonInBody`. |
| `LegalHoldControllerSecurityTest.java` | Class-level `@PreAuthorize("hasRole('ADMIN')")` already covers all routes; **no new test cases required for the existing pattern**, but two existing-cases adapted to use the new request shape (so the security-test build doesn't break on the DTO field addition). Verify by `WithMockUser(roles="USER")` → 403 on the create-with-items request body still holds. |

### Frontend (Jest)

| File | New tests |
|---|---|
| `legalHoldService.test.ts` | `createHold with nodeIds parses bulkApplyResults`, `createHold without nodeIds: bulkApplyResults is null`, `releaseHold with releaseReason forwards payload`, `assertLegalHoldDto rejects unknown releaseReason values`, HTML fallback throws the right sentinel for both endpoints. |
| `LegalHoldsPage.test.tsx` | `parseUuidList` unit tests (split / dedupe / invalid UUID rejection / case-insensitive). Dialog flow: create-with-items all-success closes dialog + prepends rows; partial-failure stays open + drains UUIDs in textarea + Alert listing FAILED rows. Release dialog: submit disabled until reason selected; selecting reason enables submit; selecting + comment passes both to service. `Legacy release` chip for a released hold with null `releaseReason`. Reason-specific chips for each enum value. |

### Test-discipline notes (pre-emptive)

Per the `2da7fd6` lesson:

- Tests that exercise the guard path (e.g. `releaseHoldRequiresReleaseReason` where null reason throws BEFORE any save) **must not** pre-stub repository / security dependencies that won't be exercised. Either don't call shared `setUp*Base*` helpers or use `Mockito.lenient()` on the unused stubs.
- Tests that drive `applyBulkItems` to a failure should stub specifically the path the failure hits, not the whole upstream chain.

Per the orchestration-pattern correction (gate review 2026-05-24):

- `LegalHoldService` now has TWO `TransactionTemplate` fields built from the same `PlatformTransactionManager`. Unit tests cannot drive real transactions; both templates need their `execute(...)` calls stubbed to invoke the callback. The standard pattern is to stub `transactionManager.getTransaction(any(TransactionDefinition.class))` to return a `SimpleTransactionStatus()` (as in `SiteInvitationServiceTest.newService`); both templates then run their callbacks against the same mocked manager. Mockito strict-stub mode does not flag this if the stub is set up leniently and is consumed by at least one template path; if a test exercises only the guard branch and neither template runs, drop the stub for that specific test (same trap as `inviteBulkEmptyArrayThrows`).
- A new dedicated unit test `createHoldOrchestratorRunsOutsideOuterTransaction` should verify orchestration order: parent save commits BEFORE any per-row apply runs. The simplest lock is to record method-call order on a Mockito `InOrder` and assert `legalHoldRepository.save(any(LegalHold.class))` precedes `legalHoldItemRepository.save(any(LegalHoldItem.class))` calls.
- An integration test (Spring Boot test slice with H2 or testcontainers Postgres, if such infrastructure exists in the project) **would** be the strongest lock against the FK-visibility trap that motivated this correction. Without it, a future regression that reintroduces an outer `@Transactional` on `createHold` would pass unit tests + only fail under real DB. If the project has a `@DataJpaTest`-shaped integration profile, add one `createHoldBulkApplyParentVisibleBeforeRowInsert` test there.

## Verification

### Local

```bash
# Backend targeted (will be Docker-blocked on the dev box; CI is the gate)
./ecm-core/mvnw -Dtest='LegalHoldServiceTest,LegalHoldControllerTest,LegalHoldControllerSecurityTest' test

# Frontend targeted
cd ecm-frontend
CI=true npm test -- --runTestsByPath \
  src/services/legalHoldService.test.ts \
  src/pages/LegalHoldsPage.test.tsx \
  --watchAll=false
npm run lint
CI=true npm run build

# Whitespace
git diff --check -- . ':!.env'
```

### Expected CI gate (7 / 7 green)

- Backend Verify — load-bearing on the new service / controller / migration; the migration runs in the Liquibase boot phase of the integration test profile
- Frontend Build & Test
- Phase C Security Verification — `requireAdmin()` is the gate for all legal-hold operations; new code paths inherit it
- Acceptance Smoke (3 admin pages)
- Property Encryption Closeout Gate
- Phase 5 Mocked Regression Gate — load-bearing on the new bulk + release sentinels
- Frontend E2E Core Gate

## Commit sequence

Per past slice pattern (e.g. `e7f76c3` + `5d374a3` + `2da7fd6` + `2e6406a`):

1. **`fix(core):`** — backend production (service, controller, migration, entity if necessary) + frontend production + tests + migration master-changelog include, in one commit (`feedback_per_slice_fix_commit_stages_code_and_test`: stage code AND test in one commit).
2. **`docs(core):`** — verification doc recording local outcomes.
3. (Conditional) **`test(core):`** — align fix if CI #1 surfaces Mockito strict-stub or schema drift. **Anticipate this; do not be surprised.**
4. **`docs(core): ... [skip ci]`** — CI Follow-Up section with the green run id.

CI gate on `gh run view conclusion`, never on `gh run watch` exit code (`feedback_gh_run_watch_unreliable`).

## OOS reaffirmation

This slice does NOT:

- Modify `release_comment` semantics (stays free-text, nullable, optional).
- Backfill any existing released-hold row with a default reason.
- Add bulk-release-across-N-holds.
- Add CSV / file import for node IDs.
- Add a background worker for bulk-apply (synchronous per-row).
- Add email notification on release.
- Touch `Folder` / `Node` / `Trash` / `Version` service code beyond the existing `requireLiveNode(...)` call inside `applyBulkItems`.
- Touch `.env`, `application*.yml`, `docker-compose*`, `logback-spring.xml`.
- Change the existing `HoldStatus` enum or rename any DTO field.
- Add new HTTP routes — both changes are extensions to existing routes' request shapes.

## Decision points

| Decision | Recommendation | Why |
|---|---|---|
| Bulk-apply endpoint shape | **Extend `POST /legal-holds` `CreateLegalHoldRequest` with optional `nodeIds`** | Single route, single test surface, backward compatible. Skip the alternative new `/with-items` route. |
| Release-reason required for new releases | **Yes**; reject null reason with HTTP 400 | Structured audit trail is the slice's compliance value. Allowing null at the API would let the frontend skip the dropdown and erase the value-add. |
| Backfill existing released rows | **No backfill** | "We don't know why this legacy hold was released" is honest. Inventing a default reason creates a misleading audit trail. |
| Per-row transaction for bulk-apply | **Orchestration pattern**: orchestrator method `@Transactional(propagation = NOT_SUPPORTED)`; parent hold saved in one `TransactionTemplate.execute(...)` (REQUIRED); each row added via its own `TransactionTemplate.execute(...)` (REQUIRES_NEW) | Spring proxy does not intercept same-bean self-call; **and** keeping the orchestrator inside an outer transaction would hide the still-uncommitted parent hold from inner REQUIRES_NEW transactions, causing FK violations on `LegalHoldItem.hold_id`. Gate review 2026-05-24 flagged this; the orchestrator runs OUTSIDE any transaction. |
| Bulk-apply response shape | **`BulkApplyResults` wrapper as a single nullable field `bulkApplyResults` on `LegalHoldDto`** (not a bare `List<BulkApplyResult>`) | Avoid splitting the response into two shapes. Wrapper allows future per-batch fields to land without DTO churn. Earlier brief text was inconsistent (said `List<...>` in prose, `BulkApplyResults` wrapper in code); corrected 2026-05-24 to wrapper everywhere. |
| `release_reason` column type | **`VARCHAR(32) NULL`** | Comfortably fits the 4 enum values + future additions. Nullable required for legacy compatibility. |
| Enum location | **`HoldReleaseReason` as a public top-level enum inside `LegalHold.java`** | Mirrors the existing `HoldStatus` sibling; keeps JPA `@Enumerated(EnumType.STRING)` mapping local. |
| Error-category names | **NODE_NOT_FOUND / NODE_NOT_VISIBLE / INTERNAL_ERROR** | Mirrors the bulk-site-invitation categories' shape (3 categories, opaque-string-to-frontend). |

## Worker brief handoff

When gate authorizes implementation:

1. Read all 9 sources in §"Required reading".
2. Implement migration `094-add-legal-hold-release-reason.xml` + include it in the master changelog.
3. Implement entity update + service + controller + DTOs.
4. Implement frontend service additions + dialog updates + page chip rendering.
5. Add tests per §"Tests".
6. Run local verification per §"Verification".
7. Commit + push, gate CI on `gh run view conclusion=success`.
8. Author the verification doc as a sibling of this brief: `docs/LEGAL_HOLD_BULK_APPLY_AND_RELEASE_REASON_DESIGN_VERIFICATION_<actual completion date>.md`.

## What this brief does not commit to

- No code, no test, no migration, no doc beyond this brief itself has been written.
- No commits made.
- The executor still needs explicit "go" from the gate to begin the implementation slice.
