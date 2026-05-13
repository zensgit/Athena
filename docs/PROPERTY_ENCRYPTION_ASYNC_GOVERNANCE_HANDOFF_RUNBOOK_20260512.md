# Property Encryption Async Governance Handoff Runbook

Date: 2026-05-12

## Context

The 2026-05-12 Property Encryption async-governance addendum is locally
implemented and verified. The addendum connects property-encryption backfill
and rewrap jobs to the shared async-governance dashboard and task list, then
packages the local verification into a reusable gate script.

Primary implementation docs:

- `docs/PROPERTY_ENCRYPTION_ASYNC_GOVERNANCE_DESIGN_VERIFICATION_20260512.md`
- `docs/PROPERTY_ENCRYPTION_CLOSEOUT_GATE_ASYNC_GOVERNANCE_VERIFICATION_20260512.md`
- `docs/PROPERTY_ENCRYPTION_ASYNC_GOVERNANCE_OVERVIEW_FALLBACK_E2E_VERIFICATION_20260512.md`
- `docs/PROPERTY_ENCRYPTION_ASYNC_GOVERNANCE_GATE_SCRIPT_DESIGN_VERIFICATION_20260512.md`
- `docs/PROPERTY_ENCRYPTION_ASYNC_GOVERNANCE_INTEGRATION_MANIFEST_20260512.md`

This runbook is the handoff entry point for the next executor.

## Local Gate

Run the full local addendum gate before push:

```bash
MAVEN_BIN=/tmp/codex-maven/apache-maven-3.9.11/bin/mvn \
scripts/property-encryption-async-governance-gate.sh
```

Expected result:

```text
Backend async-governance contract tests: 65 tests, 0 failures, 0 errors
Frontend targeted Jest: 1 suite passed, 2 tests passed
Frontend lint: passed
Frontend production build: compiled successfully
Phase 5 registry-only preflight: expected events 24, observed markers 24
Mocked E2E: 3 passed
property_encryption_async_governance_gate: ok
```

Use the lightweight path only for fast local iteration:

```bash
MAVEN_BIN=/tmp/codex-maven/apache-maven-3.9.11/bin/mvn \
RUN_FRONTEND_BUILD=0 \
RUN_E2E=0 \
scripts/property-encryption-async-governance-gate.sh
```

Expected lightweight result:

```text
Backend async-governance contract tests: 65 tests, 0 failures, 0 errors
Frontend targeted Jest: 1 suite passed, 2 tests passed
Frontend lint: passed
Phase 5 registry-only preflight: expected events 24, observed markers 24
property_encryption_async_governance_gate: ok
```

## CI Handoff

After push, watch these gates:

| Gate | Why it matters |
| --- | --- |
| Backend Verify | Confirms the async-governance backend contracts compile and run in the normal backend lane |
| Frontend Build & Test | Confirms Admin Dashboard and Property Encryption frontend changes compile in CI |
| Property Encryption Closeout Gate | Runs the widened property-encryption preflight plus Docker-backed PostgreSQL/Testcontainers gate |
| Phase 5 Mocked Regression Gate | Runs the newly registered fallback mocked E2E with the broader mocked suite |

If all four are green, append the CI run ID and job evidence to:

- `docs/PROPERTY_ENCRYPTION_FINAL_ACCEPTANCE_MATRIX_20260505.md`

Do not replace the historical 2026-05-05 CI run IDs. Add the new run under the
`2026-05-12 Async Governance Addendum` section.

## Failure Triage

| Failure | First check |
| --- | --- |
| Backend async-governance failure | Re-run `PropertyEncryptionAsyncTaskServiceTest,AsyncTaskGovernanceServiceTest,AsyncTaskLifecycleServiceTest,AnalyticsControllerTest,AnalyticsControllerSecurityTest` |
| Frontend Jest/lint/build failure | Re-run `scripts/property-encryption-async-governance-gate.sh` locally and inspect `AdminDashboard.tsx` / `PropertyEncryptionOperationsPage.tsx` |
| Mocked E2E failure | Re-run only the failed spec with `ECM_UI_URL` against a fresh static build |
| Phase 5 registry drift | Run `PHASE5_VALIDATE_RECOVERY_REGISTRY_ONLY=1 bash scripts/phase5-regression.sh` |
| Docker/Testcontainers failure | Treat as closeout-gate infrastructure or PostgreSQL JSONB behavior; inspect `PropertyEncryptionBackfillPostgresIntegrationTest` first |
| `.env` appears in diff | Do not stage it; it is a pre-existing local change |

## Verification

This runbook was verified by checking:

- `docs/PROPERTY_ENCRYPTION_CLOSEOUT_TODO_20260505.md` now recommends the one-command gate before push.
- `docs/PROPERTY_ENCRYPTION_FINAL_ACCEPTANCE_MATRIX_20260505.md` now records the one-command local gate as addendum evidence.
- `scripts/property-encryption-async-governance-gate.sh` is executable.
- `git diff --check -- . ':!.env'` passes.

## Remaining Work

- Push the addendum bundle.
- Record the next green CI evidence under the final acceptance matrix addendum.
- Keep `.env` out of commits.
