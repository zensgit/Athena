# Calendar Service Shape Guards Design and Verification

## Context

The frontend hardening line continues to close service boundaries that can
mistake SPA HTML fallback or malformed JSON for valid API data.
`calendarService` backs `CalendarPage`, including range loading, event creation,
and event deletion. Before this slice, all calendar reads and readbacks trusted
the API body shape directly.

The backend `CalendarController.CalendarEventDto` has nullable optional detail
fields (`description`, `location`, and `recurrence`) and required core fields
for event identity, timing, ownership, and all-day state. This slice rejects
malformed responses without rejecting those valid nullable backend states.

## Design

- Add a shared `CALENDAR_UNEXPECTED_RESPONSE_MESSAGE` for malformed calendar
  responses.
- Guard `CalendarEventDto` responses:
  - required string `id`, `siteId`, `title`, `startDate`, `endDate`,
    `createdBy`, and `createdDate`;
  - nullable-string `description`, `location`, and `recurrence`;
  - required boolean `allDay`.
- Guard `CalendarEventPage` responses with a structural page validator.
- Guard range responses as arrays of valid `CalendarEventDto` values.
- Guard `getEvent`, `createEvent`, and `updateEvent` readbacks with the same
  event validator.
- Keep `deleteEvent(...)` unchanged because it is a no-content delete endpoint
  and current consumers only await HTTP success/failure.
- Preserve endpoint paths, query params, and request bodies verbatim.

## Files Changed

- `ecm-frontend/src/services/calendarService.ts`
- `ecm-frontend/src/services/calendarService.test.ts`

## Verification

### Targeted Service Test

```bash
cd ecm-frontend
CI=true npm test -- --runTestsByPath src/services/calendarService.test.ts --watchAll=false
```

Result:

- 1 suite passed
- 12 tests passed
- New coverage rejects HTML fallback for calendar pages; rejects malformed page
  items, range arrays, create readbacks, and update readbacks; accepts nullable
  optional detail fields; preserves delete endpoint wiring.

### Frontend Lint

```bash
cd ecm-frontend
npm run lint
```

Result: passed.

### Production Build

```bash
cd ecm-frontend
CI=true npm run build
```

Result: compiled successfully. CRA still reports the existing bundle-size
advisory. Node emitted the known dependency deprecation warning for `fs.F_OK`;
it did not fail the build.

### Diff Hygiene

```bash
git diff --check -- ecm-frontend/src/services/calendarService.ts \
  ecm-frontend/src/services/calendarService.test.ts \
  docs/CALENDAR_SERVICE_SHAPE_GUARDS_DESIGN_VERIFICATION_20260514.md
```

Result: passed.

### Remote CI

Run: pending.

Commit: pending.

Result: pending.

## Residual Work

- This does not add new calendar product capability.
- `deleteEvent(...)` still trusts HTTP success/failure rather than response-body
  shape because it is designed as a no-content endpoint.
- `CalendarPage` component tests are unchanged in this slice; this slice covers
  the service contract only.
- Other frontend services may still need similar shape guards; this slice only
  covers calendar service reads and readbacks used by current consumers.
