# Security-Test Backfill — Legacy Controllers, Round 4: Design & Verification

**Commit:** `1d53933`
**Date:** 2026-04-27
**Scope:** Adds `@WebMvcTest` security tests for the three data-mutation amplifier controllers flagged in round 3: `TrashController`, `BulkOperationController`, `BulkImportController`.

---

## 1. Why this round

Round 3's doc flagged this round explicitly:

> "Round 4, if pursued, should look at the data-mutation-amplifier group: TrashController, BulkOperationController, BulkImportController. Mis-gating any of these turns into a single API call that touches many objects, so the blast radius is larger than per-controller intuition suggests."

The amplifier framing is the key. A missing security rule on `RuleController.deleteRule(id)` deletes one rule per call. A missing rule on `BulkOperationController.bulkDelete(payload)` deletes every node listed in the payload — one HTTP request, hundreds of mutations. The same logic applies to `TrashController.emptyTrash()` (wipes every trashed node visible to the caller) and `BulkImportController` (one POST ingests an entire archive).

Per-test-file blast-radius notes are inline in the source so anyone reading the test understands why it exists.

## 2. Design

### 2.1 TrashController — 9 tests

No `@PreAuthorize` annotations — every endpoint is gated only by the global filter chain's `isAuthenticated()`. Real authorization (whose trash you can see, whose you can hard-delete, who can `emptyTrash`) is enforced inside `TrashService`.

| Endpoint | Method | Test |
|---|---|---|
| `/trash/nodes/{nodeId}` (soft-delete) | POST | unauth 401 |
| `/trash/{nodeId}/restore` | POST | unauth 401 |
| `/trash/{nodeId}` (hard-delete) | DELETE | unauth 401 |
| `/trash` | GET | unauth 401 + ROLE_USER 200 |
| `/trash/user/{username}` | GET | unauth 401 |
| `/trash/empty` | DELETE | **unauth 401 (load-bearing)** |
| `/trash/stats` | GET | unauth 401 |
| `/trash/nearing-purge` | GET | unauth 401 |

The `DELETE /trash/empty` 401 case is the load-bearing one: `emptyTrash()` hard-deletes every trashed node visible to the caller in a single transaction. A missing filter rule would let an unauthenticated request wipe trash for the entire repository. The test makes that risk explicit in a `@DisplayName` so a reviewer who weakens the rule knows what they're trading.

### 2.2 BulkOperationController — 10 tests

Every endpoint carries `@PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")`. Test asserts:

- 5 unauth-401 cases covering the four mutating writes (`move`, `delete`, `restore`, `metadata`) plus one read (`history`)
- 4 ROLE_USER 403 cases (proves `@PreAuthorize` actually fires; one per gate-tier sample)
- 1 ROLE_EDITOR 200 case on `GET /history` (proves the gate is `hasAnyRole(ADMIN, EDITOR)`, not `hasRole(ADMIN)` alone)

The ROLE_EDITOR 200 case is the load-bearing assertion: it catches a future refactor that accidentally narrows the gate from admin-or-editor to admin-only.

The `POST /bulk/delete` unauth-401 is also explicitly flagged in the test's `@DisplayName` as the high-blast-radius operation.

### 2.3 BulkImportController — 5 tests

No `@PreAuthorize` annotations — every endpoint is gated only by `isAuthenticated()`. Quota and tenant-scope enforcement is service-side via `TenantQuotaService`.

| Endpoint | Method | Test |
|---|---|---|
| `/bulk-import` (multipart) | POST | unauth 401 |
| `/bulk-import/{jobId}` | GET | unauth 401 |
| `/bulk-import` | GET | unauth 401 + ROLE_USER 200 |
| `/bulk-import/{jobId}` | DELETE | unauth 401 |

Smaller test count because the surface is smaller and the gate is uniform. The `POST /bulk-import` upload is flagged in its `@DisplayName` as a privilege amplifier.

### 2.4 Pattern reuse

All three tests use the now-standard shape:
- `@WebMvcTest(controllers = X.class)` slice loading
- Inner `TestSecurityConfig` requiring `authenticated()` for `/api/**`
- One `@MockBean` per service the controller depends on (5 for BulkOperationController, 1-2 for the others)

No new test utilities, no new dependencies.

## 3. Verification

### 3.1 Local

`./mvnw test` is blocked locally because `ecm-core/mvnw` delegates to a Maven Docker image and this dev box has no Docker daemon. Same constraint as rounds 1–3 of this backfill.

### 3.2 CI

All three files ship through the standard Surefire `**/*Test.java` glob and will execute alongside the green sibling security tests on the next CI run.

### 3.3 Verification checklist

| # | Item | Status |
|---|---|---|
| 1 | TrashController: every endpoint covered by an unauth-401 case | ✓ (8/8) |
| 2 | TrashController: `emptyTrash` 401 case carries blast-radius note in @DisplayName | ✓ |
| 3 | TrashController: ROLE_USER → 200 on read confirms isAuthenticated()-only gate | ✓ |
| 4 | BulkOperationController: 5 unauth-401 cases across writes and reads | ✓ |
| 5 | BulkOperationController: ROLE_USER → 403 cases prove @PreAuthorize fires | ✓ (4/4) |
| 6 | BulkOperationController: ROLE_EDITOR → 200 case prove gate is admin-or-editor, not admin-only | ✓ |
| 7 | BulkImportController: every endpoint covered by an unauth-401 case | ✓ (4/4) |
| 8 | BulkImportController: ROLE_USER → 200 confirms isAuthenticated()-only gate | ✓ |
| 9 | All three tests use @WebMvcTest, no Docker dependency to compile | ✓ |
| 10 | Each test file documents its blast-radius reasoning in source comments | ✓ |

## 4. After this commit

| | Round 1 (082e9cd) | Round 2 (3283ec5) | Round 3 (de72cfa) | Round 4 (1d53933) |
|---|---|---|---|---|
| Security tests in repo | 21 | 22 | 25 | **28** |
| Controllers without security tests | 53 | 52 | 49 | **46** |

Cumulative across all four rounds: **+10 security tests, –10 untested controllers** (plus the 2 from the original Phase 5 fill, so 12 if counting from the start of this backfill thread).

Remaining security-critical controllers (subjective ranking after round 4):

- `LicenseController` — license admin (low frequency, high stakes if bypass)
- `PermissionTemplateController` — permission templates (defines other gates)
- `ScriptController` — script execution surface (sandbox-escape risk)
- `SecurityController` — general security ops
- `WopiHostController` / `WopiIntegrationController` — Office integration auth ticket flow
- `TransferReceiverController` / `TransferReplicationController` — replication endpoints

Round 5, if pursued, should look at `ScriptController` and `PermissionTemplateController` together — both are "control planes" that govern other security decisions, and `ScriptController` has the highest single-controller blast radius left.
