# Property Encryption Async Governance Overview Fallback E2E Verification

Date: 2026-05-12

## Context

The Property Encryption async-governance integration depends on the unified
`/api/v1/analytics/async-governance/overview` endpoint. The Admin Dashboard
still has a legacy fallback path that queries older per-domain summary
endpoints when the unified overview is unavailable.

Property Encryption intentionally has no legacy health-summary endpoint. That
means the fallback path must not silently render Property Encryption as healthy
with zero counts. It must show the domain as degraded with the explicit
`overview-required` reason, so operators know the unified overview endpoint is
required for this domain.

## Design

Added a mocked Playwright regression:

- `ecm-frontend/e2e/admin-async-governance-overview-fallback.mock.spec.ts`

The scenario:

1. Open `/admin` as an admin user.
2. Mock `/analytics/async-governance/overview` as HTTP 503.
3. Let the dashboard fall back to the five legacy summary endpoints that still
   exist for audit, ops recovery, search, preview, and batch download.
4. Assert no property-encryption legacy summary endpoint is requested.
5. Assert the Property Encryption health row renders:
   - `degraded`
   - `overview-required`
   - `CRITICAL`
   - zero counts
6. Assert the aggregate health chips show `Status DEGRADED` and
   `Risk CRITICAL`.

The new spec was also added to `scripts/phase5-regression.sh` so the Phase 5
mocked regression gate runs it with the other admin mocked E2E specs.

## Verification

### Targeted Mocked E2E

Command:

```bash
cd ecm-frontend
npx serve -s build -l 5511
ECM_UI_URL=http://localhost:5511 \
  npx playwright test \
  e2e/admin-async-governance-overview-fallback.mock.spec.ts \
  --project=chromium
```

Result:

```text
1 passed
```

The first run exposed a test assumption issue rather than a product failure:
React/effect refreshes can call the fallback summary endpoints more than once.
The assertion was corrected to require the five unique legacy domains instead
of exactly five total requests.

### Phase 5 Registry

Command:

```bash
PHASE5_VALIDATE_RECOVERY_REGISTRY_ONLY=1 bash scripts/phase5-regression.sh
```

Result:

```text
expected events: 24
observed markers in specs: 24
OK registry matches spec markers
```

### Script Syntax

Command:

```bash
bash -n scripts/phase5-regression.sh
```

Result: passed.

### Static Check

Command:

```bash
git diff --check -- . ':!.env'
```

Result: passed.

## Files Changed

- `ecm-frontend/e2e/admin-async-governance-overview-fallback.mock.spec.ts`
- `scripts/phase5-regression.sh`
- `docs/PROPERTY_ENCRYPTION_ASYNC_GOVERNANCE_OVERVIEW_FALLBACK_E2E_VERIFICATION_20260512.md`

## Remaining Work

- No additional fallback code change is required.
- The full Phase 5 mocked regression gate will now include this new spec on the
  next CI run.
- `.env` has pre-existing local changes and remains intentionally excluded from
  this slice.
