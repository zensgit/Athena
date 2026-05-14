# Site Invitation Service Shape Guards Design and Verification

## Context

The frontend service hardening line closes places where Phase 5 mocked E2E or
SPA fallback can return HTML with HTTP 200 and still be treated as a valid JSON
DTO. `siteInvitationService` already guarded the resend readback and rejected
non-array list responses, but it still trusted create, accept, and reject
readbacks directly.

The backend contract comes from `SiteInvitationController` and
`SiteInvitationService.SiteInvitationDto`:

- `GET /sites/{siteId}/invitations` returns `List<SiteInvitationDto>`.
- `POST /sites/{siteId}/invitations`, `POST /invitations/accept`,
  `POST /invitations/reject`, and
  `POST /sites/{siteId}/invitations/{invitationId}/resend` return one
  `SiteInvitationDto`.
- `DELETE /sites/{siteId}/invitations/{invitationId}` returns `204 No Content`.
- Send-tracking fields are deliberately nullable except `sendAttemptCount`,
  where null means no send attempt or no successful send timestamp yet.

## Design

- Extend the existing invitation response guard from resend/list-only coverage
  to every endpoint that reads a `SiteInvitationDto` body.
- Validate required string fields: `id`, `siteId`, `siteTitle`,
  `inviteeEmail`, `invitedRole`, `status`, `invitedBy`, `expiresAt`, and
  `createdDate`.
- Validate nullable string fields explicitly: `inviteeUsername`, `message`,
  `acceptedAt`, `lastSendAttemptAt`, `lastSendError`, and `lastSentAt`.
- Validate `lastSendStatus` as `SENT`, `FAILED`, or `null`.
- Validate `sendAttemptCount` as a finite number.
- Preserve the existing exported
  `SITE_INVITATION_RESEND_UNEXPECTED_RESPONSE_MESSAGE` constant. Its name is
  historical, but page tests and mocked factories already depend on it.
- Keep `cancelInvitation` unchanged as a no-content endpoint.

## Files Changed

- `ecm-frontend/src/services/siteInvitationService.ts`
- `ecm-frontend/src/services/siteInvitationService.test.ts`
- `docs/SITE_INVITATION_SERVICE_SHAPE_GUARDS_DESIGN_VERIFICATION_20260514.md`

## Verification

### Targeted Service Test

```bash
cd ecm-frontend
CI=true npm test -- --runTestsByPath src/services/siteInvitationService.test.ts --watchAll=false
```

Result:

- 1 suite passed.
- 9 tests passed.
- Coverage includes HTML fallback rejection, malformed list item rejection,
  create/accept/reject/resend readback guards, nullable send-tracking fields,
  and no-content cancel wiring.

### Full Frontend Gates

```bash
cd ecm-frontend
npm run lint
```

Result: passed.

```bash
cd ecm-frontend
CI=true npm run build
```

Result: compiled successfully. CRA still reports the existing bundle-size
advisory, and Node emits the known `fs.F_OK` dependency deprecation warning;
neither failed the build.

### Integration and Remote CI

Pending after integration with the parallel `siteService` slice:

- combined service Jest run
- remote GitHub Actions

## Residual Work

- This slice does not change invitation product behavior.
- The unexpected-response constant still says "resend endpoint" because it is
  part of the existing mocked page-test contract. A future cleanup can add a
  general alias, but this slice avoids widening the page test surface.
- Other site collaboration services still need equivalent response-shape
  guards; this slice only covers invitation endpoints.
