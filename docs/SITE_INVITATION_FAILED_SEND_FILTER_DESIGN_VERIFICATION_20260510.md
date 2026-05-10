# Site Invitation Failed-Send Filter - Design and Verification

Date: 2026-05-10

## Context

The resend layer now records email send outcomes on each site invitation row.
Operators can see `FAILED` rows in the table, but a busy site still requires
manual scanning. This small frontend-only follow-up adds a compact filter chip
so operators can triage failed invitation sends directly.

No backend, migration, or service contract changed.

## Design

`SiteInvitationsPage` now keeps a local `sendStatusFilter` state:

- `ALL` shows every invitation row.
- `FAILED` shows rows whose `lastSendStatus === 'FAILED'`.

The filter control is a small `Paper` section above the table:

- `All (<count>)` chip resets the table to the full list.
- `Failed sends (<count>)` chip narrows the table to failed sends.
- Counts are derived from the already-loaded `SiteInvitationDto[]`; no extra
  network request is made.

The empty state is filter-aware:

- no loaded rows -> `No invitations found. Use "Invite" to invite a new member.`
- loaded rows but zero failed sends under the failed filter ->
  `No failed-send invitations found.`

The filter is intentionally presentation-only. Resend/cancel operations still
update the canonical `invitations` array; `visibleInvitations` is derived on
render, so a row that changes from `FAILED` to `SENT` naturally disappears
from the failed-send view after a successful resend.

## Verification

### Local gates

| Gate | Command | Result |
|---|---|---|
| Targeted Jest | `CI=true npm test -- --runTestsByPath src/pages/SiteInvitationsPage.test.tsx --watchAll=false` | 1 suite, 13 tests, 0 failures |
| Frontend lint | `npm run lint` | clean |
| Frontend production build | `CI=true npm run build` | compiled successfully; CRA bundle-size advisory remains informational |

New test coverage:

- filters the table to failed sends and can return to all invitations.
- shows a filter-specific empty state when no rows have failed sends.

### GitHub Actions

Push to `origin/main` at `1444425` triggered CI run `25630086406`.

| Job | Result | Duration |
|---|---|---:|
| Backend Verify | success | 2m23s |
| Frontend Build & Test | success | 9m38s |
| Phase C Security Verification | success | 5m04s |
| Phase 5 Mocked Regression Gate | success | 6m21s |
| Property Encryption Closeout Gate | success | 4m50s |
| Acceptance Smoke (3 admin pages) | success | 6m54s |
| Frontend E2E Core Gate | success | 11m19s |

Run outcome: 7/7 jobs green.

The run still emits the existing GitHub Actions Node.js 20 deprecation warning
for `actions/checkout@v4` / `actions/setup-java@v4`. This is a workflow
maintenance warning, not a failed gate.

## Files Changed

- `ecm-frontend/src/pages/SiteInvitationsPage.tsx`
- `ecm-frontend/src/pages/SiteInvitationsPage.test.tsx`
- `docs/SITE_INVITATION_FAILED_SEND_FILTER_DESIGN_VERIFICATION_20260510.md`

## Remaining Work

- Optional: add a status query parameter if operators need shareable filtered
  URLs. Not needed for this small triage slice.
- Optional: add bulk resend only if failed-send counts become high enough to
  justify a separate backend capability.
