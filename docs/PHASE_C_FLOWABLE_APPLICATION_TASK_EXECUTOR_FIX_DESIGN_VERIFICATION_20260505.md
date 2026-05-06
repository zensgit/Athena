# Phase C Flowable Application Task Executor Fix Design Verification

Date: 2026-05-05

## Context

GitHub Actions run `25418606323` proved the Property Encryption closeout target:

```text
Backend Verify: success
Frontend Build & Test: success
Property Encryption Closeout Gate: success
Property Encryption Closeout Gate job ID: 74556589054
```

The same run then failed `Phase C Security Verification` during `Start verification stack`, before `scripts/verify.sh` could execute.

Failure signature from job `74555631087`:

```text
Error creating bean with name 'springProcessEngineConfiguration'
No qualifying bean of type 'org.springframework.core.task.AsyncTaskExecutor' available
Dependency annotations: {@org.springframework.beans.factory.annotation.Qualifier("applicationTaskExecutor")}
```

## Root Cause

`PropertyEncryptionAsyncConfiguration` introduced two named `ThreadPoolTaskExecutor` beans:

```text
propertyEncryptionBackfillTaskExecutor
propertyEncryptionRewrapTaskExecutor
```

Those custom executor beans can suppress Spring Boot's default task-execution auto-configuration. Flowable 7 still expects a qualified `AsyncTaskExecutor` named:

```text
applicationTaskExecutor
```

When the Docker application context starts with Flowable enabled, the missing bean prevents `ecm-core` from reaching `/actuator/health`, so Phase C times out/fails at stack startup.

## Design

Restore the default application executor contract explicitly while preserving the property-encryption executors as isolated bounded workers.

Implementation:

- Add a primary bounded `ThreadPoolTaskExecutor`.
- Expose the same bean under both `applicationTaskExecutor` and `taskExecutor`.
- Keep the property-encryption backfill and rewrap executors unchanged.
- Guard the application executor with `@ConditionalOnMissingBean` so a future explicit app executor can override it.

Executor names after the fix:

| Bean name | Purpose |
| --- | --- |
| `applicationTaskExecutor` | Flowable-qualified async executor |
| `taskExecutor` | Spring default `@Async` lookup alias |
| `propertyEncryptionBackfillTaskExecutor` | bounded property-encryption backfill worker |
| `propertyEncryptionRewrapTaskExecutor` | bounded property-encryption rewrap worker |

## Files Changed

```text
ecm-core/src/main/java/com/ecm/core/config/PropertyEncryptionAsyncConfiguration.java
ecm-core/src/test/java/com/ecm/core/config/PropertyEncryptionAsyncConfigurationTest.java
```

## Verification

Targeted backend test command:

```bash
cd ecm-core
/tmp/apache-maven-3.9.9/bin/mvn -B -q -Dstyle.color=never \
  -Dmaven.repo.local=.m2-cache/repository \
  -Dspring.profiles.active=test \
  -Dtest=PropertyEncryptionAsyncConfigurationTest,PropertyEncryptionBackfillRunnerAsyncProxyTest \
  test
```

Result:

```text
PropertyEncryptionAsyncConfigurationTest: Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
PropertyEncryptionBackfillRunnerAsyncProxyTest: Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
```

Whitespace:

```bash
git diff --check
```

Result: passed with no output.

## CI Expectation

The next pushed run should start `ecm-core` with Flowable enabled because the qualified `applicationTaskExecutor` is now present.

Expected effect:

- `Property Encryption Closeout Gate` remains green.
- `Phase C Security Verification` advances past `Start verification stack`.
- If Phase C fails later inside `scripts/verify.sh`, that should be triaged as a separate security-verification behavior issue rather than this startup blocker.

