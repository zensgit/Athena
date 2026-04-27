# Phase 5 — Backend Security Test Fill: Design & Verification

**Commit:** `799fd70`
**Date:** 2026-04-27
**Scope:** Closes the last two `@WebMvcTest` security-test gaps in the Phase 5 controller surface (NotificationController, EmailIntegrationController).

---

## 1. Why

Phase 5 added several admin controllers, each delivered with a security
test that asserts the Spring Security filter chain rejects unauthenticated
requests with `401`. After PR-159..166 the matrix looked like this:

| Controller | Functional test | Security test (before) |
|---|---|---|
| LegalHoldController | LegalHoldControllerTest | LegalHoldControllerSecurityTest |
| LdapSyncController | LdapSyncControllerTest | LdapSyncControllerSecurityTest |
| DispositionScheduleController | DispositionScheduleControllerTest | DispositionScheduleControllerSecurityTest |
| LocalizedContentController | LocalizedContentControllerTest | LocalizedContentControllerSecurityTest |
| SiteInvitationController | SiteInvitationControllerTest | SiteInvitationControllerSecurityTest |
| **NotificationController** | NotificationControllerTest | **— missing —** |
| **EmailIntegrationController** | EmailIntegrationControllerTest | **— missing —** |

Both gaps are now filled.

## 2. Design

### 2.1 Pattern reused

Every Phase-5 security test follows the same shape, set by
`LdapSyncControllerSecurityTest`:

1. `@WebMvcTest(controllers = X.class)` — boots only the controller, `RestExceptionHandler`, and a `TestSecurityConfig` that forces `requestMatchers("/api/**").authenticated()`.
2. `@MockBean` for the service the controller depends on.
3. One unauthenticated case per endpoint asserting `status().isUnauthorized()`.
4. One or two `@WithMockUser` cases asserting the happy path returns 200 once the request is authenticated, proving the gate is `isAuthenticated()` and **not** role-based.

This isolates security from controller logic — the functional `*ControllerTest` already covers behavior, the security test only checks the filter-chain wiring.

### 2.2 NotificationControllerSecurityTest (9 tests)

`NotificationController` exposes 6 endpoints. The test asserts each one returns `401` unauthenticated, and that an authenticated `ROLE_USER` can hit the read endpoints and `mark-all-read`:

| Endpoint | Method | Unauth | Auth happy-path |
|---|---|---|---|
| `/api/v1/notifications` | GET | ✓ 401 | ✓ 200 (Page<Notification>) |
| `/api/v1/notifications/unread` | GET | ✓ 401 | implicit (same gate) |
| `/api/v1/notifications/unread-count` | GET | ✓ 401 | ✓ 200 (`{count: 5}`) |
| `/api/v1/notifications/mark-all-read` | POST | ✓ 401 | ✓ 200 (`{marked: 3}`) |
| `/api/v1/notifications/{id}/read` | PATCH | ✓ 401 | implicit (same gate) |
| `/api/v1/notifications/{id}` | DELETE | ✓ 401 | implicit (same gate) |

We don't repeat happy-path assertions for endpoints whose service mock would need extra setup (`markRead`, `deleteNotification`); the unauth 401 is the security-relevant claim. Authenticated coverage on those paths lives in the functional test.

### 2.3 EmailIntegrationControllerSecurityTest (2 tests)

`EmailIntegrationController` has one endpoint:

| Endpoint | Method | Unauth | Auth |
|---|---|---|---|
| `/api/v1/integration/email/ingest` | POST multipart | ✓ 401 | ✓ 200 |

Uses `MockMultipartFile` to simulate a `.eml` upload. The mock returns `null` so the controller body is `ResponseEntity.ok(null)` — Spring serializes that as `200 + empty body`, which is sufficient for the security claim.

## 3. Verification

### 3.1 Local

Local execution of `./mvnw test` is **blocked** in this environment: `ecm-core/mvnw` is a tiny POSIX wrapper that delegates to the official Maven Docker image (`docker run … maven:…`), and the dev machine has no Docker daemon. This is the same reason live-backend Playwright specs are `skipped` rather than `passed` in our last suite run.

The tests are written to compile against the same Spring Boot 3.2 / Spring Security 6 stack already in use by the green security tests in CI. Imports, annotations, configuration class, mock bean, and the `TestSecurityConfig` block are byte-identical to `LdapSyncControllerSecurityTest` and `SiteInvitationControllerSecurityTest`.

### 3.2 CI

Both files will be picked up by Surefire's default `**/*Test.java` glob and executed as part of the existing `ecm-core` Maven test stage. No CI changes required.

### 3.3 Verification checklist

| # | Item | Status |
|---|---|---|
| 1 | All 6 NotificationController endpoints have an unauthenticated-401 case | ✓ |
| 2 | At least one authenticated happy-path per HTTP verb (GET, POST) | ✓ |
| 3 | EmailIntegrationController ingest has unauthenticated-401 case | ✓ |
| 4 | EmailIntegrationController ingest has authenticated-200 case | ✓ |
| 5 | Tests use `@WebMvcTest` (no full context, no Docker dependency at compile time) | ✓ |
| 6 | Tests use the same `TestSecurityConfig` shape as siblings | ✓ |
| 7 | No mocked behavior beyond what the security claim needs | ✓ |
| 8 | Files follow the existing naming convention `<Controller>SecurityTest.java` | ✓ |
| 9 | Phase 5 controller surface has full security-test parity | ✓ |

## 4. After this commit

Phase 5 controllers all have parity:

| Controller | Functional test | Security test |
|---|---|---|
| LegalHoldController | ✓ | ✓ |
| LdapSyncController | ✓ | ✓ |
| DispositionScheduleController | ✓ | ✓ |
| LocalizedContentController | ✓ | ✓ |
| SiteInvitationController | ✓ | ✓ |
| **NotificationController** | ✓ | **✓ (this commit)** |
| **EmailIntegrationController** | ✓ | **✓ (this commit)** |
