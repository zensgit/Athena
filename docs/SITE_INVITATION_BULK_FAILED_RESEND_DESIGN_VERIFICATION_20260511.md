# Site Invitation Bulk Failed Resend - Design and Verification

Date: 2026-05-11

## Context

The site invitation resend layer already supported per-row resend and a
shareable `?sendStatus=failed` filter. The remaining operator friction was
handling several failed `PENDING` sends: the runbook still described "No
bulk-resend action", so operators had to open every row dialog one-by-one.

This frontend-only follow-up adds a compact bulk action without changing the
backend contract.

## Design

`SiteInvitationsPage` now shows `Resend failed (N)` next to the send-result
filter chips.

Eligibility is deliberately narrow:

| Row condition | Included in bulk action |
|---|---|
| `status=PENDING` and `lastSendStatus=FAILED` | yes |
| `status=PENDING` and `lastSendStatus=SENT` | no |
| `status=PENDING` and `lastSendStatus=null` | no |
| `status=ACCEPTED/CANCELLED/EXPIRED/REJECTED` and `lastSendStatus=FAILED` | no |

The button reuses the existing per-row frontend service method:

- No new backend endpoint.
- No new DTO fields.
- No new migration.
- No change to the per-row confirmation dialog.
- A browser confirmation protects the operator from accidentally resending
  multiple emails.
- The implementation calls `resendInvitation(siteId, invitationId)` for each
  eligible row and updates the table from the returned DTOs.
- HTTP success is still not treated as send success; returned DTO
  `lastSendStatus` drives the summary counts.

The UX reports one aggregate toast:

| Result mix | Toast severity |
|---|---|
| all returned `SENT` | success |
| any returned `FAILED` or rejected | error |
| any returned neither `SENT` nor `FAILED` and no failures | info |

## Verification

### Local gates

| Gate | Command | Result |
|---|---|---|
| Targeted Jest | `CI=true npm test -- --runTestsByPath src/pages/SiteInvitationsPage.test.tsx --watchAll=false` | 1 suite, 16 tests, 0 failures |
| ESLint | `npm run lint` | clean |
| Frontend build | `CI=true npm run build` | compiled successfully; CRA bundle-size advisory remains informational |
| Whitespace | `git diff --check` | clean |

New test coverage:

- Bulk resend calls the service only for failed `PENDING` invitations.
- Failed but already-accepted invitations are excluded.
- Returned DTOs replace the matching table rows.
- The bulk action is disabled when there are failed sends but none are pending.

Notes:

- Targeted Jest still prints the existing React Router v7 future-flag warnings.
- Negative resend-path tests intentionally exercise `console.error`; those
  console lines are expected and the suite passes.
- The production build still prints the existing Node `fs.F_OK` deprecation
  warning from the CRA toolchain.

### GitHub Actions

Push to `origin/main` at `e7034c4` triggered CI run `25659207664`.

| Job | Result | Duration |
|---|---|---:|
| Backend Verify | success | 2m17s |
| Frontend Build & Test | success | 10m11s |
| Phase C Security Verification | success | 5m23s |
| Frontend E2E Core Gate | success | 12m05s |
| Phase 5 Mocked Regression Gate | success | 6m10s |
| Acceptance Smoke (3 admin pages) | success | 6m38s |
| Property Encryption Closeout Gate | success | 4m57s |

Run outcome: 7/7 jobs green.

## Files Changed

- `ecm-frontend/src/pages/SiteInvitationsPage.tsx`
- `ecm-frontend/src/pages/SiteInvitationsPage.test.tsx`
- `docs/SITE_INVITATION_RESEND_OPERATOR_RUNBOOK_20260507.md`
- `docs/SITE_INVITATION_RESEND_INTEGRATION_VERIFICATION_20260510.md`

## Remaining Work

- Automatic retry / DLQ remains out of scope until the documented revisit
  thresholds are met.
- Per-recipient delivery tracking still requires provider-specific webhook
  integrations and was not changed by this frontend-only slice.
