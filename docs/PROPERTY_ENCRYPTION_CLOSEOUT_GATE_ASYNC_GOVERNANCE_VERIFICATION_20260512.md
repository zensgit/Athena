# Property Encryption Closeout Gate Async Governance Verification

Date: 2026-05-12

## Context

The Property Encryption async-governance slice added a new shared task-center
domain for backfill and rewrap jobs. The implementation and direct tests were
covered in:

- `docs/PROPERTY_ENCRYPTION_ASYNC_GOVERNANCE_DESIGN_VERIFICATION_20260512.md`

The remaining closeout gap was the CI-facing property-encryption preflight
script. Before this slice, `scripts/property-encryption-closeout-preflight.sh`
still targeted the older property-encryption admin/service/redaction tests and
did not run the new async-governance service, lifecycle, analytics-controller,
or analytics-security contracts by default.

## Design

Updated `BACKEND_NON_DOCKER_TESTS` in
`scripts/property-encryption-closeout-preflight.sh` to include:

- `PropertyEncryptionAsyncTaskServiceTest`
- `AsyncTaskGovernanceServiceTest`
- `AsyncTaskLifecycleServiceTest`
- `AnalyticsControllerTest`
- `AnalyticsControllerSecurityTest`

This keeps the gate non-Docker by default while covering the new contract
surface:

- provider aggregation for the `propertyEncryption` domain;
- lifecycle domain alias handling for `property-encryption`;
- analytics task-list JSON contract, including normalized domain and cancel URL;
- existing analytics async-governance security boundaries.

No workflow topology was changed. The GitHub Actions
`Property Encryption Closeout Gate` job still invokes the same preflight script,
and the Docker-backed PostgreSQL/Testcontainers gate remains controlled by the
existing `RUN_DOCKER_BACKED_GATE` and `REQUIRE_DOCKER_BACKED_GATE` variables.

Frontend mocked E2E execution remains covered by the Phase 5 mocked regression
lane. The property-encryption closeout preflight continues to run the Phase 5
registry-only check so the mocked specs stay registered without forcing a
browser install into this property-specific gate.

## Verification

### Property Encryption Closeout Preflight

Command:

```bash
MAVEN_BIN=/tmp/codex-maven/apache-maven-3.9.11/bin/mvn \
RUN_FRONTEND_BUILD=0 \
RUN_DOCKER_BACKED_GATE=0 \
scripts/property-encryption-closeout-preflight.sh
```

Result:

```text
property_encryption_closeout_preflight: backend non-Docker evidence
Tests run: 140, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS

property_encryption_closeout_preflight: frontend targeted evidence
Test Suites: 3 passed, 3 total
Tests:       10 passed, 10 total

property_encryption_closeout_preflight: frontend lint
passed

property_encryption_closeout_preflight: Phase 5 registry-only preflight
expected events: 24
observed markers in specs: 24
OK registry matches spec markers

property_encryption_closeout_preflight: Docker-backed gate skipped by RUN_DOCKER_BACKED_GATE=0
property_encryption_closeout_preflight: ok
```

The command intentionally disabled the production build and Docker-backed gate
for this local script-verification pass:

- production build was already verified in the async-governance slice;
- Docker-backed verification remains available through the unchanged
  `RUN_DOCKER_BACKED_GATE=1` path and is enforced in CI by
  `REQUIRE_DOCKER_BACKED_GATE=1`.

### Previously Preserved Evidence

The underlying async-governance implementation was also verified with:

```text
Backend targeted async-governance suite: 65 passed
Frontend PropertyEncryptionOperationsPage Jest: 2 passed
Frontend lint: passed
Frontend production build: compiled successfully
Playwright mocked specs: 2 passed
```

Those results are recorded in
`docs/PROPERTY_ENCRYPTION_ASYNC_GOVERNANCE_DESIGN_VERIFICATION_20260512.md`.

### Static Check

Command:

```bash
git diff --check -- . ':!.env'
```

Result: passed.

## Files Changed

- `scripts/property-encryption-closeout-preflight.sh`
- `docs/PROPERTY_ENCRYPTION_CLOSEOUT_GATE_ASYNC_GOVERNANCE_VERIFICATION_20260512.md`

## Remaining Work

- No further code change is required for the async-governance closeout gate.
- The full CI `Property Encryption Closeout Gate` will still run the
  Docker-backed PostgreSQL/Testcontainers path because the workflow sets
  `REQUIRE_DOCKER_BACKED_GATE=1`.
- `.env` has pre-existing local changes and remains intentionally excluded from
  this slice.
