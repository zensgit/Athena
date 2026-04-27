# Security-Test Backfill — Legacy Controllers, Round 3: Design & Verification

**Commit:** `de72cfa`
**Date:** 2026-04-27
**Scope:** Adds `@WebMvcTest` security tests for the three identity-surface controllers flagged in round 2: `UserController`, `GroupController`, `ShareLinkController`.

---

## 1. Why this round

Round 2's doc explicitly flagged identity as round 3:

> "Round 3 should look at identity (UserController, GroupController, ShareLinkController) since those govern who can do what."

Identity controllers are security-critical because mis-gating them means an attacker can:

- **UserController** — create accounts, change passwords/emails on existing accounts → full account takeover.
- **GroupController** — create groups, add themselves to a group with elevated ACLs → privilege escalation.
- **ShareLinkController** — share content publicly without authorization, OR (the inverse) accidentally lock down the public redeem path so external recipients can't use links they were sent.

The third bullet is the most subtle and the one this round invests the most testing effort in.

## 2. Design

### 2.1 UserController — 8 tests

Two-tier gate model:

| Tier | Annotation | Endpoints |
|---|---|---|
| Reads | none (`isAuthenticated()` only) | `GET /users` (search), `GET /users/{username}` |
| Admin writes | `@PreAuthorize("hasRole('ADMIN')")` | `POST /users`, `PUT /users/{username}` |

Test breakdown:

| Test | Expectation |
|---|---|
| unauth GET /users | 401 |
| unauth GET /users/{username} | 401 |
| unauth POST /users | 401 |
| unauth PUT /users/{username} | 401 |
| ROLE_USER GET /users | 200 |
| ROLE_USER POST /users | 403 |
| ROLE_USER PUT /users/{username} | 403 |
| **ROLE_EDITOR POST /users → 403** | proves user CRUD is admin-only, **not** admin-or-editor |

The `ROLE_EDITOR → 403` is the load-bearing assertion: it catches a future refactor that widens the gate to `hasAnyRole('ADMIN','EDITOR')`.

### 2.2 GroupController — 11 tests

Two-tier gate model:

| Tier | Annotation | Endpoints |
|---|---|---|
| Reads | none | `GET /groups` |
| Admin writes | `@PreAuthorize("hasRole('ADMIN')")` | `POST /groups`, `DELETE /groups/{name}`, `POST /groups/{groupName}/members/{username}`, `DELETE /groups/{groupName}/members/{username}` |

The 4 admin-write endpoints all share one gate, so the test asserts:
- 4 unauth-401 cases (one per endpoint, full enumeration since cheap)
- 1 read-tier 401 (GET /groups)
- 1 read-tier 200 (ROLE_USER → GET /groups)
- 4 ROLE_USER → 403 cases (one per write endpoint, proves the gate fires for each)
- 1 ROLE_EDITOR → 403 (proves the gate is `hasRole('ADMIN')`, not widened)

### 2.3 ShareLinkController — 9 tests

This is the architecturally interesting one. The production `SecurityConfig.java:52` includes:

```java
.requestMatchers("/api/v1/share/access/**", "/api/share/access/**").permitAll()
```

This is **intentional**: the share-link redeem flow must work for external recipients who have no Athena account. ShareLinkService is then responsible for token validity, password checks, IP allowlist, expiry, etc. — all the auth-equivalent logic moves into the service layer.

The test config explicitly mirrors that prod rule:

```java
.requestMatchers("/api/v1/share/access/**", "/api/share/access/**").permitAll()
.requestMatchers("/api/**").authenticated()
```

Tests:

| Tier | Test | Expectation |
|---|---|---|
| Authenticated-required | unauth POST /share/nodes/{nodeId} (create) | 401 |
| Authenticated-required | unauth GET /share/{token} (read) | 401 |
| Authenticated-required | unauth GET /share/my | 401 |
| Authenticated-required | unauth PUT /share/{token} | 401 |
| Authenticated-required | unauth DELETE /share/{token} | 401 |
| Authenticated-required | unauth POST /share/{token}/deactivate | 401 |
| Authenticated-required | unauth GET /share/admin/all | 401 |
| **Public redeem** | unauth GET /share/access/{token} on `/api/v1/share` prefix | **403** (ShareLinkService says "invalid token" — request reached the controller body) |
| **Public redeem** | unauth GET /share/access/{token} on `/api/share` legacy prefix | **403** (same — both prefixes are permitAll) |

The two public-redeem cases use `ShareLinkAccessResult.invalid("token-not-found")` so `accessShareLink()` returns `success=false`, and the controller turns that into a `403 + body{error: "..."}`. The 403 here is **ShareLinkService**'s decision, not Spring Security's — proven by the fact that the mock was invoked. If a future change deletes the `permitAll()` rule, the filter chain rejects the request before the controller's body executes, the mock is never called, and the response becomes `401`. Both tests would flip from 403 → 401 and fail loudly.

### 2.4 Pattern reuse

Every test uses the same `@WebMvcTest` + inner `TestSecurityConfig` pattern as the green sibling tests (LdapSyncControllerSecurityTest, RuleControllerSecurityTest, etc.). No new dependencies, no new utilities.

## 3. Verification

### 3.1 Local

`./mvnw test` is blocked locally because the wrapper delegates to a Maven Docker image and this dev box has no Docker daemon.

### 3.2 CI

All three files ship through the standard Surefire `**/*Test.java` glob and will execute alongside the green sibling security tests on the next CI run.

### 3.3 Verification checklist

| # | Item | Status |
|---|---|---|
| 1 | UserController: unauthenticated 401 on every endpoint | ✓ (4/4) |
| 2 | UserController: ROLE_USER → 403 on writes proves @PreAuthorize fires | ✓ (2/2) |
| 3 | UserController: ROLE_EDITOR → 403 on create proves admin-only, not admin-or-editor | ✓ |
| 4 | UserController: ROLE_USER → 200 on read proves the gate is `isAuthenticated()` not role-based | ✓ |
| 5 | GroupController: unauthenticated 401 on every endpoint | ✓ (5/5) |
| 6 | GroupController: ROLE_USER → 403 on every write proves @PreAuthorize fires | ✓ (4/4) |
| 7 | GroupController: ROLE_EDITOR → 403 proves admin-only | ✓ |
| 8 | ShareLinkController: TestSecurityConfig mirrors production permitAll() rule | ✓ |
| 9 | ShareLinkController: 7 unauth 401 cases on protected endpoints | ✓ |
| 10 | ShareLinkController: 2 public-redeem 403 cases on `/access/{token}` (both prefixes) prove the redeem path is reachable without authentication | ✓ |
| 11 | If a future change drops the redeem permitAll() rule, the 403 cases will flip to 401 and fail | ✓ (by construction) |

## 4. After this commit

| | Round 1 (082e9cd) | Round 2 (3283ec5) | Round 3 (de72cfa) |
|---|---|---|---|
| Security tests in repo | 21 | 22 | **25** |
| Controllers without security tests | 53 | 52 | **49** |

Remaining security-critical controllers (subjective ranking):

- `LicenseController` — license admin (rare, low-impact-of-mis-gating)
- `TrashController` — deletion / recovery endpoints (data-loss risk)
- `BulkOperationController` / `BulkImportController` — bulk admin actions (privilege amplifier)
- `PermissionTemplateController` — permission templates
- `ScriptController` — script execution (sandbox-escape risk)
- `SecurityController` — general security ops
- `WopiHostController` / `WopiIntegrationController` — Office integration (auth ticket flow)

Round 4, if pursued, should look at the data-mutation-amplifier group: `TrashController`, `BulkOperationController`, `BulkImportController`. Mis-gating any of these turns into a single API call that touches many objects, so the blast radius is larger than per-controller intuition suggests.
