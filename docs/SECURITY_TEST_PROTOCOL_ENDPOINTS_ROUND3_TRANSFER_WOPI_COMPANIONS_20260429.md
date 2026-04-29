# Protocol-Endpoint Security Tests - Round 3: Transfer + WOPI Companions

**Commit:** `cdd0aa3`
**Date:** 2026-04-29
**Scope:** Adds sender-side Transfer Replication and application-facing WOPI Integration security coverage, plus exact production `SecurityConfig` drift guards for the adjacent API routes.

## 1. Context

Rounds 1 and 2 covered the externally callable protocol seams:

| Round | Surface | Route security model |
|---|---|---|
| 1 | Transfer receiver, WOPI host | `permitAll()` route plus protocol token validation |
| 2 | CMIS Atom, CMIS Browser | ordinary `/api/**` authentication plus service ACL |
| 3 | Transfer sender/admin, WOPI integration companion | ordinary `/api/**` authentication plus method/service authorization |

Round 3 is intentionally different from Round 1. These endpoints are not public protocol callbacks. They are companion/admin endpoints that sit next to protocol routes and must not inherit the public `permitAll()` carve-outs.

## 2. Security Claims

### 2.1 Transfer sender/admin endpoints are not public

Production allows anonymous access only to the receiver path:

```java
.requestMatchers("/api/v1/transfer/receiver/**").permitAll()
.requestMatchers("/api/**").authenticated()
```

The sender-side `TransferReplicationController` stays under `/api/**` authentication and class-level `@PreAuthorize("hasRole('ADMIN')")`.

The new tests prove three layers:

| Layer | Assertion |
|---|---|
| Filter chain | Anonymous `/api/v1/transfer/targets` returns 401 |
| Controller method security | `ROLE_USER` on transfer/replication management endpoints returns 403 |
| Admin happy path | `ROLE_ADMIN` reaches representative service calls and returns 200/202 |

`TransferReplicationServiceSecurityTest` also proves the service-level `requireAdmin()` guard rejects non-admin callers before repository access.

### 2.2 WOPI app integration is not the public WOPI host

Production allows anonymous access to the WOPI host protocol only:

```java
.requestMatchers("/wopi/**").permitAll()
.requestMatchers("/api/**").authenticated()
```

`/api/v1/integration/wopi/**` is application-facing API surface. It must remain authenticated and then apply document-level permission checks when issuing editor URLs.

The new tests prove:

| Layer | Assertion |
|---|---|
| Filter chain | Anonymous `/api/v1/integration/wopi/health` and `/url/{documentId}` return 401 |
| Authenticated app API | `ROLE_USER` can call health and service-approved editor URL paths |
| Service permission | read URL checks `PermissionType.READ`, write URL checks `PermissionType.WRITE` |
| Token safety | permission denial stops before `WopiAccessTokenService.issue(...)` |

### 2.3 Production drift guard is exact, not generic

Round 2 already had a generic `/api/v1/protected/probe` 401 assertion. Round 3 adds exact adjacent-route probes so future matcher drift is caught at the route family where it matters.

New `SecurityConfigProtocolSecurityTest` inverse guards:

| Path | Expected |
|---|---|
| `/api/v1/transfer/targets` | 401 |
| `/api/v1/replication/jobs` | 401 |
| `/api/v1/integration/wopi/health` | 401 |
| `/api/v1/integration/wopi/url/{documentId}` | 401 |

If a future change accidentally expands `permitAll()` to `/api/v1/transfer/**` or `/api/v1/integration/wopi/**`, these tests flip from 401 to 200 and fail.

## 3. Test Breakdown

### 3.1 TransferReplicationControllerSecurityTest - 10 tests

| Area | Coverage |
|---|---|
| Unauthenticated | list transfer targets returns 401 |
| Non-admin | list targets, verify target, run definition, list jobs return 403 |
| Admin | list targets returns 200, verify target returns 200, run definition returns 202, list jobs returns 200 |
| Exception mapping | service `SecurityException` maps to 403 |

### 3.2 WopiIntegrationControllerSecurityTest - 5 tests

| Area | Coverage |
|---|---|
| Unauthenticated | health returns 401, editor URL returns 401 |
| Authenticated | health returns 200, editor URL returns 200 |
| Exception mapping | service `SecurityException` maps to 403 |

### 3.3 TransferReplicationServiceSecurityTest - 2 tests

| Area | Coverage |
|---|---|
| Non-admin | `listTargets()` throws before repository access |
| Admin | `listTargets()` passes through to repository |

### 3.4 WopiEditorServiceSecurityTest - 3 tests

| Area | Coverage |
|---|---|
| Read URL | checks `PermissionType.READ` before issuing read token |
| Write URL | checks `PermissionType.WRITE` before issuing writable token |
| Denied permission | throws and does not issue a token |

### 3.5 SecurityConfigProtocolSecurityTest - +4 tests, now 13 total

Existing positive guards for Transfer receiver and WOPI host remain unchanged. Round 3 adds four inverse guards for Transfer sender/admin and WOPI app-facing companion routes.

## 4. Verification

### 4.1 Targeted run

Command:

```bash
cd ecm-core
/tmp/codex-maven/apache-maven-3.9.11/bin/mvn -q \
  -Dmaven.repo.local=.m2-cache/repository \
  -Dspring.profiles.active=test \
  -Dtest=TransferReplicationControllerSecurityTest,WopiIntegrationControllerSecurityTest,SecurityConfigProtocolSecurityTest,TransferReplicationServiceSecurityTest,WopiEditorServiceSecurityTest \
  test
```

Result:

| Test class | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| SecurityConfigProtocolSecurityTest | 13 | 0 | 0 | 0 |
| TransferReplicationControllerSecurityTest | 10 | 0 | 0 | 0 |
| WopiIntegrationControllerSecurityTest | 5 | 0 | 0 | 0 |
| WopiEditorServiceSecurityTest | 3 | 0 | 0 | 0 |
| TransferReplicationServiceSecurityTest | 2 | 0 | 0 | 0 |
| **Total** | **33** | **0** | **0** | **0** |

### 4.2 Full security sweep

Command:

```bash
cd ecm-core
/tmp/codex-maven/apache-maven-3.9.11/bin/mvn -q \
  -Dmaven.repo.local=.m2-cache/repository \
  -Dspring.profiles.active=test \
  '-Dtest=*SecurityTest' \
  test
```

Result:

```text
security_files=45 tests=414 failures=0 errors=0 skipped=0
```

Before this round, the full security sweep was 41 files / 390 tests. Round 3 added 4 files and 24 tests with no regressions.

### 4.3 Diff hygiene

Command:

```bash
git diff --check
```

Result: clean.

## 5. Review Notes

| Decision | Reason |
|---|---|
| No production code change | Existing route and service boundaries were correct; missing item was regression coverage. |
| No `.env` change | Local `.env` remained uncommitted and outside this work. |
| No frontend change | This round covers backend security seams only. |
| No broad CMIS change | Round 2 already closed CMIS Atom/Browser coverage. |

## 6. Remaining Work

The protocol-endpoint security-test thread is now effectively closed for the currently identified surfaces:

| Surface | Status |
|---|---|
| Transfer receiver | Covered in Round 1 |
| WOPI host | Covered in Round 1 |
| CMIS Atom/Browser | Covered in Round 2 |
| Transfer sender/admin companion | Covered in Round 3 |
| WOPI app-facing companion | Covered in Round 3 |

Future work should only reopen this thread if a new public protocol endpoint is added, such as WebDAV, IMAP, FTP, or another callback-style integration with a non-JWT credential seam.

Property Encryption remains a separate operations-design track. It should start API-first, not UI-first, because the missing product decision is which encryption operations should be exposed at all.
