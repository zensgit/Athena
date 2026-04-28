# Security-Test Backfill CI Fixes: Design & Verification

**Date:** 2026-04-28
**Scope:** Fixes the Backend Verify failure from CI run `25026507786` after the legacy controller security-test backfill.

## 1. Context

CI run `25026507786` proved the frontend and Phase 5 mocked gate are now stable:

| Job | Result |
|---|---|
| Frontend Build & Test | success |
| Phase 5 Mocked Regression Gate | success |
| Backend Verify | failure |
| Phase C Security Verification | skipped because Backend Verify failed |
| Acceptance Smoke | skipped because Backend Verify failed |
| Frontend E2E Core Gate | skipped because Backend Verify failed |

Backend Verify failed in newly added `@WebMvcTest` security tests, not in production compilation. The failures were all test-fixture issues that prevented the tests from reaching the intended authorization assertions.

## 2. Root Causes

### 2.1 PageImpl without a concrete Pageable

Five tests used:

```java
new PageImpl<>(List.of())
```

That constructor creates an unpaged response. When Spring/Jackson serializes the returned `Page`, it touches unpaged metadata that can throw `UnsupportedOperationException`, producing a 500 before the test can assert the security gate.

Affected tests:

| Test | CI symptom |
|---|---|
| `NotificationControllerSecurityTest.authenticatedUserCanListInbox` | expected 200, got 500 |
| `GroupControllerSecurityTest.userCanListGroups` | expected 200, got 500 |
| `UserControllerSecurityTest.userCanSearchUsers` | expected 200, got 500 |
| `RuleControllerSecurityTest.userCanListRules` | expected 200, got 500 |
| `BulkImportControllerSecurityTest.userCanListJobs` | expected 200, got 500 |

Fix: return `new PageImpl<>(List.of(), PageRequest.of(0, 20), 0)` so the serialized response has concrete pageable metadata.

### 2.2 Bulk history repository was not stubbed

`BulkOperationControllerSecurityTest.editorCanReadBulkHistory` verifies that `ROLE_EDITOR` is admitted by the admin-or-editor gate. The controller then calls `AuditLogRepository.findBulkOperationTimelineNoNodeId(...)`; without a stub, Mockito returned `null`, causing:

```text
NullPointerException: Cannot invoke "org.springframework.data.domain.Page.getContent()" because "historyPage" is null
```

Fix: stub the repository to return an empty `Page<AuditLog>` with `PageRequest.of(0, 20)`.

### 2.3 Permission template apply missed required request parameter

`PermissionTemplateController.applyTemplate(...)` requires `nodeId` as a request parameter. The USER/EDITOR forbidden-path tests omitted it, so Spring MVC returned 400 during argument binding before method security could return 403.

Fix: include `.param("nodeId", UUID.randomUUID().toString())` in both forbidden-path requests.

## 3. Design

No production authorization rule was changed.

The fix keeps the security-test intent intact:

| Area | Design decision |
|---|---|
| Pageable controller happy paths | Use concrete `PageRequest` in fixtures so serialization does not hide authorization behavior |
| Bulk history editor happy path | Mock only the minimum repository read needed after `@PreAuthorize` admits the user |
| Permission template forbidden paths | Provide required MVC arguments so the request reaches method security |
| Existing public API | No API, service, entity, migration, or frontend change |

## 4. Verification

### 4.1 Local targeted regression

Command:

```bash
/tmp/codex-maven/apache-maven-3.9.11/bin/mvn -q \
  -Dmaven.repo.local=.m2-cache/repository \
  -Dspring.profiles.active=test \
  -Dtest=NotificationControllerSecurityTest,GroupControllerSecurityTest,UserControllerSecurityTest,RuleControllerSecurityTest,BulkImportControllerSecurityTest,BulkOperationControllerSecurityTest,PermissionTemplateControllerSecurityTest \
  test
```

Result:

```text
70 tests, 0 failures, 0 errors, 0 skipped
```

### 4.2 Local security-test sweep

Command:

```bash
/tmp/codex-maven/apache-maven-3.9.11/bin/mvn -q \
  -Dmaven.repo.local=.m2-cache/repository \
  -Dspring.profiles.active=test \
  '-Dtest=*SecurityTest' \
  test
```

Result from Surefire XML:

```text
security_files=34 tests=344 failures=0 errors=0 skipped=0
```

### 4.3 Static check

Command:

```bash
git diff --check
```

Result: passed.

## 5. Notes

The repo's `ecm-core/mvnw` delegates to Docker. This machine had no Docker daemon available, so validation used a temporary Maven binary in `/tmp/codex-maven` with the repo-local `.m2-cache/repository`.

Full CI validation is still required after push because Backend Verify is the gate that also runs the full Surefire suite.
