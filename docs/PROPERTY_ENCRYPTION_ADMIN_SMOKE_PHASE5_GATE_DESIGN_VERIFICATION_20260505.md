# Property Encryption Admin Smoke + Phase 5 Gate Design Verification

Date: 2026-05-05

## Context

`/admin/property-encryption` already had a React admin operations page, route, menu entry, service client, unit tests, lint, and production build verification.

The remaining frontend delivery gap was a mocked browser smoke that proves the admin route can mount under the same Keycloak-bypass and mocked-API conditions used by the Phase 5 mocked regression gate.

## Design

### New Playwright Smoke

Added `ecm-frontend/e2e/admin-property-encryption.mock.spec.ts`.

The spec uses the existing mocked admin pattern:

- `mockKeycloakUnreachable(page)`
- `seedBypassSessionE2E(page, 'admin', 'e2e-token')`
- route all `**/api/v1/**` calls before navigation
- fulfill notification unread-count because `MainLayout` requests it on mount
- fulfill the property-encryption status, definitions, backfill job list, dry-run, plan, run, and cancel endpoints

The assertions cover:

- page heading `Property Encryption`
- crypto status chip `Secret crypto enabled`
- encrypted definition row `cm:secretCode`
- initial job status `PLANNED`
- backfill dry-run result
- rewrap dry-run result
- plan action
- run action producing `RUNNING`
- cancel action producing `CANCEL_REQUESTED`

The job-status assertions are scoped to the jobs table to avoid strict-mode collisions with toast text.

### Phase 5 Mocked Gate Registration

Updated `scripts/phase5-regression.sh` to include:

```text
e2e/admin-property-encryption.mock.spec.ts
```

This makes the property-encryption admin page part of the mocked-first gate that runs without Docker or a backend.

## Verification

### Targeted Playwright

Command:

```bash
cd ecm-frontend
ECM_UI_URL=http://127.0.0.1:5500 \
  npx playwright test e2e/admin-property-encryption.mock.spec.ts \
  --project=chromium \
  --workers=1
```

Result:

```text
1 passed
```

Environment note: the command used a temporary local SPA server serving `ecm-frontend/build` on `127.0.0.1:5500`.

### Phase 5 Registry Preflight

Command:

```bash
PHASE5_VALIDATE_RECOVERY_REGISTRY_ONLY=1 bash scripts/phase5-regression.sh
```

Result:

```text
expected events: 24
observed markers in specs: 24
missing_from_events_file_count: 0
stale_events_file_entries_count: 0
OK registry matches spec markers
registry-only mode complete
```

### Script Syntax

Command:

```bash
bash -n scripts/phase5-regression.sh
```

Result: passed.

### Diff Check

Command:

```bash
git diff --check
```

Result: passed.

### ASCII Check

Command:

```bash
LC_ALL=C grep -RIn '[^ -~]' ecm-frontend/e2e/admin-property-encryption.mock.spec.ts || true
```

Result: no non-ASCII in the new spec.

## Remaining Development Estimate

### Backfill/Admin UI Delivery Closure

Remaining work: about `0.5-1.5 person-days`.

Scope:

- run `scripts/property-encryption-backfill-gate.sh` on a Docker-capable runner
- wire that gate into CI if the team wants an explicit property-encryption backend gate
- update closeout docs with the first Docker-backed green run

Risk buffer: add `1-2 person-days` if the Docker-backed PostgreSQL integration test exposes a real migration/query failure.

### Full Property-Encryption Benchmark Delivery

Remaining work: about `6-10 person-days`.

Major slices:

1. Rewrap execution backend: `3-5 person-days`
   - `PropertyEncryptionRewrapJob` ledger
   - plan/list/get/run/cancel endpoints
   - JSONB candidate query and CAS update
   - decrypt old payload, encrypt with active target key, terminal counters, failure handling
   - PostgreSQL/Testcontainers coverage

2. Rewrap execution UI: `1-2 person-days`
   - expose plan/run/cancel after backend support exists
   - show rewrap ledger and failed/missing-key states
   - add unit and mocked browser coverage

3. Runtime masking/redaction policy polish: `1-2 person-days`
   - clarify how encrypted properties render in generic property editors
   - document readable-vs-indexable behavior
   - add UI/acceptance checks if masking is product-required

4. CI and acceptance closeout: `0.5-1 person-day`
   - run backend Docker gate
   - run Phase 5 mocked gate with the new spec
   - update final acceptance matrix

## Recommendation

For the next slice, prefer backend API-first rewrap job ledger before adding more UI. The frontend now accurately shows rewrap as dry-run only; exposing execution before backend plan/run/cancel exists would create a product promise the API cannot honor.

