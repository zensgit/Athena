# Property Encryption Final Acceptance Matrix

Date: 2026-05-05

## Scope

This document closes the Property Encryption implementation thread up to the final Docker-backed CI evidence boundary.

Covered capability:

- encrypted model-property definitions
- admin backfill status, dry-run, plan, run, cancel, and stale-job recovery
- planned rewrap ledger
- rewrap execution run/cancel workflow
- admin UI for status, backfill, and rewrap execution
- runtime protected-payload redaction
- public response masking for model-declared encrypted keys
- local closeout preflight
- CI closeout gate wiring

Out of scope:

- replacing key-management infrastructure
- printing plaintext protected values in docs or CI logs
- treating local Docker socket absence as a product regression

## Commit And Push Evidence

Latest code-gate commit:

```text
51a337412c5661b1c57d11b273c5872bb6f64d6f
fix(core): restore application task executor
```

Push result:

```text
origin/main: f26ec3e -> 51a3374
```

Local working-tree note:

```text
.env remains locally modified and intentionally uncommitted.
```

## CI Run

Initial run triggered by the closeout-gate push:

```text
Workflow: CI
Run ID: 25418055312
URL: https://github.com/zensgit/Athena/actions/runs/25418055312
Trigger: push to main
Head SHA: f5de379399ae9db8b47301eb4bd50378e49cce37
Observed at: 2026-05-06T05:24:44Z
Status at observation: in_progress
```

Final result for run `25418055312`:

```text
Workflow conclusion: failure
Backend Verify: failure
Frontend Build & Test: failure
Property Encryption Closeout Gate: skipped
```

The closeout job did not execute because both prerequisite fast gates failed.

Fix document:

```text
docs/PROPERTY_ENCRYPTION_CI_RUN_25418055312_FIXES_DESIGN_VERIFICATION_20260505.md
```

Follow-up run `25418484543`:

```text
Backend Verify: failure
Root cause: PostgreSQL runner does not provide jsonb_object_length(jsonb)
Fix: count JSONB entries with CROSS JOIN LATERAL jsonb_each(...)
```

Closeout run `25418606323`:

```text
Backend Verify: success
Frontend Build & Test: success
Property Encryption Closeout Gate: success
Property Encryption Closeout Gate job ID: 74556589054
Property Encryption Closeout Gate completed at: 2026-05-06T06:01:17Z
Phase C Security Verification: failure
```

Closeout preflight evidence from job `74556589054`:

```text
Backend non-Docker evidence: 75 tests, 0 failures, 0 errors, 0 skipped
Frontend targeted evidence: 3 suites passed, 10 tests passed
Frontend lint: success
Frontend production build: compiled successfully
Phase 5 registry-only preflight: expected events 24, observed markers 24
Docker-backed PostgreSQL gate: 65 tests, 0 failures, 0 errors, 0 skipped
Docker-backed gate passed
property_encryption_closeout_preflight: ok
```

Final closeout run `25419356309`:

```text
Workflow conclusion: success
Head SHA: 51a337412c5661b1c57d11b273c5872bb6f64d6f
URL: https://github.com/zensgit/Athena/actions/runs/25419356309
Created at: 2026-05-06T06:07:38Z
Completed at: 2026-05-06T06:34:17Z
```

Final job matrix:

| Job | Result | Job ID |
| --- | --- | --- |
| Backend Verify | success | 74557701175 |
| Frontend Build & Test | success | 74557701078 |
| Phase C Security Verification | success | 74557929421 |
| Property Encryption Closeout Gate | success | 74559015398 |
| Acceptance Smoke (3 admin pages) | success | 74559015407 |
| Frontend E2E Core Gate | success | 74559015414 |
| Phase 5 Mocked Regression Gate | success | 74559015423 |

Final closeout preflight evidence from job `74559015398`:

```text
Backend non-Docker evidence: 75 tests, 0 failures, 0 errors, 0 skipped
Frontend targeted evidence: 3 suites passed, 10 tests passed
Phase 5 registry-only preflight: expected events 24, observed markers 24
Docker-backed PostgreSQL gate: 66 tests, 0 failures, 0 errors, 0 skipped
PropertyEncryptionBackfillPostgresIntegrationTest: 2 tests, 0 failures, 0 errors, 0 skipped
NodeRepositoryJsonbBackfillSmokeTest: 1 test, 0 failures, 0 errors, 0 skipped
PropertyEncryptionBackfillJobRepositoryTest: 3 tests, 0 failures, 0 errors, 0 skipped
PropertyEncryptionAsyncConfigurationTest: 2 tests, 0 failures, 0 errors, 0 skipped
property_encryption_closeout_preflight: Docker-backed gate passed
property_encryption_closeout_preflight: ok
```

Observed jobs at first poll:

| Job | Status | Current step |
| --- | --- | --- |
| Backend Verify | in_progress | Compile |
| Frontend Build & Test | in_progress | Run RM notification closeout preflight |
| Property Encryption Closeout Gate | pending | waits for backend + frontend |
| Acceptance Smoke | pending | waits for backend + frontend |
| Frontend E2E Core Gate | pending | waits for backend + frontend |
| Phase C Security Verification | pending | waits for backend |
| Phase 5 Mocked Regression Gate | pending | waits for frontend |

## Acceptance Matrix

| Gate | Local evidence | CI evidence required | Acceptance rule |
| --- | --- | --- | --- |
| Script syntax | `bash -n` passed for both property-encryption scripts | same via closeout preflight | no syntax failure |
| Workflow parse | `.github/workflows/ci.yml` parsed with Ruby YAML | GitHub Actions accepts workflow | run starts successfully |
| Backend non-Docker suite | reduced preflight: `PropertyEncryptionOperationsServiceTest` 38/38 passed; prior full preflight: 75/75 passed | closeout preflight backend suite passes | 0 failures, 0 errors |
| Frontend targeted suite | reduced preflight: service test 3/3 passed; prior full preflight: 10/10 passed | closeout preflight frontend suite passes | 0 failed tests |
| Frontend lint/build | reduced lint passed; prior full preflight build compiled successfully | closeout preflight lint/build pass | no lint/build failure |
| Phase 5 registry | prior full preflight matched 24/24 markers | closeout preflight registry step passes | no missing/stale registry entries |
| Docker-backed PostgreSQL/Testcontainers | local host blocked by missing Docker socket | runs `25418606323` and `25419356309` passed; final job `74559015398` passed | Docker reachable and backfill gate passes |
| Full CI baseline | runs `25418055312` and `25418484543` exposed backend + frontend CI gaps; run `25418606323` exposed the Flowable startup blocker | run `25419356309` passed all jobs | full workflow green |

## Phase C Follow-Up

Run `25418606323` failed `Phase C Security Verification` before security verification executed.

Failure signature:

```text
No qualifying bean of type 'org.springframework.core.task.AsyncTaskExecutor' available
Dependency annotations: {@org.springframework.beans.factory.annotation.Qualifier("applicationTaskExecutor")}
```

Fix document:

```text
docs/PHASE_C_FLOWABLE_APPLICATION_TASK_EXECUTOR_FIX_DESIGN_VERIFICATION_20260505.md
```

Final result on run `25419356309`:

```text
Phase C Security Verification: success
Start verification stack: success
Run Phase C verification: success
```

## Docker-Backed Closeout Requirement

Final benchmark closeout required this command to pass on a Docker-capable runner:

```bash
REQUIRE_DOCKER_BACKED_GATE=1 scripts/property-encryption-closeout-preflight.sh
```

GitHub Actions runs that command in:

```text
Property Encryption Closeout Gate
```

The final run `25419356309` passed this gate. The gate validates:

- PostgreSQL JSONB backfill candidate selection
- backfill migration into encrypted storage
- rewrap migration from old key-version payloads to active key-version payloads
- persisted job status and counters
- compare-and-set behavior for safe mutation

## Failure Triage

If `Property Encryption Closeout Gate` fails, classify the failure as one of:

| Failure class | Likely owner | First action |
| --- | --- | --- |
| Maven/toolchain setup | CI wiring | inspect setup-java and Maven resolution logs |
| Docker unavailable | runner environment | inspect `docker ps` output and runner service permissions |
| Testcontainers startup | backend test infra | inspect container startup logs and image pull failure |
| PostgreSQL JSONB query failure | backend repository/service | reproduce with `PropertyEncryptionBackfillPostgresIntegrationTest` |
| Backfill mutation failure | backend service | inspect counters and persisted job terminal state |
| Rewrap mutation failure | backend service | inspect key-version and compare-and-set paths |
| Frontend targeted failure | frontend service/UI | run the same `--runTestsByPath` command locally |
| Phase 5 registry failure | mocked E2E registry | update markers/spec registry together |

## Remaining Work

Property Encryption closeout is complete at the CI gate level.

Keep `.env` out of commits.
