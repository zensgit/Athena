# Phase 1 P64 - Effective Permission Explanation UX (Verification) - 2026-02-09

## What This Verifies

Permission explanation UX is validated by:

- A Playwright E2E flow that creates a parent/child folder, sets an explicit parent permission for user `viewer`, and verifies the child inherits it.
- The Permissions dialog “Diagnose as viewer” showing:
  - `Reason ACL_ALLOW` / `ACL_DENY`
  - A “Matched grants” table including an `Inherited` marker.

## Primary Verification Record

See: `docs/VERIFICATION_PERMISSION_DIAGNOSTICS_MATCHED_GRANTS_20260209.md`

## Quick Re-Run (Playwright)

```bash
cd ecm-frontend
ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 \
  npx playwright test e2e/permission-explanation.spec.ts
```

Expected:
- `1 passed`

