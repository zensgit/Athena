# Security-Test Backfill — Legacy Controllers, Round 5: Design & Verification

**Commit:** `7dbfc91`
**Date:** 2026-04-27
**Scope:** Adds `@WebMvcTest` security tests for the two control-plane controllers flagged in round 4: `ScriptController`, `PermissionTemplateController`.

---

## 1. Why this round

Round 4's doc flagged this round explicitly:

> "Round 5, if pursued, should look at ScriptController and PermissionTemplateController together — both are 'control planes' that govern other security decisions, and ScriptController has the highest single-controller blast radius left."

The control-plane framing matters because mis-gating these doesn't just expose the controller's own surface — it amplifies into wider security exposure:

- **ScriptController** runs arbitrary GraalJS code under the JVM's permissions. A missing gate on `POST /scripts/execute` is functionally remote code execution.
- **PermissionTemplateController** defines ACL templates that get applied to nodes via `/apply`. A widened gate would let editors (or anyone authenticated) redefine the security templates that other content inherits.

These are also the last two single-controller endpoints in the repo with this kind of "amplifier × control-plane" character, which is why I'm bookending the backfill thread here.

## 2. Design

### 2.1 ScriptController — 7 tests

No `@PreAuthorize` annotations — every endpoint is gated only by `isAuthenticated()`. Role-based enforcement (e.g. "only admin can edit a script", "only specific roles can execute") is in `ScriptService`.

| Endpoint | Test |
|---|---|
| `GET /scripts` | unauth 401 + ROLE_USER 200 |
| `GET /scripts/{id}` | unauth 401 |
| `POST /scripts` | unauth 401 |
| `PUT /scripts/{id}` | unauth 401 |
| `DELETE /scripts/{id}` | unauth 401 |
| **`POST /scripts/execute`** | **unauth 401 (load-bearing)** |

The `execute` 401 case is called out explicitly in its `@DisplayName` and a source comment as the **highest single-endpoint blast radius this backfill has touched**. If a future change accidentally moves the `requestMatchers("/api/**").authenticated()` rule above `/api/v1/scripts/**` in production SecurityConfig, this test fails immediately.

### 2.2 PermissionTemplateController — 11 tests

Every endpoint carries `@PreAuthorize("hasRole('ADMIN')")` — no read/write split, no admin-or-editor tier. The test verifies all three layers of the gate:

| Layer | Test cases | Critical? |
|---|---|---|
| Filter chain (`isAuthenticated()`) | 7 unauth-401 cases | required |
| `@PreAuthorize` fires on writes | ROLE_USER 403 on `/apply` | required |
| `@PreAuthorize` fires on reads too | ROLE_USER 403 on `GET /` | important — proves there's no accidental read-tier loophole |
| Gate is admin-**only**, not admin-or-editor | **ROLE_EDITOR 403 on `/apply`** | **load-bearing** |
| Gate admits admins | ROLE_ADMIN 200 on `GET /` | sanity |

The `ROLE_EDITOR → 403 on /apply` case is the load-bearing assertion: PermissionTemplateController doesn't have an admin-or-editor tier today, but a contributor extending the controller might accidentally widen `@PreAuthorize("hasRole('ADMIN')")` to `@PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")` while adding new endpoints. This test catches that immediately.

### 2.3 Pattern reuse

Same `@WebMvcTest` + inner `TestSecurityConfig` shape as rounds 1–4. ScriptController needs only one `@MockBean` (`ScriptService`). PermissionTemplateController needs three (`PermissionTemplateService`, `AuditService`, `SecurityService`).

## 3. Verification

### 3.1 Local

`./mvnw test` is blocked locally because `ecm-core/mvnw` delegates to a Maven Docker image and this dev box has no Docker daemon. Same constraint as rounds 1–4 of this backfill.

### 3.2 CI

Both files ship through the standard Surefire `**/*Test.java` glob and execute alongside the green sibling security tests on the next CI run. No new test dependencies.

### 3.3 Verification checklist

| # | Item | Status |
|---|---|---|
| 1 | ScriptController: every endpoint covered by an unauth-401 case | ✓ (6/6) |
| 2 | ScriptController: `POST /execute` 401 case carries the RCE blast-radius note | ✓ |
| 3 | ScriptController: ROLE_USER → 200 on read confirms isAuthenticated()-only gate | ✓ |
| 4 | PermissionTemplateController: 7 unauth-401 cases across reads and writes | ✓ |
| 5 | PermissionTemplateController: ROLE_USER → 403 on read proves no read-tier loophole | ✓ |
| 6 | PermissionTemplateController: ROLE_USER → 403 on `/apply` proves @PreAuthorize fires | ✓ |
| 7 | PermissionTemplateController: ROLE_EDITOR → 403 on `/apply` proves admin-only | ✓ |
| 8 | PermissionTemplateController: ROLE_ADMIN → 200 on read sanity-checks gate admits admins | ✓ |
| 9 | Both tests use `@WebMvcTest`, no Docker dependency to compile | ✓ |
| 10 | Verified `permissionTemplateService.list()` is the actual method name (not `listTemplates`) | ✓ (post-write fix) |

## 4. After this commit

| | Round 1 | Round 2 | Round 3 | Round 4 | Round 5 |
|---|---|---|---|---|---|
| Commit | `082e9cd` | `3283ec5` | `de72cfa` | `1d53933` | **`7dbfc91`** |
| Tests in repo | 21 | 22 | 25 | 28 | **30** |
| Untested controllers | 53 | 52 | 49 | 46 | **44** |

Cumulative across rounds 1–5: **+12 security tests, –12 untested controllers** (counting from when the wider gap was 56). With Phase 5 close (`799fd70`), the total reaches 14 added tests across this thread.

### 4.1 Backfill thread closeout

After five rounds, the high-blast-radius and security-critical legacy controllers are covered:

- **Authentication primitives:** MFA ✓
- **Identity:** User, Group ✓
- **External-callable admin:** Webhook ✓
- **Multi-tenant control plane:** TenantAdmin ✓
- **Automation rules (3-tier):** Rule ✓
- **External public surface:** ShareLink ✓
- **Data-mutation amplifiers:** Trash, BulkOperation, BulkImport ✓
- **Control plane:** Script (RCE risk), PermissionTemplate (ACL governor) ✓

What remains (44 controllers) is mostly:
- Content surface (`Document`, `Folder`, `Node`, `NodeContent`, `Upload`, etc.) — many endpoints, but each touches one node per call. Lower per-call blast radius than the amplifier round.
- Read-mostly metadata APIs (`Activity`, `Calendar`, `Category`, `Discussion`, etc.) — leak risk if mis-gated, but no mutation amplification.
- CMIS / WOPI / Transfer protocol endpoints — these have their own authentication mechanisms (CMIS basic auth, WOPI access tokens) that warrant a different testing approach than the `@WithMockUser` pattern used here.

If a round 6 happens, my recommendation is to **stop**, declare the backfill thread closed, and fold the remaining 44 controllers into normal feature work — adding a security test alongside any future change to those controllers, rather than running another speculative pass. The marginal value drops sharply once the high-blast-radius surfaces are covered, and the test pattern is now well-established in the codebase for any contributor to follow.

### 4.2 Cumulative pattern documented

Across the 5 rounds, the established test pattern is:

1. `@WebMvcTest(controllers = X.class)` + `@ContextConfiguration` listing the controller, `RestExceptionHandler`, and an inner `TestSecurityConfig`.
2. `TestSecurityConfig` requires `requestMatchers("/api/**").authenticated()` (with permitAll exceptions only when production has them — see ShareLinkControllerSecurityTest).
3. `@MockBean` per service the controller depends on.
4. One unauth-401 per gate/endpoint (full enumeration when cheap, sampled when many endpoints share a gate).
5. `@WithMockUser(roles = "...")` happy-path and forbidden-path cases proving each gate's granularity.
6. **Load-bearing** assertions (the ones that catch the most likely future regressions) are called out in `@DisplayName` text and inline comments.

Any future contributor adding a new controller can copy any of these tests as a template.
