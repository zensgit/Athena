# P5 PR-166 Site Invitation Manager Access and Token Acceptance Fix

Date: 2026-04-27

## Context

This change reviews the local Phase 5 frontend slice introduced by:

- `da06d40 feat(p5): Phase 5 frontend pages + mocked Playwright spec suite (11 specs, 80 tests)`
- `c216c9e docs: Phase 5 frontend design & verification summary`

The review found a cross-layer mismatch in the Site Invitation flow:

- Backend `SiteInvitationService` allows site invitation management for admins and site `MANAGER` members.
- Frontend routing and the Sites page entry point allowed only `ROLE_ADMIN`, blocking valid site managers.
- The invite dialog omitted the backend-supported `CONTRIBUTOR` role.
- The seeded invitation email only exposed a raw token, while the accept page required a `?token=` URL parameter and had no manual token fallback.

## Development

Implemented a scoped fix across frontend, backend email variables, database seed migration, and mocked E2E coverage.

Frontend:

- Changed `/admin/sites/:siteId/invitations` from admin-only to authenticated-only routing. Backend authorization remains the source of truth for admin/site-manager enforcement.
- Updated `SitesPage` to show the Invitations entry point to admins and selected-site `MANAGER` members.
- Added `CONTRIBUTOR` to the invitation role selector and gave the MUI Select an accessible label/id pair.
- Typed `InviteRequest.invitedRole` with `SiteMemberRole`.
- Added manual token entry on `/invitations/accept` when no `token` query parameter is present.

Backend:

- Added `ecm.frontend.base-url` backed invitation URL generation in `SiteInvitationService`, defaulting to `http://localhost:3000`.
- Added `invitationUrl` to the `site.invitation` email variables.
- Added Liquibase change `089-update-site-invitation-email-template-link.xml` to update the default email template with both a direct accept link and the raw-token fallback.

Tests:

- Added mocked E2E coverage for non-admin authenticated route access.
- Added mocked E2E coverage for `CONTRIBUTOR` role visibility.
- Replaced the missing-token error E2E with a manual-token accept flow.
- Extended `SiteInvitationServiceTest` to assert the generated invitation URL variable.

## Verification

Passed:

- `git diff --check`
- `xmllint --noout ecm-core/src/main/resources/db/changelog/db.changelog-master.xml ecm-core/src/main/resources/db/changelog/changes/089-update-site-invitation-email-template-link.xml`
- `cd ecm-core && ./mvnw -Dtest=SiteInvitationServiceTest test`
- `cd ecm-frontend && npm run lint`
- `cd ecm-frontend && CI=true npm run build`
- `cd ecm-frontend && ECM_UI_URL=http://127.0.0.1:5500 npx playwright test e2e/admin-site-invitations.mock.spec.ts e2e/invitation-accept.mock.spec.ts --project=chromium --workers=1`

Playwright result:

- `9 passed (19.1s)`

Known local command caveat:

- `cd ecm-frontend && npx tsc --noEmit` fails before project source checking because the installed `react-hook-form` declaration file uses TypeScript 5 syntax while the project is pinned to TypeScript 4.9. The effective CI compile path for this repo is `CI=true npm run build`, which passed.

## Risk Notes

- The frontend now lets authenticated non-admin users open the invitation page, but backend APIs still enforce admin or site-manager authorization.
- The new email URL uses `ecm.frontend.base-url`; deployments should set this value to the externally reachable frontend origin.
- The raw token remains in the email as a fallback, so existing operational workflows are not broken.
