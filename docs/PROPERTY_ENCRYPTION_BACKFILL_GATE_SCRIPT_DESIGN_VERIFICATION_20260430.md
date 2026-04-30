# Property Encryption Backfill Gate Script Design and Verification

Date: 2026-04-30
Code commit: `01aa370`

## Context

The remaining required evidence for property-encryption backfill is Docker/PostgreSQL-backed verification. Local Docker is still unavailable at:

```text
unix:///Users/chouhua/.docker/run/docker.sock
```

Instead of leaving the next verification pass as a hand-assembled command, this slice adds a reusable gate script that runs the exact targeted backend set when Docker is available and fails early with a clear message when Docker is unavailable.

## Script

Path:

```text
scripts/property-encryption-backfill-gate.sh
```

Default test set:

```text
NodeRepositoryJsonbBackfillSmokeTest,
PropertyEncryptionBackfillJobRepositoryTest,
PropertyEncryptionOperationsServiceTest,
PropertyEncryptionOperationsControllerSecurityTest,
PropertyEncryptionAsyncConfigurationTest,
PropertyEncryptionBackfillRunnerTest,
PropertyEncryptionBackfillRunnerAsyncProxyTest,
PropertyEncryptionBackfillRecoverySchedulerTest
```

The list can be overridden with `BACKEND_TESTS=...`.

## Behavior

The script:

1. Prints the selected test list.
2. Checks Docker reachability with `docker ps`.
3. Fails before Maven if Docker is unavailable.
4. Runs the targeted backend gate through `ecm-core/./mvnw` when Docker is available.

The explicit Docker precheck matters because both `ecm-core/mvnw` and `NodeRepositoryJsonbBackfillSmokeTest` require Docker in this environment.

## Verification

Static checks:

```bash
git diff --check -- scripts/property-encryption-backfill-gate.sh
perl -ne 'print "$ARGV:$.:$_" if /[^\x00-\x7F]/' scripts/property-encryption-backfill-gate.sh
```

Result: both passed with no output.

Local execution:

```bash
scripts/property-encryption-backfill-gate.sh
```

Result: failed early as expected because Docker is unavailable:

```text
property_encryption_backfill_gate: Docker API is not reachable; this gate requires Docker because ecm-core/mvnw and Testcontainers depend on it.
failed to connect to the docker API at unix:///Users/chouhua/.docker/run/docker.sock
```

## Remaining Work

1. Run `scripts/property-encryption-backfill-gate.sh` on a Docker-enabled machine or CI runner.
2. If green, promote backend recovery/backfill verification from compile-level to real PostgreSQL evidence.
3. Then start the property-encryption admin UI slice.
