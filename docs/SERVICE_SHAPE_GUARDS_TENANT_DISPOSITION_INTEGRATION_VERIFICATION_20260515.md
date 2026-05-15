# Tenant and Disposition Service Shape Guards: Integration Verification

Date: 2026-05-15

## Scope

This round continued the frontend service response-shape guard closeout
line with two bounded slices:

- `tenantService`: implemented directly in the main worktree by Codex.
- `dispositionScheduleService`: implemented in an isolated Claude
  worktree, reviewed by Codex, verified locally, then cherry-picked into
  main.

Both slices are frontend-only. No backend code, endpoint path, request
payload, authorization rule, or public service method was intentionally
changed.

## Parallel Split

The split was deliberately narrow:

- Codex owned `ecm-frontend/src/services/tenantService.ts`,
  `tenantService.test.ts`, and the tenant design/verification doc.
- Claude owned `ecm-frontend/src/services/dispositionScheduleService.ts`,
  `dispositionScheduleService.test.ts`, and the disposition
  design/verification doc in a separate worktree.
- Codex owned final review, local verification, cherry-pick,
  documentation correction, and main-branch integration.

This split worked because the two services have no shared source files,
no shared tests, and no database or backend migration dependency.

## Backend Contract Checks

Tenant service checks were derived from:

- `TenantAdminController`
- `TenantService.TenantDto`
- `TenantMetricsService.TenantMetrics`

Disposition service checks were derived from:

- `DispositionScheduleController`
- `DispositionScheduleService` DTO records
- `Node.ArchiveStoreTier`
- `DispositionActionExecution.ActionType`
- `DispositionActionExecution.ExecutionStatus`

The four recurring cross-package pre-flight checks were applied before
integration:

- Endpoint paths were verified from controllers rather than copied from
  assumptions.
- HTML fallback strings are rejected by exported service-level error
  messages.
- Nullable fields are explicit in the frontend DTO contracts and guards.
- HTTP success is not treated as semantic success unless the response
  body matches the expected DTO shape.

## Commits

- `a21db36 fix(tenant): guard service responses`
- `6d461fe fix(disposition): guard service responses`
  - Claude worktree commit.
- `e696fce fix(disposition): guard service responses`
  - Main cherry-pick of the Claude worktree commit.

## Local Verification

### Combined Targeted Jest

Command:

```bash
cd ecm-frontend
npm test -- --runTestsByPath src/services/tenantService.test.ts src/services/dispositionScheduleService.test.ts --watchAll=false
```

Result:

```text
PASS src/services/tenantService.test.ts
PASS src/services/dispositionScheduleService.test.ts

Test Suites: 2 passed, 2 total
Tests:       40 passed, 40 total
Snapshots:   0 total
```

### Frontend Lint

Command:

```bash
cd ecm-frontend
npm run lint
```

Result:

```text
eslint src --ext .ts,.tsx
```

Exit status: 0.

### Frontend Production Build

Command:

```bash
cd ecm-frontend
CI=true npm run build
```

Result:

```text
Creating an optimized production build...
Compiled successfully.
```

Observed warnings:

- Existing CRA bundle-size advisory.
- Node deprecation warning: `fs.F_OK is deprecated, use fs.constants.F_OK instead`.

Neither warning failed the build.

### Diff Hygiene

Command:

```bash
git diff --check
```

Result: PASS.

## Dirty Worktree Notes

`.env` was already modified before this round and was not touched,
staged, or committed.

The Claude worktree temporarily reused the main worktree's
`ecm-frontend/node_modules` through a symlink for verification. The
symlink and generated build directory were removed before staging in the
Claude worktree.

## Residual Work

Remaining service guard candidates still need separate slices. Recommended
next groups:

- Small admin/services: `opsPolicyService`, `opsRecoveryService`,
  `previewDiagnosticsService`.
- Medium services: `peopleService`, `propertyEncryptionService`,
  `ruleService`.
- Larger services that should stay isolated: `recordsManagementService`,
  `nodeService`, `workflowService`, `contentArchiveService`,
  `contentModelService`, `bulkOperationService`, `authService`.

For future Claude collaboration, keep the same model: one service per
Claude worktree, explicit backend contract evidence, targeted tests,
local verification, then Codex review and main-branch integration.

## Remote CI

Run: `25904124986`

Head: `5e466f9de3418cb3904a0653a4c68d8a62f97b41`

Result: PASS.

Jobs:

- Backend Verify: success.
- Frontend Build & Test: success.
- Phase C Security Verification: success.
- Property Encryption Closeout Gate: success.
- Frontend E2E Core Gate: success.
- Acceptance Smoke (3 admin pages): success.
- Phase 5 Mocked Regression Gate: success.
