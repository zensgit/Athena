# Site Invitation Resend - Integration and Verification

Date: 2026-05-10

## Context

This run integrated the three Claude local branches for the site-invitation
resend layer:

| Package | Branch | Tip integrated | Scope |
|---|---|---:|---|
| A | `claude/invitation-resend-backend` | `05c6716` | migration 092, send-tracking fields, `sendSync`, resend service/controller tests |
| B | `claude/invitation-resend-ui` | `256d9a7` | invitation send-status column, resend dialog, frontend service shape guard |
| C | `claude/invitation-resend-docs` | `c9d87f1` | closeout/runbook updates and no-auto-retry rationale |

Integration order was A -> B -> C. Package A fast-forwarded cleanly. Packages
B and C were merged with no-ff merge commits because `main` had advanced after
Package A landed. The existing local `.env` modification was left untouched and
was not staged.

## Integration Review Finding

The backend intentionally returns `200 OK` for a resend attempt that reached the
controller/service but failed at SMTP dispatch. The failure is represented in
the returned `SiteInvitationDto`:

- `lastSendStatus = FAILED`
- `lastSendError = <captured send failure>`
- `status` remains `PENDING`

The initial UI branch treated every resolved `resendInvitation(...)` promise as
a success and displayed `Invitation re-sent...` even when the DTO recorded
`FAILED`. The create-invitation dialog had the same issue for an initial invite
whose synchronous send failed.

This integration adds a small frontend follow-up:

- Creation now checks `created.lastSendStatus` before choosing success/error
  toast text.
- Resend now checks `updated.lastSendStatus`; `FAILED` keeps the dialog open,
  updates the row, shows the inline error, and does not show a success toast.
- Two Jest regressions pin the cross-boundary case where HTTP resolves but the
  payload records send failure.

No backend behavior was changed by the integration follow-up.

## Verification

| Gate | Command | Result |
|---|---|---|
| Backend targeted | `/tmp/apache-maven-3.9.9/bin/mvn -B -q -Dstyle.color=never -Dmaven.repo.local=.m2-cache/repository -Dspring.profiles.active=test -Dtest=SiteInvitationServiceTest,SiteInvitationControllerSecurityTest,SiteInvitationControllerTest,EmailNotificationServiceTest test` | 4 suites, 32 tests, 0 failures, 0 errors |
| Frontend target | `CI=true npm test -- --runTestsByPath src/pages/SiteInvitationsPage.test.tsx --watchAll=false` | 1 suite, 11 tests, 0 failures |
| Frontend lint | `npm run lint` | clean |
| Frontend production build | `CI=true npm run build` | compiled successfully; CRA bundle-size advisory remains informational |

`ecm-core/./mvnw` was attempted first and failed before Maven execution because
this repository wrapper delegates Maven to Docker and the local Docker socket
was unavailable:

```text
failed to connect to the docker API at unix:///Users/chouhua/.docker/run/docker.sock
```

The backend gate was therefore rerun with the existing host Maven binary under
`/tmp/apache-maven-3.9.9/bin/mvn`, matching the pattern used by recent Athena
verification documents.

## Design State After Integration

- Operators can see the invitation email send outcome directly in the site
  invitation table.
- `PENDING` invitations can be manually resent by an admin or site MANAGER.
- Send failures are visible both as row state and as dialog feedback after a
  failed resend attempt.
- Automatic retry remains deliberately out of scope for v1. The operator
  runbook records revisit triggers.
- The frontend does not trust HTTP success alone for email-send UX; it reads
  the DTO-level `lastSendStatus`.

## Remaining Work

- Push and monitor CI for the integrated main branch.
- Optional follow-up: add a compact "failed invitations" filter if operators
  start handling many failed rows.
- Optional follow-up: add auto retry/DLQ only if the documented revisit
  thresholds are met.
