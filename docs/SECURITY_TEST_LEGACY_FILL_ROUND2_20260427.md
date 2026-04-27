# Security-Test Backfill — Legacy Controllers, Round 2: Design & Verification

**Commit:** `3283ec5`
**Date:** 2026-04-27
**Scope:** Adds the `@WebMvcTest` security test for `RuleController`, deferred from round 1 because of its size and complexity.

---

## 1. Why this round

Round 1 (`docs/SECURITY_TEST_LEGACY_FILL_ROUND1_20260427.md`, commit `082e9cd`) covered MFA, Webhook, and TenantAdmin and explicitly deferred `RuleController` for a separate effort:

> "RuleController was the next obvious candidate but **deliberately deferred**: it has 12+ endpoints, mixed `hasAnyRole('ADMIN', 'EDITOR')` writes, plus 4 service dependencies… that belongs in its own round."

This round is that round.

`RuleController` is security-critical because:
- The `@PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")` writes can change rule logic that runs against every uploaded document — a mis-gating means any USER could install a rule that exfiltrates content via the `WEBHOOK` action.
- The `@PreAuthorize("hasRole('ADMIN')")` `triggerScheduledRule` endpoint can fire scheduled jobs out-of-band — a mis-gating could let an EDITOR force-run admin-controlled jobs.
- The 25+ endpoints across the controller previously had **zero** Spring-Security-aware tests. Functional coverage in `RuleControllerTest` exists but doesn't load the security filter chain.

## 2. Design

### 2.1 Three-tier gate model

| Tier | Annotation | Sample endpoints |
|---|---|---|
| **Open reads** | none (only filter-chain `isAuthenticated()`) | `GET /rules`, `GET /rules/{id}`, `GET /rules/templates`, `GET /rules/actions/definitions`, `GET /rules/stats`, `POST /rules/validate`, `POST /rules/validate-cron` |
| **ADMIN-or-EDITOR writes** | `@PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")` | `POST /rules`, `PUT /rules/{id}`, `DELETE /rules/{id}`, `PATCH /rules/{id}/enable`, `PATCH /rules/{id}/disable`, `POST /rules/folders/{id}/reorder`, `POST /rules/folders/{id}/dry-run`, `POST /rules/{id}/test`, `POST /rules/{id}/execute`, `GET /rules/executions*`, `GET /rules/executions/audit*` |
| **ADMIN-only** | `@PreAuthorize("hasRole('ADMIN')")` | `POST /rules/{id}/trigger` |

The test verifies each tier with **one or two representative endpoints** instead of enumerating all 25+. Three cases per tier prove the gate fires:

- Tier 1: unauth → 401, ROLE_USER → 200
- Tier 2: unauth → 401, ROLE_USER → 403, ROLE_EDITOR → 200 *(critical — proves the gate isn't `hasRole('ADMIN')` alone)*
- Tier 3: unauth → 401, ROLE_USER → 403, **ROLE_EDITOR → 403** *(critical — proves the gate isn't `hasAnyRole('ADMIN','EDITOR')`)*

### 2.2 Test class shape

Identical to the green sibling tests:

- `@WebMvcTest(controllers = RuleController.class)` for slice-only loading.
- `@ContextConfiguration` lists `RuleController.class`, `RestExceptionHandler.class`, and the inner `TestSecurityConfig`.
- Inner `TestSecurityConfig` enables `@EnableMethodSecurity(prePostEnabled = true)` so `@PreAuthorize` annotations are actually evaluated.
- Five `@MockBean`s for the controller's dependencies: `RuleEngineService`, `SecurityService`, `ScheduledRuleRunner`, `AuditService`, `AuditLogRepository`.

### 2.3 Test breakdown — 17 tests

| Tier | Test | Expectation |
|---|---|---|
| 1 | unauth GET /rules | 401 |
| 1 | unauth GET /rules/templates | 401 |
| 1 | ROLE_USER GET /rules | 200 |
| 1 | ROLE_USER GET /rules/templates | 200 |
| 2 | unauth POST /rules | 401 |
| 2 | unauth DELETE /rules/{id} | 401 |
| 2 | unauth PATCH /rules/{id}/enable | 401 |
| 2 | ROLE_USER POST /rules | 403 |
| 2 | ROLE_USER PUT /rules/{id} | 403 |
| 2 | ROLE_USER DELETE /rules/{id} | 403 |
| 2 | ROLE_USER PATCH /rules/{id}/enable | 403 |
| 2 | ROLE_USER GET /rules/executions | 403 |
| 2 | ROLE_EDITOR GET /rules/executions | 200 |
| 3 | unauth POST /rules/{id}/trigger | 401 |
| 3 | ROLE_USER POST /rules/{id}/trigger | 403 |
| 3 | ROLE_EDITOR POST /rules/{id}/trigger | 403 |

The two most security-relevant assertions are **ROLE_EDITOR → 200 on tier-2** and **ROLE_EDITOR → 403 on tier-3**. Together they prove the controller distinguishes the two roles correctly. If someone accidentally rewrites `triggerScheduledRule` to use `hasAnyRole('ADMIN', 'EDITOR')`, the tier-3 editor case fails immediately.

## 3. Verification

### 3.1 Local

`./mvnw test` is blocked locally because `ecm-core/mvnw` delegates to a Maven Docker image and this dev box has no Docker daemon. Same constraint as rounds 1/2 of this backfill and the live-backend Playwright specs.

### 3.2 CI

The new file ships through the standard Surefire `**/*Test.java` glob with no new test dependencies. Imports, annotations, and `TestSecurityConfig` are byte-identical in shape to the green security tests already in CI.

### 3.3 Verification checklist

| # | Item | Status |
|---|---|---|
| 1 | All three authorization tiers covered | ✓ |
| 2 | At least one unauth-401 case per tier | ✓ |
| 3 | ROLE_USER → 403 cases prove `@PreAuthorize` actually fires for tier-2 and tier-3 | ✓ |
| 4 | ROLE_EDITOR → 200 case proves tier-2 admits editors | ✓ |
| 5 | ROLE_EDITOR → 403 case proves tier-3 is admin-only and **not** admin-or-editor | ✓ |
| 6 | Test only asserts security claims; behavioral coverage left to RuleEngineServiceTest | ✓ |
| 7 | Five service mocks declared, matching the controller's constructor dependencies | ✓ |
| 8 | Same `@WebMvcTest` + `TestSecurityConfig` pattern as green siblings | ✓ |

## 4. After this commit

| | Round 0 (Phase 5 close) | Round 1 (082e9cd) | Round 2 (3283ec5) |
|---|---|---|---|
| Security tests in repo | 18 | 21 | **22** |
| Controllers without security tests | 56 | 53 | **52** |

Remaining security-critical controllers without tests (subjective ranking):
- `UserController` — identity / user management
- `GroupController` — group / role management
- `LicenseController` — license admin
- `TrashController` — deletion endpoints
- `BulkOperationController` / `BulkImportController`
- `PermissionTemplateController`
- `ScriptController`
- `ShareLinkController` — public sharing surface
- `SecurityController`

Each future round should pick 3–4 controllers, sample endpoints for each tier, and ship the same `@WebMvcTest` shape. Round 3 should look at identity (UserController, GroupController, ShareLinkController) since those govern who can do what.
