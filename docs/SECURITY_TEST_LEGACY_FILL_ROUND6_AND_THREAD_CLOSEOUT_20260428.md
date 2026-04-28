# Security-Test Backfill — Legacy Controllers, Round 6 + Thread Closeout

**Commit:** `4c66a7e`
**Date:** 2026-04-28
**Scope:** Adds `@WebMvcTest` security tests for `LicenseController` and `SecurityController`. **Closes the backfill thread.**

---

## 1. Why one more round, then closeout

Round 5's doc (`SECURITY_TEST_LEGACY_FILL_ROUND5_20260427.md`) recommended stopping after round 5 — the high-blast-radius surfaces were covered and marginal value was dropping.

Two pieces of new information argued for one more bounded round:

1. **CI fixes came in.** Between rounds 5 and 6, the linter / CI run polished the test files I'd written across rounds 2–5 — adding concrete `Page<T>` generics, `PageRequest.of(0, 20)` in `PageImpl` constructors, mocking the actual repository methods the controller calls (e.g. `auditLogRepository.findBulkOperationTimelineNoNodeId(...)` for the editor 200 happy-path in `BulkOperationControllerSecurityTest`), and adding the `nodeId` query parameter to `PermissionTemplateController.apply` calls. That's significant engagement, not abandonment.
2. **`SecurityController` was a notable omission from round 1.** I'd skipped it in the original "auth/security primitives" round in favor of MFA/Webhook/TenantAdmin. Coming back to it now matches the spirit of that round.

This round 6 picks the two cleanest remaining controllers (one tiny, one mid-sized) and then **actually closes the thread** in this same doc, rather than promising "round 7 if pursued" again.

## 2. Design

### 2.1 LicenseController — 4 tests

Single endpoint, single gate. `GET /api/v1/system/license` carries `@PreAuthorize("hasRole('ADMIN')")`.

| Test | Expectation | Why |
|---|---|---|
| unauth → 401 | filter chain rejects | baseline |
| ROLE_USER → 403 | `@PreAuthorize` fires | gate works for non-admins |
| **ROLE_EDITOR → 403** | admin-only, **not** admin-or-editor | load-bearing |
| ROLE_ADMIN → 200 | gate admits admin | sanity |

The smallest, cleanest test in the entire backfill thread.

### 2.2 SecurityController — 9 tests

14 endpoints covering per-node ACL reads/writes, take-ownership, inherit-permissions, current-user info, and expired-permission cleanup. **Zero `@PreAuthorize` annotations** — every endpoint is gated only by `isAuthenticated()`. Per-node permission checks ("does this user have CHANGE_PERMISSIONS on this node?") are enforced inside `SecurityService` / `NodeService`.

| Tier | Tests |
|---|---|
| unauth-401 sample | 8 cases across permission reads (`GET /nodes/{id}/permissions`, `GET .../effective-permissions`), ACL writes (`POST .../permissions`, `DELETE .../permissions`), take-ownership, inherit-permissions, current-user, expired cleanup |
| ROLE_USER → 200 | 1 happy-path (`/users/current/authorities`) confirming the gate is isAuthenticated()-only |

**Load-bearing assertion**: `POST /security/nodes/{id}/permissions` unauth-401. ACL mutation in one call. A missing filter rule means anonymous callers can rewrite permissions across the entire repository.

The reason no `ROLE_USER → 403` case appears here: the controller doesn't have `@PreAuthorize`, so there's nothing role-specific to test at the controller layer. Authorization on a per-node basis lives in `SecurityService`, which is functional-test territory.

### 2.3 Pattern reused, lessons applied

I picked up the conventions from the rounds 2–5 CI fixes:

- Concrete `Page<T>` generic in `PageImpl<T>(List.of(), PageRequest.of(0, 20), 0)` calls.
- Mocking the actual repository method the controller invokes (verified via `grep "<service>\." <Controller>.java`), not the obvious-from-the-DTO-name guess.
- For controllers that depend on `securityService.getCurrentUser()`, mocking it explicitly so happy-path tests don't NPE.

I caught one issue mid-write: `SecurityController.getCurrentUser()` calls `securityService.getCurrentUserEntity()` returning a `User` entity, then `toDto(user)`. With a default-null mock, that NPEs. Switched the happy-path to `/users/current/authorities` which has cleaner mocking via `Set<String>`. Inline doc note in the test.

## 3. Verification

### 3.1 Local

`./mvnw test` is blocked locally because `ecm-core/mvnw` delegates to a Maven Docker image and this dev box has no Docker daemon. Same constraint as rounds 1–5.

### 3.2 CI

Both files ship through the standard Surefire `**/*Test.java` glob and execute alongside the green sibling security tests on the next CI run.

### 3.3 Verification checklist

| # | Item | Status |
|---|---|---|
| 1 | LicenseController: 4-cell matrix (unauth/USER/EDITOR/ADMIN) covered | ✓ |
| 2 | LicenseController: ROLE_EDITOR → 403 catches future widening | ✓ |
| 3 | SecurityController: 8 unauth-401 cases sampled across the 14 endpoints | ✓ |
| 4 | SecurityController: `POST /nodes/{id}/permissions` 401 marked load-bearing | ✓ |
| 5 | SecurityController: ROLE_USER 200 on read confirms isAuthenticated-only gate | ✓ |
| 6 | Both tests use `@WebMvcTest`, no Docker dependency to compile | ✓ |
| 7 | Lessons from rounds 2–5 CI fixes applied (concrete Page generics, real method mocks) | ✓ |

## 4. Backfill thread closeout

| Round | Commit | Tests added | Repo total |
|---|---|---|---|
| Phase 5 close | `799fd70` | Notification, Email | 18 → 20 |
| 1 | `082e9cd` | MFA, Webhook, TenantAdmin | 20 → 23 |
| 2 | `3283ec5` | Rule (3-tier) | 23 → 24 |
| 3 | `de72cfa` | User, Group, ShareLink | 24 → 25 |
| 4 | `1d53933` | Trash, BulkOperation, BulkImport | 25 → 28 |
| 5 | `7dbfc91` | Script, PermissionTemplate | 28 → 30 |
| **6** | **`4c66a7e`** | **License, SecurityController** | **30 → 32** |

Cumulative across the seven commits in this thread: **+14 security tests, –14 untested controllers**. Untested legacy controllers: 56 → 42.

### 4.1 What's covered now

- **Auth primitives:** MFA ✓
- **Identity:** User, Group ✓
- **Public surfaces:** ShareLink (permitAll redeem path) ✓
- **External admin:** Webhook ✓
- **Multi-tenant control plane:** TenantAdmin ✓
- **Automation rules (3-tier):** Rule ✓
- **Data-mutation amplifiers:** Trash, BulkOperation, BulkImport ✓
- **Control plane (RCE / ACL governance):** Script, PermissionTemplate ✓
- **Per-node ACL surface:** SecurityController ✓
- **License admin:** LicenseController ✓
- **Phase-5 controllers (full parity):** Notification, Email, LegalHold, LdapSync, DispositionSchedule, LocalizedContent, SiteInvitation ✓

### 4.2 What's deliberately not covered

The remaining ~42 untested controllers fall into three buckets:

1. **Per-node content surface** (`Document`, `Folder`, `Node`, `NodeContent`, `Upload`, `BatchDownload`, `Trash`-adjacent, `Favorite`, `Following`, etc.). High volume of endpoints, but each touches one node per call. Lower per-call blast radius than the amplifier round. Per-node authorization is enforced inside `SecurityService` and is functional-test territory. These should get security tests **opportunistically** — when a future PR touches one of them, add the test alongside the change.

2. **Read-mostly metadata APIs** (`Activity`, `Calendar`, `Category`, `Discussion`, `Tag`, `Rating`, `Comment`, `Discussion`, etc.). Low mutation impact. Information-leak risk if mis-gated, but the surface is small.

3. **Protocol endpoints** (`CmisAtomPubController`, `CmisBrowserController`, `WopiHostController`, `WopiIntegrationController`, `TransferReceiverController`, `TransferReplicationController`). These have non-standard authentication mechanisms — CMIS basic auth, WOPI access tokens, transfer per-job tokens. The `@WithMockUser` pattern doesn't apply cleanly. These need a different testing approach (mock the auth token service, simulate the protocol-specific header) and warrant their own design effort.

### 4.3 Established pattern for future contributors

Any contributor adding a new controller can copy any of the rounds 1–6 test files as a template. The shape is:

```java
@WebMvcTest(controllers = X.class)
@ContextConfiguration(classes = {
    X.class,
    RestExceptionHandler.class,
    XSecurityTest.TestSecurityConfig.class
})
class XSecurityTest {
    @Autowired private MockMvc mockMvc;
    @MockBean private <each service> ...;

    @Configuration @EnableWebSecurity @EnableMethodSecurity(prePostEnabled = true)
    static class TestSecurityConfig {
        @Bean SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/api/**").authenticated()
                    .anyRequest().permitAll())
                .httpBasic(basic -> {})
                .build();
        }
    }

    // 1+ unauth-401 case per gate
    // ROLE_USER → 200/403 happy/forbidden cases per gate
    // Role-tier proofs (e.g. ROLE_EDITOR → 403 if admin-only)
    // @DisplayName carries the load-bearing reasoning
}
```

Required calibration per controller:
- `@MockBean` per service the controller depends on (find with `grep "private final.*Service" <Controller>.java`).
- `TestSecurityConfig` should mirror the production rule (e.g. `permitAll()` for share-link redeem path — see `ShareLinkControllerSecurityTest`).
- For role-gated endpoints, include both a denied-role case (proves `@PreAuthorize` fires) and an admitted-role case (proves the gate admits the right role).
- For load-bearing assertions, surface the reasoning in the `@DisplayName` text or an inline comment so a future reader understands why the test exists before deciding whether to weaken it.

### 4.4 Recommendation: do NOT run another speculative round

Three rounds ago I documented: "the marginal value drops sharply once the high-blast-radius surfaces are covered". Three rounds later, that's still true. I went two rounds past the recommendation because the CI engagement was real — but at +14 cumulative tests, the next +4 from round 7 would be in lower-priority controllers (per-node content surface or protocol endpoints), and the per-test value drops noticeably.

Folding remaining coverage into normal PR work is the right next mode.
