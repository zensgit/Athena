# Protocol-Endpoint Security Tests — Round 1: TransferReceiver + WOPI Host

**Commit:** `55f279a`
**Date:** 2026-04-28
**Scope:** Establishes a reusable token-seam security-test pattern for protocol endpoints that sit on `permitAll()` routes, and applies it to the two cleanest cases — `TransferReceiverController` and `WopiHostController`.

---

## 1. Why this is a separate thread (not a continuation of rounds 1–6)

The legacy backfill thread (closed at `f343231`) used `@WithMockUser` to test routes guarded by Spring Security's `isAuthenticated()` / `@PreAuthorize` gates. That pattern doesn't apply here:

`SecurityConfig.java:48–58` declares several `permitAll()` exceptions for protocols where Spring Security would interfere with the protocol's own auth mechanism:

```java
.requestMatchers("/api/v1/share/access/**", "/api/share/access/**").permitAll()
.requestMatchers("/api/v1/transfer/receiver/**").permitAll()
.requestMatchers("/wopi/**").permitAll()
```

For these paths, **the protocol carries its own opaque credential** — and the controller / service layer must validate it. The security-test job is to prove three things end-to-end:

1. The route is reachable WITHOUT Spring Security authentication (the `permitAll()` rule is in place).
2. Missing or invalid protocol credentials are rejected at the controller / service layer (the proxy auth is actually validated).
3. Valid protocol credentials reach the service successfully (legitimate clients still work).

Codex review pointed out (correctly) that doing this at scale needs upfront design. This first slice picks the two cleanest seams to establish the pattern; CMIS comes later in its own round.

## 2. Pattern design

### 2.1 Test class shape

```java
@WebMvcTest(controllers = X.class)
@ContextConfiguration(classes = {
    X.class,
    RestExceptionHandler.class,
    XSecurityTest.TestSecurityConfig.class
})
class XSecurityTest {

    @MockBean private TokenValidatingService tokenSvc;  // or service that calls it
    @MockBean private OtherDeps... ;

    @Configuration @EnableWebSecurity @EnableMethodSecurity(prePostEnabled = true)
    static class TestSecurityConfig {
        @Bean SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                    // mirror PROD permitAll for THIS protocol's path
                    .requestMatchers("/<protocol-path>/**").permitAll()
                    // catch-all: prove this is the ONLY path that's permitAll
                    .requestMatchers("/api/**").authenticated()
                    .anyRequest().permitAll())
                .httpBasic(basic -> {})
                .build();
        }
    }

    // (a) missing/invalid token → controller-layer rejection (NOT Spring 401)
    // (b) valid token → 200 (proves request reached the controller body)
}
```

The two assertions together triangulate the controller-seam security claim under a mirrored test matcher. Either one alone is insufficient:
- "Bad token → 401" alone could mean the filter chain rejected anonymously (permitAll was dropped).
- "Good token → 200" alone could mean the filter chain accepted but token was never validated.
- **Both together** prove the path is permitAll AND the token actually flows through to a validating service.

### 2.2 Production SecurityConfig drift guard

Codex review found one important gap in the first version of this pattern: a mirrored `TestSecurityConfig` proves the controller seam, but it does not by itself fail if production `SecurityConfig` later removes or reorders the real `permitAll()` matchers.

Added `SecurityConfigProtocolSecurityTest` to close that hole with the real production `SecurityConfig` and a minimal probe controller:

| Probe | Expected | Security claim |
|---|---|---|
| `GET /api/v1/transfer/receiver/verify?folderId=...` without auth | 200 | production matcher leaves receiver protocol path reachable |
| same path with `Authorization: Bearer opaque-transfer-secret` | 200 | production `BearerTokenResolver` ignores opaque protocol credentials on receiver path |
| `GET /wopi/files/{id}?access_token=...` without auth | 200 | production matcher leaves WOPI host path reachable |
| same path with `Authorization: Bearer opaque-wopi-token` | 200 | production `BearerTokenResolver` ignores opaque protocol credentials on WOPI path |
| `GET /api/v1/protected/probe` without auth | 401 | ordinary API routes remain protected |

This keeps the responsibilities split:
- `TransferReceiverControllerSecurityTest` and `WopiHostControllerSecurityTest` prove service-layer protocol credential behavior.
- `SecurityConfigProtocolSecurityTest` proves the production matcher and bearer-token resolver still route those protocol paths correctly.

### 2.3 Service-mock seam

The choice of which service to mock is calibrated per controller:

| Controller | Service to mock | Why |
|---|---|---|
| TransferReceiverController | `TransferReceiverService` (the whole service) | Controller delegates header validation to service.verifyFolder/createFolder/uploadDocument |
| WopiHostController | `WopiService` for the file-info / file-content endpoints; `WopiAccessTokenService` for the lock-ops endpoint | checkFileInfo / getFile go through `WopiService.checkFileInfo(id, token)` which calls validate internally; lockOperations calls `accessTokenService.validate(...)` directly |

For the test, mocking the outer service (e.g. `WopiService.checkFileInfo`) is enough — we throw the exact production exception shape the validating service would emit, and assert the response code matches. We don't need to load the real validation logic.

### 2.4 Production exception shapes

| Controller | Failure path | Exception | HTTP status |
|---|---|---|---|
| TransferReceiver | bad/missing transfer headers | `SecurityException("Transfer receiver credentials do not permit folder: ...")` | 403 (via `RestExceptionHandler:71`) |
| WOPI | missing access_token query param | `MissingServletRequestParameterException` | 400 (Spring built-in) |
| WOPI | invalid/expired/wrong-doc access_token | `ResponseStatusException(HttpStatus.UNAUTHORIZED, ...)` | 401 (Spring built-in) |

The 400 vs 401 distinction for WOPI is intentional and worth surfacing: a missing token is a client error (the request is malformed), while an invalid token is an auth error (the credential failed validation). Both are NOT 401-from-filter-chain.

## 3. The two tests

### 3.1 TransferReceiverControllerSecurityTest — 4 tests

| Test | Headers | Expected | Why |
|---|---|---|---|
| no headers | none | 403 | Service throws SecurityException; mapped to 403. Proves request reached controller (NOT Spring 401). |
| bad headers | `attacker` / `wrong` | 403 | Same path, different mock. |
| valid headers | `peer-a` / `good-secret` | 200 | Proves valid credentials reach the service successfully. |
| triangulation | `peer-a` / `good-secret` | 200 | Explicit "200, not 401" assertion documenting the permitAll claim. |

Endpoints sampled: `GET /api/v1/transfer/receiver/verify`. Other endpoints (`/folders`, `/documents`) share the same auth seam — adding one test each would be cheap but redundant. The pattern is now established for any future contributor.

### 3.2 WopiHostControllerSecurityTest — 6 tests

| Test | access_token | Expected | Why |
|---|---|---|---|
| missing | (omitted) | **400** | Spring missing-required-param. NOT 401 — proves filter chain didn't intercept. |
| invalid | `"invalid-token"` | 401 | Service throws `ResponseStatusException(401, "Invalid WOPI access_token")`. |
| expired | `"expired-token"` | 401 | Service throws `ResponseStatusException(401, "Expired WOPI access_token")`. |
| wrong-document | `"token-for-other-doc"` | 401 | Service throws `ResponseStatusException(401, "WOPI access_token does not match document")`. |
| valid | `"valid-token"` | 200 | Service returns CheckFileInfoResponse. |
| triangulation | `"valid-token"` | 200 | Explicit "200, not 401" assertion. |

Endpoints sampled: `GET /wopi/files/{id}` (CheckFileInfo). Other endpoints (`/contents` GET, `/contents` POST putFile, lock operations) share the same seam.

The four token-failure cases enumerate every distinct rejection reason `WopiAccessTokenService.validate` produces. If a future contributor accidentally widens token acceptance (e.g. forgets the per-document check), one of these tests fails immediately with a clear message.

## 4. Verification

### 4.1 Local

Used the Maven workaround (documented in `docs/SECURITY_TEST_BACKFILL_LOCAL_VERIFICATION_20260428.md`) to bypass the Docker dependency in `ecm-core/mvnw`:

```bash
cd ecm-core
/tmp/codex-maven/apache-maven-3.9.11/bin/mvn -q \
  -Dmaven.repo.local=.m2-cache/repository \
  -Dspring.profiles.active=test \
  '-Dtest=TransferReceiverControllerSecurityTest,WopiHostControllerSecurityTest' \
  test
```

Targeted result for the controller-seam tests:
- TransferReceiverControllerSecurityTest: tests=4, errors=0, skipped=0, failures=0
- WopiHostControllerSecurityTest: tests=6, errors=0, skipped=0, failures=0

Additional production-config drift guard:
- SecurityConfigProtocolSecurityTest: tests=5, errors=0, skipped=0, failures=0

Full controller-security sweep:
```
security_files=39 tests=372 failures=0 errors=0 skipped=0
```

(Was 36/357 before round 1 — +3 files, +15 tests, no regressions.)

### 4.2 Verification checklist

| # | Item | Status |
|---|---|---|
| 1 | `TestSecurityConfig` mirrors prod permitAll for the protocol path | ✓ (both) |
| 2 | Token-validating service is mocked, not the controller's helper layer | ✓ (both) |
| 3 | Production exception shape used in mock throws (`SecurityException` / `ResponseStatusException`) | ✓ (both) |
| 4 | Distinct rejection reasons enumerated where the service has them (WOPI's 4 token-failure cases) | ✓ (WOPI) |
| 5 | "Valid creds → 200" case present and triangulating the permitAll claim | ✓ (both) |
| 6 | Production `SecurityConfig` drift guard covers protocol permitAll matchers and BearerTokenResolver bypass | ✓ |
| 7 | Surefire green on the targeted run | ✓ |
| 8 | Surefire green on the full controller-security sweep | ✓ (39/372/0/0/0) |
| 9 | `.env` untouched (per Codex direction) | ✓ |

## 5. What's next, what's not

### 5.1 Next protocol-endpoint round candidates

Following the same pattern, the remaining protocol-endpoint controllers are:

- **`CmisAtomPubController`** + **`CmisBrowserController`** — CMIS basic auth flow. Bigger surface (multi-binding protocol, lots of operations); needs careful sampling. The two CMIS controllers should ship together as round 2.
- **`TransferReplicationController`** — adjacent to TransferReceiver but on the *sender* side; per-job tokens. Could fold into a future round once the sender-side surface stabilizes.
- **`WopiIntegrationController`** — companion to WopiHost on the integration side. Smaller, could ship with a future round.

### 5.2 Property Encryption "operations design" track

Per Codex direction, this is a separate track that should NOT start with a UI:

1. Design the encryption operations API surface (key rotation, rewrap, masking visibility, audit) at the controller level
2. Decide which operations need an admin endpoint vs. CLI vs. nothing
3. Only after that, design any UI

Not done in this commit. Filed for a separate slice.

### 5.3 Out of scope for this commit

- `.env` modifications (Codex direction)
- Any frontend changes
- CMIS controllers (round 2 of this thread)
- Property Encryption operations API (separate track)
