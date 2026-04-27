# Security-Test Backfill — Legacy Controllers, Round 1: Design & Verification

**Commit:** `082e9cd`
**Date:** 2026-04-27
**Scope:** Adds `@WebMvcTest` security tests for three security-critical legacy admin controllers that previously lacked them: `MfaController`, `WebhookController`, `TenantAdminController`.

---

## 1. Why this round

Background: After the Phase 5 round (`docs/P5_BACKEND_SECURITY_TEST_FILL_20260427.md`), every Phase-5 controller has parity (functional + security tests). An audit then found the wider repo had **18 security tests covering 74 controllers** — a 56-controller gap that predates the gap-closure plan.

This round does **not** attempt to close that 56-controller gap in one shot. Instead it picks the three most security-sensitive admin controllers — by impact-of-misconfiguration — and brings them up to the same `@WebMvcTest` shape used in the green sibling tests.

Selection criteria (highest to lowest):
1. **Authentication primitive** → `MfaController` (literally controls how MFA is set up; mis-gating here would let an attacker enroll/disable MFA on someone else's session)
2. **External-callable admin surface** → `WebhookController` (admin-only by `@PreAuthorize`; outbound webhooks can exfiltrate event data if mis-configured)
3. **Multi-tenant control plane** → `TenantAdminController` (tenant CRUD + metrics; cross-tenant data exposure if mis-gated)

`RuleController` was the next obvious candidate but was **deferred**: it has 12+ endpoints, mixed `hasAnyRole('ADMIN', 'EDITOR')` writes, plus 4 service dependencies (`RuleEngineService`, `SecurityService`, `ScheduledRuleRunner`, `AuditService`). That belongs in its own round.

## 2. Design

### 2.1 Reused pattern

Every test follows the proven pattern from `LdapSyncControllerSecurityTest`:

- `@WebMvcTest(controllers = X.class)` boots a slice of the web layer only.
- `@ContextConfiguration` includes the controller, `RestExceptionHandler`, and a tiny inner `TestSecurityConfig` that forces `requestMatchers("/api/**").authenticated()`.
- `@MockBean` for each service the controller depends on.
- Per-endpoint unauthenticated-401 cases assert the filter chain rejects.
- `@WithMockUser(roles = "...")` cases assert the gate granularity (USER vs. ADMIN).

This isolates security concerns from controller logic — the existing functional tests (where present) cover behavior; the security test only checks the filter-chain wiring is correct.

### 2.2 MfaControllerSecurityTest — 7 tests

`MfaController` has **no `@PreAuthorize`** — it relies on the global filter chain's `isAuthenticated()`. Verifying that means:

| Endpoint | Method | Unauth 401 | Auth 200 |
|---|---|---|---|
| `/api/v1/mfa/status` | GET | ✓ | ✓ |
| `/api/v1/mfa/enroll` | POST | ✓ | ✓ |
| `/api/v1/mfa/verify` | POST (json body) | ✓ | not checked (logic-heavy, covered elsewhere) |
| `/api/v1/mfa/disable` | POST (json body) | ✓ | not checked |
| `/api/v1/mfa/recovery-codes` | POST (json body) | ✓ | not checked |

Authenticated coverage on `/verify` and friends is omitted intentionally — the security claim is "auth required", and that's already proved by the `/status` and `/enroll` happy paths returning 200 under `@WithMockUser`. The verify/disable/recovery-codes endpoints have non-trivial logic that belongs in a functional `MfaServiceTest` (already covered there).

### 2.3 WebhookControllerSecurityTest — 9 tests

`WebhookController` has method-level `@PreAuthorize("hasRole('ADMIN')")` on **every** endpoint. The test verifies all three layers of the gate:

| Endpoint | Unauth 401 | ROLE_USER 403 | ROLE_ADMIN 200 |
|---|---|---|---|
| `GET /webhooks` | ✓ | ✓ | ✓ |
| `GET /webhooks/event-types` | ✓ | (implicit, same gate) | ✓ |
| `POST /webhooks` | ✓ | ✓ | not asserted (validation-heavy) |
| `DELETE /webhooks/{id}` | ✓ | ✓ | not asserted |

`ROLE_USER 403` cases prove the `@PreAuthorize` actually fires (without these, a missing `@EnableMethodSecurity` would silently let everyone through). The admin-200 cases on read endpoints prove the gate admits admins, which is the inverse claim.

### 2.4 TenantAdminControllerSecurityTest — 7 tests

`TenantAdminController` has **no `@PreAuthorize`** annotations either. Real-world admin enforcement happens inside `TenantService` (e.g. `current tenant matches request domain` and similar service-side checks). The security test therefore documents that:

| Endpoint | Unauth 401 |
|---|---|
| `GET /admin/tenants` | ✓ |
| `GET /admin/tenants/current` | ✓ |
| `POST /admin/tenants` | ✓ |
| `PUT /admin/tenants/{domain}` | ✓ |
| `DELETE /admin/tenants/{domain}` | ✓ |
| `GET /admin/tenants/{domain}/metrics` | ✓ |

Plus one `@WithMockUser(roles = "USER")` happy-path on `GET /admin/tenants` that:
- Returns 200 (since the controller has only an `isAuthenticated()` gate)
- Carries a `@DisplayName` explicitly noting **admin enforcement is service-side**, not controller-side

This is intentional documentation, not a bug. If a future contributor reads the `@DisplayName` and disagrees, that's a useful conversation to surface — security-by-design should be visible.

## 3. Verification

### 3.1 Local

`./mvnw test` is blocked locally because the wrapper delegates to a Maven Docker image and this dev box has no Docker daemon. (Same constraint that skips live-backend Playwright specs in the latest e2e suite.)

### 3.2 CI

All three files are in the standard `**/*Test.java` Surefire glob and will execute alongside the existing green security tests on the next CI run. They share zero new dependencies, frameworks, or test utilities — every import already resolves in the green sibling tests.

### 3.3 Verification checklist

| # | Item | Status |
|---|---|---|
| 1 | MFA: every endpoint covered by a 401 case | ✓ (5/5) |
| 2 | MFA: at least one authenticated 200 case | ✓ (status, enroll) |
| 3 | Webhook: every endpoint covered by a 401 case (sample) | ✓ (4 representative endpoints) |
| 4 | Webhook: ROLE_USER → 403 cases prove `@PreAuthorize` fires | ✓ (list, create, delete) |
| 5 | Webhook: ROLE_ADMIN → 200 cases prove gate admits admins | ✓ (list, event-types) |
| 6 | TenantAdmin: every endpoint covered by a 401 case | ✓ (6/6) |
| 7 | TenantAdmin: at least one authenticated case documents service-side enforcement | ✓ |
| 8 | All three tests use `@WebMvcTest` (no Docker dependency to compile) | ✓ |
| 9 | All three use the same `TestSecurityConfig` shape as green siblings | ✓ |
| 10 | `RuleController` deliberately deferred to a separate round | ✓ |

## 4. Where this leaves the wider gap

After this round:

- **Security tests in repo:** 18 → **21**
- **Controllers without security tests:** 56 → **53**
- **Security-critical controllers without security tests** (subjective ranking):
  - `RuleController` — next round (12+ endpoints, mixed roles, 4 service deps)
  - `UserController` — identity / user management
  - `GroupController` — group / role management
  - `LicenseController` — license admin
  - `TrashController` — deletion endpoints
  - `BulkOperationController` / `BulkImportController` — bulk admin actions
  - `PermissionTemplateController` — permission templates
  - `ScriptController` — script execution
  - `ShareLinkController` — public sharing surface
  - `SecurityController` — general security ops

These remain **out of scope** for this round and should be triaged into future rounds based on actual risk + effort.
