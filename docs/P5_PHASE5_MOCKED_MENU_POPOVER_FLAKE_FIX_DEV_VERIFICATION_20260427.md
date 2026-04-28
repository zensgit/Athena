# P5 Phase 5 Mocked Menu Popover Flake Fix

Date: 2026-04-27
Finalized: 2026-04-28

## Context

CI run `24970634791` reached 5 of 6 green jobs, then failed in `Phase 5 Mocked Regression Gate`.

Passing CI jobs in that run:

- `Backend Verify`
- `Frontend Build & Test`
- `Phase C Security Verification`
- `Acceptance Smoke (3 admin pages)`
- `Frontend E2E Core Gate`

Failing job:

- `Phase 5 Mocked Regression Gate`

The failure was isolated to:

- `ecm-frontend/e2e/mail-automation-processed-management.mock.spec.ts`
- Step: click the `Replay` button in the processed-mail table
- Symptom: Playwright timed out because a leftover MUI `Menu/Popover` from `MainLayout` intercepted pointer events.
- Evidence from CI log: `MuiPopover-root MuiMenu-root MuiModal-root` intercepted the click, including menu content such as `CMIS Explorer`.

This was not a product regression in Mail Automation. It was a test navigation flake caused by opening `Account menu`, clicking a menu item, then immediately interacting with page body content while the portal-backed menu could remain mounted in CI timing.

## Design

The affected mocked specs are not testing menu navigation. They are testing their target page workflows. The durable test design is therefore:

- Do not navigate through `Account menu` for page workflow specs.
- Use direct route navigation after seeding the authenticated E2E session.
- Keep menu navigation testing in dedicated layout/navigation specs only.

Changed direct routes:

- `admin-audit-filter-export.mock.spec.ts`: `/admin`
- `admin-preview-diagnostics.mock.spec.ts`: `/admin/preview-diagnostics`
- `mail-automation-diagnostics-export.mock.spec.ts`: `/admin/mail`
- `mail-automation-phase6-p1.mock.spec.ts`: `/admin/mail`
- `mail-automation-processed-management.mock.spec.ts`: `/admin/mail`
- `mail-automation-trigger-fetch.mock.spec.ts`: `/admin/mail`

Parallel review also checked Phase 5 mocked specs for remaining same-pattern risk. The only remaining `Account menu` usages in this gate are visibility assertions or recovery-shell assertions that do not open the menu and then click page body content.

## Verification

Passed before writing this report:

- `rg -n "getByRole\\('button', \\{ name: 'Account menu' \\}\\)\\.click\\(|getByRole\\('menuitem'" ...six modified specs...`

Result:

- No matches in the six modified specs.

Passed targeted/full local verification:

- `CI=false bash scripts/phase5-regression.sh`

Result:

- `30 passed`
- `phase5_regression: ok`

Additional observed improvements:

- The previously failing `mail-automation-processed-management.mock.spec.ts` completed locally in about 3 seconds after direct-route navigation.
- The full Phase 5 mocked gate completed locally in about 3.3 minutes.

## CI Notes

The earlier CI run `24970634791` failed before this fix existed. Expected next CI behavior after this patch:

- `Phase 5 Mocked Regression Gate` should no longer fail on stale `Account menu` popover interception.
- Other gates are not affected because this is E2E spec-only and does not change production code.

## Risk

Risk is low:

- Production code is unchanged.
- The specs still seed the same authenticated admin session.
- The specs still assert the same target page headings and workflows.
- The only behavior removed is testing menu navigation in workflow specs where menu navigation is not the subject under test.
