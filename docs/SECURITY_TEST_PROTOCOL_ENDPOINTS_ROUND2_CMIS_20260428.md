# Protocol-Endpoint Security Tests — Round 2: CMIS Atom + Browser

**Commit:** `89233ff`
**Date:** 2026-04-28
**Scope:** Adds security tests for `CmisAtomPubController` and `CmisBrowserController`, plus an *inverse* drift-guard extension to `SecurityConfigProtocolSecurityTest`.

---

## 1. Why CMIS doesn't fit the round-1 mold

Round 1 (TransferReceiver / WOPI) tested controllers that sit on
`permitAll()` routes — the protocol carries its own opaque credential, and
the controller / service layer validates it. The tests had to mirror prod's
`permitAll` rules and prove the request reached the controller (i.e. the
bypass was working).

CMIS is structurally different. Production `SecurityConfig.java` does NOT
include `/api/cmis/**` in its `permitAll` list:

```java
.requestMatchers("/api/v1/share/access/**", "/api/share/access/**").permitAll()
.requestMatchers("/api/v1/transfer/receiver/**").permitAll()
.requestMatchers("/wopi/**").permitAll()
.requestMatchers("/api/**").authenticated()    // ← CMIS lands here
```

CMIS clients authenticate via JWT bearer (the standard `/api/**` mechanism);
there is no protocol-specific token seam. So this round looks more like
the legacy `@WithMockUser` backfill than round 1, with three CMIS-specific
concerns to surface explicitly.

## 2. CMIS-specific concerns documented by the tests

### 2.1 Both URL prefixes must require authentication

Both controllers register two prefixes (`/api/cmis/atom` and `/api/v1/cmis/atom`;
`/api/cmis/browser` and `/api/v1/cmis/browser`). The catch-all
`requestMatchers("/api/**").authenticated()` covers both — but the test makes
that explicit so a per-prefix carve-out (e.g. an accidentally added
`/api/cmis/**` permitAll) cannot bypass auth on one prefix while leaving the
other green.

### 2.2 Multi-binding does not relax security

The Atom binding (XML responses) and the Browser binding (JSON responses)
share the same auth gate. The Atom-XML response format does not give the
filter chain different visibility into the request than the Browser-JSON
binding. The tests prove this by hitting both bindings unauthenticated and
asserting both return 401.

### 2.3 Per-object ACL violations map to 403

Both CMIS controllers wrap service calls in
`try { ... } catch (SecurityException ex) { throw new ResponseStatusException(FORBIDDEN, ...); }`.
This converts a per-object ACL denial inside `CmisBrowserService` /
`CmisAclService` into a 403 — the CMIS-correct status. The tests prove this
by mocking the service to throw `SecurityException` and asserting 403 (NOT
500 from a missing handler, NOT 401 from a spurious filter rejection).

The two assertions covering this:

- `CmisAtomPubControllerSecurityTest.perObjectAclViolationMapsToForbidden`
- `CmisBrowserControllerSecurityTest.aclSecurityExceptionMapsToForbidden`

## 3. Inverse drift guard

`SecurityConfigProtocolSecurityTest` (added by Codex review of round 1)
already covered the *positive* drift claims for Transfer/WOPI: production
`SecurityConfig` must keep those paths permitAll.

Round 2 extends it with the *inverse* claim for CMIS:

```java
@Test
@DisplayName("production SecurityConfig protects /api/cmis/atom — CMIS is NOT permitAll (inverse drift guard)")
void productionSecurityConfigProtectsCmisAtomPath() throws Exception {
    mockMvc.perform(get("/api/cmis/atom")).andExpect(status().isUnauthorized());
}

@Test
@DisplayName("production SecurityConfig protects /api/cmis/browser — CMIS is NOT permitAll (inverse drift guard)")
void productionSecurityConfigProtectsCmisBrowserPath() throws Exception {
    mockMvc.perform(get("/api/cmis/browser").param("cmisselector", "repositoryInfo"))
        .andExpect(status().isUnauthorized());
}
```

If a future change accidentally adds `/api/cmis/**` (or `/api/v1/cmis/**`) to
the production permitAll list — for example while wiring a public CMIS
landing page or repository-info probe — these tests flip from 401 to 200 and
fail. That's the behavior we want: any move toward making CMIS anonymously
reachable should require an explicit, deliberate change to this test.

The inverse guard pattern is now established for any future controller that
should remain `authenticated()` and never be silently moved to permitAll.

## 4. Test breakdown

### 4.1 CmisAtomPubControllerSecurityTest — 7 tests

| Test | Expectation |
|---|---|
| unauth GET /api/cmis/atom (service doc) | 401 |
| unauth GET /api/v1/cmis/atom (/v1 prefix) | 401 |
| unauth GET /api/cmis/atom/object | 401 |
| unauth POST /api/cmis/atom/document | 401 |
| unauth DELETE /api/cmis/atom/object | 401 |
| ROLE_USER GET /api/cmis/atom | 200 (JSON service doc serialized) |
| ROLE_USER GET /api/cmis/atom/object with SecurityException | **403** (load-bearing) |

### 4.2 CmisBrowserControllerSecurityTest — 7 tests

| Test | Expectation |
|---|---|
| unauth GET /api/cmis/browser?cmisselector=repositoryInfo | 401 |
| unauth GET /api/v1/cmis/browser (/v1 prefix) | 401 |
| unauth POST /api/cmis/browser?cmisaction=createDocument | 401 |
| unauth POST /api/cmis/browser/acl | 401 |
| unauth POST /api/cmis/browser/relationships | 401 |
| ROLE_USER GET ?cmisselector=repositoryInfo | 200 (JSON) |
| ROLE_USER POST /acl with SecurityException | **403** (load-bearing) |

### 4.3 SecurityConfigProtocolSecurityTest — +2 tests (now 7 total)

Existing 5 (Transfer/WOPI permitAll claims + ordinary `/api/**` 401) plus:

| Test | Expectation |
|---|---|
| GET /api/cmis/atom against real SecurityConfig | 401 (NOT permitAll) |
| GET /api/cmis/browser against real SecurityConfig | 401 (NOT permitAll) |

## 5. Verification

### 5.1 Local

Used the documented Maven workaround:

```bash
cd ecm-core
/tmp/codex-maven/apache-maven-3.9.11/bin/mvn -q \
  -Dmaven.repo.local=.m2-cache/repository \
  -Dspring.profiles.active=test \
  '-Dtest=CmisAtomPubControllerSecurityTest,CmisBrowserControllerSecurityTest,SecurityConfigProtocolSecurityTest' \
  test
```

Targeted result:
- CmisAtomPubControllerSecurityTest: tests=7, errors=0, skipped=0, failures=0
- CmisBrowserControllerSecurityTest: tests=7, errors=0, skipped=0, failures=0
- SecurityConfigProtocolSecurityTest: tests=7, errors=0, skipped=0, failures=0

Full controller-security sweep:
```
security_files=41 tests=388 failures=0 errors=0 skipped=0
```

(Was 39/372 before this round — +2 files, +16 tests, no regressions.)

### 5.2 Verification checklist

| # | Item | Status |
|---|---|---|
| 1 | Both Atom URL prefixes (`/api/cmis/atom`, `/api/v1/cmis/atom`) carry an unauth-401 case | ✓ |
| 2 | Both Browser URL prefixes carry an unauth-401 case | ✓ |
| 3 | Atom-XML and Browser-JSON bindings both have an authenticated-200 happy-path | ✓ |
| 4 | Per-object ACL `SecurityException` → 403 mapping covered for both controllers | ✓ |
| 5 | Inverse drift guard added — `/api/cmis/**` is asserted to remain `authenticated()` against the real SecurityConfig | ✓ |
| 6 | Surefire green on the targeted run | ✓ |
| 7 | Surefire green on the full controller-security sweep | ✓ (41/388/0/0/0) |
| 8 | `.env` untouched | ✓ |

## 6. What's next

### 6.1 Remaining protocol-endpoint candidates

After round 2:

- **`TransferReplicationController`** — sender side of the transfer protocol; per-job tokens. Smaller surface than the receiver. Could ship as round 3 alongside any companion sender-side logic.
- **`WopiIntegrationController`** — companion to `WopiHostController`. Smaller, could fold into a future round.

These are lower-priority than round 1 (TransferReceiver/WOPI Host) because they're admin-facing rather than externally-callable. A round 3 covering both is reasonable but not urgent.

### 6.2 Property Encryption "operations design" track

Still deferred per Codex direction. When picked up:
1. Design the encryption operations API surface (key rotation / rewrap / masking visibility / audit) at the controller level
2. Decide which operations need an admin endpoint vs. CLI vs. nothing
3. Only after that, design any UI

### 6.3 Out of scope for this commit

- `.env` modifications (Codex direction)
- Any frontend changes
- TransferReplication / WopiIntegration controllers (round 3)
- Property Encryption operations API (separate track)
