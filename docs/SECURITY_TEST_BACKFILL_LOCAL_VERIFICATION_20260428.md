# Security-Test Backfill — Local Verification Record

**Date:** 2026-04-28
**Scope:** Independent local reproduction of the green Surefire result documented in `SECURITY_TEST_LEGACY_FILL_ROUND6_AND_THREAD_CLOSEOUT_20260428.md` §3.1.

This is **not** another backfill round. The round-6 closeout doc explicitly recommends against further speculative rounds, and that stands. This is a verification-only artifact: I had previously claimed "local verification is blocked by Docker" across rounds 1–6, but the round-6 doc was updated with a Maven workaround that does work locally. Running it now to close my own verification gap.

---

## 1. The Maven workaround

`ecm-core/mvnw` is a thin POSIX wrapper around the Maven Docker image. Without a Docker daemon (the case on this dev box), `./mvnw` fails. The workaround is to use a stand-alone Maven binary against the repo-local artifact cache:

```bash
cd ecm-core
/tmp/codex-maven/apache-maven-3.9.11/bin/mvn -q \
  -Dmaven.repo.local=.m2-cache/repository \
  -Dspring.profiles.active=test \
  '-Dtest=*SecurityTest' \
  test
```

Both `/tmp/codex-maven/apache-maven-3.9.11/` and `ecm-core/.m2-cache/repository/` already exist on the box from prior CI/codex runs. No new artifacts to fetch.

## 2. Result

After the run, parsed Surefire XML reports under `ecm-core/target/surefire-reports/TEST-*SecurityTest.xml`:

```text
security_files=36 tests=357 failures=0 errors=0 skipped=0
```

Counted via:

```bash
python3 -c "
import os, re
total_tests = total_failures = total_errors = total_skipped = 0
files = sorted([f for f in os.listdir('.') if f.startswith('TEST-') and 'SecurityTest' in f and f.endswith('.xml')])
for fn in files:
    with open(fn) as f:
        head = f.read(2000)
    m = re.search(r'tests=\"(\\d+)\"\\s+errors=\"(\\d+)\"\\s+skipped=\"(\\d+)\"\\s+failures=\"(\\d+)\"', head)
    if m:
        t, e, s, fa = (int(m.group(i)) for i in range(1,5))
        total_tests += t; total_failures += fa; total_errors += e; total_skipped += s
print(f'security_files={len(files)} tests={total_tests} failures={total_failures} errors={total_errors} skipped={total_skipped}')
"
```

This **matches** the number documented in the round-6 closeout doc (which used the same workaround). All seven backfill commits land green:

| Round | Tests added | File present in report? |
|---|---|---|
| Phase 5 close (`799fd70`) | NotificationController, EmailIntegrationController | ✓ |
| 1 (`082e9cd`) | MFA, Webhook, TenantAdmin | ✓ |
| 2 (`3283ec5`) | Rule | ✓ |
| 3 (`de72cfa`) | User, Group, ShareLink | ✓ |
| 4 (`1d53933`) | Trash, BulkOperation, BulkImport | ✓ |
| 5 (`7dbfc91`) | Script, PermissionTemplate | ✓ |
| 6 (`4c66a7e`) | License, SecurityController | ✓ |

Confirmed by `ls TEST-*ScriptControllerSecurityTest.xml TEST-*LicenseControllerSecurityTest.xml TEST-*SecurityControllerSecurityTest.xml TEST-*PermissionTemplateControllerSecurityTest.xml`.

## 3. What this changes

Two corrections to the verification narrative I'd been writing across rounds:

1. **"Local verification is blocked by Docker" was incomplete.** It was true for `./mvnw` specifically. The actual reachable workaround is a stand-alone Maven binary + repo-local cache. Future rounds-doc verification sections should reference this workaround rather than just declaring local-blocked.
2. **The 357-tests-green number is now independently reproducible** — not just from the user's CI run, but on a fresh local invocation in this dev environment. That's a stronger evidence claim than the original "trust me, the pattern matches green sibling tests" wording.

## 4. What this does NOT change

The round-6 closeout still stands. The thread is still closed.

This doc adds verification confidence; it does not reopen the backfill or argue for round 7. The remaining ~42 untested controllers should still be:

- **Per-node content surface:** opportunistically tested as future PRs touch them
- **Read-mostly metadata APIs:** likely not worth dedicated tests
- **Protocol endpoints (CMIS/WOPI/Transfer):** require a different test design than `@WithMockUser` and warrant their own dedicated effort, not a continuation of this thread

## 5. Verification checklist

| # | Item | Status |
|---|---|---|
| 1 | Maven workaround documented in round-6 doc executes locally | ✓ |
| 2 | Full `*SecurityTest` sweep runs to completion | ✓ |
| 3 | All 36 security test files contribute 0 failures, 0 errors, 0 skipped | ✓ |
| 4 | Test count (357) matches the round-6 closeout doc | ✓ |
| 5 | Round-6 files (License, SecurityController) present in Surefire reports | ✓ |
| 6 | Round-5 files (Script, PermissionTemplate) present in Surefire reports | ✓ |
| 7 | Closeout decision unchanged — no scope expansion in this doc | ✓ |
