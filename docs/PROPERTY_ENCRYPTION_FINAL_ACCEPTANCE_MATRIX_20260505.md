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
f5de379399ae9db8b47301eb4bd50378e49cce37
ci(security): add property encryption closeout gate
```

Push result:

```text
origin/main: 7d7639c -> f5de379
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
| Docker-backed PostgreSQL/Testcontainers | local host blocked by missing Docker socket | `Property Encryption Closeout Gate` runs with `REQUIRE_DOCKER_BACKED_GATE=1` | Docker reachable and backfill gate passes |
| Full CI baseline | run pending at first poll | CI workflow completes | required jobs green |

## Docker-Backed Closeout Requirement

Final benchmark closeout is not complete until this command passes on a Docker-capable runner:

```bash
REQUIRE_DOCKER_BACKED_GATE=1 scripts/property-encryption-closeout-preflight.sh
```

GitHub Actions now runs that command in:

```text
Property Encryption Closeout Gate
```

The gate must validate:

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

1. Poll run `25418055312` until completion.
2. If `Property Encryption Closeout Gate` is green, update this document with the completed job result and mark benchmark closeout ready.
3. If the gate is red, fix the concrete failure and rerun the same gate.
4. Keep `.env` out of commits.
