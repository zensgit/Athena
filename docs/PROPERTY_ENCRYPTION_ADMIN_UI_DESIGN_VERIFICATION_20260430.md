# Property Encryption Admin UI Design + Verification

Date: 2026-04-30

## Context

The backend property-encryption operations surface already exposes admin-only endpoints for:

- status
- encrypted property definitions
- rewrap dry-run
- backfill dry-run
- backfill job plan/list/run/cancel

The missing product slice was a frontend admin entry point. This change adds the first admin operations page without introducing rewrap execution UI, because the backend currently supports rewrap dry-run only.

## Design

### Service Client

Added `ecm-frontend/src/services/propertyEncryptionService.ts`.

The service wraps:

- `GET /admin/property-encryption/status`
- `GET /admin/property-encryption/definitions`
- `POST /admin/property-encryption/rewrap-jobs/dry-run`
- `POST /admin/property-encryption/backfill-jobs/dry-run`
- `POST /admin/property-encryption/backfill-jobs/plan`
- `GET /admin/property-encryption/backfill-jobs`
- `POST /admin/property-encryption/backfill-jobs/{jobId}/run`
- `POST /admin/property-encryption/backfill-jobs/{jobId}/cancel`

Payloads trim optional target key versions before sending them to the backend.

### Admin Page

Added `ecm-frontend/src/pages/PropertyEncryptionOperationsPage.tsx`.

The page includes:

- crypto status card with active key, configured key count, warning display, definition count, node count, and encrypted value count
- target key input
- backfill dry-run action
- backfill job planning action
- rewrap dry-run action with explicit copy that execution is not yet exposed
- encrypted property definition table
- backfill job ledger with status chips, counters, run action, and cancel action

The page deliberately does not expose rewrap execution. That prevents a UI promise ahead of backend support and keeps the operation model consistent with the current API.

### Routing And Navigation

Updated:

- `ecm-frontend/src/App.tsx`
- `ecm-frontend/src/components/layout/MainLayout.tsx`

The new route is `/admin/property-encryption` and is guarded by `PrivateRoute requiredRoles={['ROLE_ADMIN']}`.

The admin account menu now includes `Property Encryption`.

## Verification

### Targeted Jest

Command:

```bash
cd ecm-frontend
npm test -- --watchAll=false \
  src/services/propertyEncryptionService.test.ts \
  src/pages/PropertyEncryptionOperationsPage.test.tsx \
  src/components/layout/MainLayout.menu.test.tsx
```

Result:

```text
Test Suites: 3 passed, 3 total
Tests:       8 passed, 8 total
```

### ESLint

Command:

```bash
cd ecm-frontend
npm run lint
```

Result: passed.

### Production Build

Command:

```bash
cd ecm-frontend
CI=true npm run build
```

Result: passed.

### Diff Check

Command:

```bash
git diff --check
```

Result: passed.

### TypeScript Note

Command:

```bash
cd ecm-frontend
npx tsc --noEmit
```

Result: failed before project source checking on `node_modules/react-hook-form/dist/watch.d.ts`.

Observed error class:

```text
node_modules/react-hook-form/dist/watch.d.ts: Type parameter declaration expected
```

The CRA production build succeeds, so the practical frontend build gate is green. The raw `tsc --noEmit` command is currently not a reliable repo gate with this dependency set.

## Remaining Work

Recommended next slices:

1. Add a Playwright mocked admin smoke for `/admin/property-encryption`.
2. Implement API-first rewrap job ledger in the backend.
3. Add rewrap execution UI only after backend plan/run/cancel exists.

