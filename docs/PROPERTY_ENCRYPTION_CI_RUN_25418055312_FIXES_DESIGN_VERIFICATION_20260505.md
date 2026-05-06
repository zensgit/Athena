# Property Encryption CI Run 25418055312 Fixes Design Verification

Date: 2026-05-05

## Context

After pushing the Property Encryption closeout gate, GitHub Actions run `25418055312` started successfully but failed before the new closeout job could run.

Failed jobs:

- `Backend Verify`
- `Frontend Build & Test`

Skipped because of failed prerequisites:

- `Property Encryption Closeout Gate`
- `Phase C Security Verification`
- `Acceptance Smoke`
- `Frontend E2E Core Gate`
- `Phase 5 Mocked Regression Gate`

This slice fixes the concrete CI failures that blocked the closeout gate.

## Backend Failures

CI failures:

```text
NodeRepositoryJsonbBackfillSmokeTest.jsonbBackfillPredicatesMatchPostgresSemantics
PropertyEncryptionBackfillJobRepositoryTest.cancelRequestTransitionsPlannedAndRunningJobsWithExpectedStatusGuards
PropertyEncryptionBackfillJobRepositoryTest.claimAndTerminalUpdateRequireExpectedStatus
PropertyEncryptionBackfillPostgresIntegrationTest.backfillJobPlansAndMigratesPlaintextPropertyOnPostgres
PropertyEncryptionBackfillPostgresIntegrationTest.rewrapJobPlansAndMigratesEncryptedPropertyOnPostgres
```

### Native Query JSONB Cast

Failure:

```text
syntax error at or near ":"
n.encrypted_properties <> '{}':jsonb
```

Root cause:

Spring Data native-query parameter parsing can consume one colon from PostgreSQL `::jsonb`, leaving invalid SQL as `:jsonb`.

Fix:

Use portable explicit casts:

```sql
CAST('{}' AS jsonb)
```

Affected file:

```text
ecm-core/src/main/java/com/ecm/core/repository/NodeRepository.java
```

### JSONB Text Formatting

Failure:

```text
expected compact JSON text but PostgreSQL JSONB emitted spaces and nondeterministic set display ordering
```

Root cause:

The test was asserting raw JSONB text formatting. PostgreSQL can render object and array text with spaces, while the semantic JSON value is unchanged.

Fix:

Normalize spaces in JSON text before set comparison.

Affected file:

```text
ecm-core/src/test/java/com/ecm/core/repository/NodeRepositoryJsonbBackfillSmokeTest.java
```

### Timestamp Precision

Failure:

```text
expected nanos but persisted timestamp was rounded to microseconds
```

Root cause:

The H2 schema uses `timestamp(6)`, so persisted `LocalDateTime` values round/truncate beyond microseconds.

Fix:

Generate repository-test timestamps with microsecond precision:

```java
LocalDateTime.now().truncatedTo(ChronoUnit.MICROS)
```

Affected file:

```text
ecm-core/src/test/java/com/ecm/core/repository/PropertyEncryptionBackfillJobRepositoryTest.java
```

## Frontend Failure

CI failure:

```text
PropertyEncryptionOperationsPage.test.tsx
runs dry-run, plan, run, and cancel actions for backfill and rewrap jobs
Exceeded timeout of 5000 ms
```

Root cause:

The test intentionally covers a long operator flow: backfill dry-run, plan, run, cancel, then rewrap dry-run, plan, run, cancel. It is fast in targeted mode but can exceed Jest's default 5 second timeout under the full CI Jest workload.

Fix:

Set an explicit 15 second timeout for that long-flow test only.

Affected file:

```text
ecm-frontend/src/pages/PropertyEncryptionOperationsPage.test.tsx
```

## Verification

### Follow-Up CI Run 25418484543

After the first fix commit, run `25418484543` still failed `Backend Verify` on a narrower PostgreSQL function issue:

```text
ERROR: function jsonb_object_length(jsonb) does not exist
```

Root cause:

The repository used `jsonb_object_length(...)` to count encrypted-property entries. The GitHub runner PostgreSQL image does not provide that function.

Follow-up fix:

```sql
SELECT COUNT(*)
FROM nodes n
CROSS JOIN LATERAL jsonb_each(n.encrypted_properties) AS payload(key, value)
WHERE n.is_deleted = false
  AND n.encrypted_properties IS NOT NULL
  AND n.encrypted_properties <> CAST('{}' AS jsonb)
```

This uses JSONB entry expansion instead of relying on an unavailable aggregate helper.

Backend repository and JSONB smoke target:

```bash
cd ecm-core
/tmp/apache-maven-3.9.9/bin/mvn -B -Dstyle.color=never \
  -Dmaven.repo.local=.m2-cache/repository \
  -Dspring.profiles.active=test \
  -Dtest=PropertyEncryptionBackfillJobRepositoryTest,NodeRepositoryJsonbBackfillSmokeTest \
  test
```

Result:

```text
PropertyEncryptionBackfillJobRepositoryTest: Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
NodeRepositoryJsonbBackfillSmokeTest: Tests run: 1, Failures: 0, Errors: 0, Skipped: 1
BUILD SUCCESS
```

Backend operations and PostgreSQL integration compile/skip target:

```bash
cd ecm-core
/tmp/apache-maven-3.9.9/bin/mvn -B -Dstyle.color=never \
  -Dmaven.repo.local=.m2-cache/repository \
  -Dspring.profiles.active=test \
  -Dtest=PropertyEncryptionBackfillPostgresIntegrationTest,PropertyEncryptionOperationsServiceTest \
  test
```

Result:

```text
PropertyEncryptionOperationsServiceTest: Tests run: 38, Failures: 0, Errors: 0, Skipped: 0
PropertyEncryptionBackfillPostgresIntegrationTest: Tests run: 2, Failures: 0, Errors: 0, Skipped: 2
BUILD SUCCESS
```

Backend PostgreSQL function follow-up compile/skip target:

```bash
cd ecm-core
/tmp/apache-maven-3.9.9/bin/mvn -B -Dstyle.color=never \
  -Dmaven.repo.local=.m2-cache/repository \
  -Dspring.profiles.active=test \
  -Dtest=PropertyEncryptionBackfillPostgresIntegrationTest,NodeRepositoryJsonbBackfillSmokeTest \
  test
```

Result:

```text
NodeRepositoryJsonbBackfillSmokeTest: Tests run: 1, Failures: 0, Errors: 0, Skipped: 1
PropertyEncryptionBackfillPostgresIntegrationTest: Tests run: 2, Failures: 0, Errors: 0, Skipped: 2
BUILD SUCCESS
```

Frontend targeted page test:

```bash
cd ecm-frontend
CI=true npm test -- --runTestsByPath src/pages/PropertyEncryptionOperationsPage.test.tsx --watchAll=false
```

Result:

```text
Test Suites: 1 passed, 1 total
Tests: 2 passed, 2 total
```

Whitespace:

```bash
git diff --check
```

Result: passed with no output.

## Remaining Work

- Push the fix commit.
- Observe the next CI run.
- The expected next milestone is that `Backend Verify` and `Frontend Build & Test` pass, allowing `Property Encryption Closeout Gate` to run instead of being skipped.
- If `Property Encryption Closeout Gate` then fails, fix the concrete Docker-backed PostgreSQL/Testcontainers failure from that job log.
