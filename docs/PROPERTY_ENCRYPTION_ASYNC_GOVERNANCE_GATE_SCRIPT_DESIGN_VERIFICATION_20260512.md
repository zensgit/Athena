# Property Encryption Async Governance Gate Script Design Verification

Date: 2026-05-12

## Context

The Property Encryption async-governance addendum had complete targeted
verification, but the commands were spread across several slice documents:

- backend async-governance contract tests;
- frontend Property Encryption page Jest coverage;
- frontend lint and production build;
- Phase 5 registry-only validation;
- mocked Playwright specs for the property-encryption page, Admin Dashboard
  async-governance task filtering/cancel action, and overview fallback behavior.

This slice turns that evidence chain into one reusable local gate.

## Design

Added:

- `scripts/property-encryption-async-governance-gate.sh`

Default behavior:

1. Resolves Maven from `MAVEN_BIN`, `/tmp/codex-maven/apache-maven-3.9.11/bin/mvn`,
   `/tmp/apache-maven-3.9.9/bin/mvn`, or `PATH`.
2. Runs script syntax checks for itself, the property-encryption closeout
   preflight, and Phase 5 regression.
3. Runs `git diff --check -- . ':!.env'` so the known local `.env` change stays
   outside this gate.
4. Runs backend async-governance contract tests:
   - `PropertyEncryptionAsyncTaskServiceTest`
   - `AsyncTaskGovernanceServiceTest`
   - `AsyncTaskLifecycleServiceTest`
   - `AnalyticsControllerTest`
   - `AnalyticsControllerSecurityTest`
5. Runs frontend targeted Jest:
   - `src/pages/PropertyEncryptionOperationsPage.test.tsx`
6. Runs frontend lint.
7. Runs `CI=true npm run build` unless `RUN_FRONTEND_BUILD=0`.
8. Runs Phase 5 registry-only validation unless `RUN_PHASE5_REGISTRY=0`.
9. Starts an ephemeral static `serve -s build -l 0` server and runs the three
   related mocked E2E specs unless `RUN_E2E=0`.

Supported overrides:

- `MAVEN_BIN`
- `BACKEND_TESTS`
- `FRONTEND_TEST_PATHS`
- `E2E_SPECS`
- `PW_PROJECT`
- `RUN_FRONTEND_BUILD`
- `RUN_PHASE5_REGISTRY`
- `RUN_E2E`
- `USE_EXISTING_UI=1 ECM_UI_URL=http://...`

The script is intentionally local/addendum scoped. It does not replace the
Docker-backed `Property Encryption Closeout Gate`; it packages the new
async-governance evidence so developers can reproduce it before pushing.

## Verification

### Lightweight Path

Command:

```bash
MAVEN_BIN=/tmp/codex-maven/apache-maven-3.9.11/bin/mvn \
RUN_FRONTEND_BUILD=0 \
RUN_E2E=0 \
scripts/property-encryption-async-governance-gate.sh
```

Result:

```text
Backend async-governance contract tests: 65 tests, 0 failures, 0 errors
Frontend targeted Jest: 1 suite passed, 2 tests passed
Frontend lint: passed
Phase 5 registry-only preflight: expected events 24, observed markers 24
property_encryption_async_governance_gate: ok
```

### Full Path

Command:

```bash
MAVEN_BIN=/tmp/codex-maven/apache-maven-3.9.11/bin/mvn \
scripts/property-encryption-async-governance-gate.sh
```

Result:

```text
Backend async-governance contract tests: 65 tests, 0 failures, 0 errors
Frontend targeted Jest: 1 suite passed, 2 tests passed
Frontend lint: passed
Frontend production build: compiled successfully
Phase 5 registry-only preflight: expected events 24, observed markers 24
Mocked E2E: 3 passed
property_encryption_async_governance_gate: ok
```

The full run started an ephemeral static UI server:

```text
property_encryption_async_governance_gate: mocked E2E against http://localhost:60376
```

Covered mocked E2E specs:

- `e2e/admin-async-governance-overview-fallback.mock.spec.ts`
- `e2e/admin-audit-filter-export.mock.spec.ts`
- `e2e/admin-property-encryption.mock.spec.ts`

Build notes:

- The production build still prints the existing CRA bundle-size advisory.

### Static Check

The gate itself ran:

```bash
git diff --check -- . ':!.env'
```

Result: passed.

## Files Changed

- `scripts/property-encryption-async-governance-gate.sh`
- `docs/PROPERTY_ENCRYPTION_ASYNC_GOVERNANCE_GATE_SCRIPT_DESIGN_VERIFICATION_20260512.md`

## Remaining Work

- No additional local gate scripting is required for this addendum.
- The next pushed CI run should still be recorded in the final acceptance
  matrix addendum.
- `.env` has pre-existing local changes and remains intentionally excluded from
  this slice.
