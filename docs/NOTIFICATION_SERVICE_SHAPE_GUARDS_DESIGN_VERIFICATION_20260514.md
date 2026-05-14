# Notification Service Shape Guards Design and Verification

## Context

The current frontend hardening line is closing service boundaries that can
mistake SPA HTML fallback or malformed JSON for valid API data.
`notificationService` is a high-visibility surface because it feeds both:

- `MainLayout` unread-count polling and notification badge refresh events.
- `NotificationsPage` inbox/unread pagination, mark-read, mark-all-read, delete,
  and activity drill-down actions.

The backend contract is clear, but `NotificationController.NotificationDto.from`
can emit nullable activity fields when a notification no longer has an attached
activity row. Specifically, `activityType` and `actorUserId` are nullable in the
real response body. This slice guards malformed response bodies without
rejecting those valid nullable backend states.

## Design

- Add a shared `NOTIFICATION_UNEXPECTED_RESPONSE_MESSAGE` for malformed
  notification responses.
- Guard paginated inbox and unread responses with a structural
  `NotificationPage` validator:
  - required numeric `totalElements`, `totalPages`, `number`, and `size`;
  - required `content` array;
  - each notification must have string `id` and `createdAt`, boolean `read`,
    object `summary`, and nullable string metadata fields.
- Guard `getUnreadCount()` with a strict `{ count: number }` validator.
- Guard `markRead(...)` with the same `NotificationDto` validator used by page
  content.
- Guard `markAllRead()` with a strict `{ marked: number }` validator.
- Keep `deleteNotification(...)` unchanged because it is a no-content delete
  endpoint and current consumers only await HTTP success/failure.
- Normalize nullable notification activity fields in `notificationUtils` and
  `NotificationsPage`:
  - missing `activityType` displays/formats as `unknown`;
  - missing `actorUserId` displays/adapts as `system`;
  - global activity drill-down omits the `type` parameter when the backend did
    not provide an activity type.

## Files Changed

- `ecm-frontend/src/services/notificationService.ts`
- `ecm-frontend/src/services/notificationService.test.ts`
- `ecm-frontend/src/utils/notificationUtils.ts`
- `ecm-frontend/src/utils/notificationUtils.test.ts`
- `ecm-frontend/src/pages/NotificationsPage.tsx`

## Verification

### Targeted Service and Utility Tests

```bash
cd ecm-frontend
CI=true npm test -- --runTestsByPath src/services/notificationService.test.ts \
  src/utils/notificationUtils.test.ts --watchAll=false
```

Result:

- 2 suites passed
- 19 tests passed
- New coverage rejects HTML fallback for notification pages; rejects malformed
  notification page items, malformed unread counts, malformed mark-read
  readbacks, and malformed mark-all-read responses; accepts valid nullable
  backend activity fields; preserves delete endpoint wiring.

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
git diff --check -- ecm-frontend/src/services/notificationService.ts \
  ecm-frontend/src/services/notificationService.test.ts \
  ecm-frontend/src/utils/notificationUtils.ts \
  ecm-frontend/src/utils/notificationUtils.test.ts \
  ecm-frontend/src/pages/NotificationsPage.tsx \
  docs/NOTIFICATION_SERVICE_SHAPE_GUARDS_DESIGN_VERIFICATION_20260514.md
```

Result: passed.

### Remote CI

Run: pending.

Commit: pending.

Result: pending.

## Residual Work

- This does not add new notification product capability.
- `deleteNotification(...)` still trusts HTTP success/failure rather than
  response-body shape because it is designed as a no-content endpoint.
- Notification page component tests are still limited in this checkout; this
  slice covers the service contract and the shared formatting/link utility.
- Other frontend services may still need similar shape guards; this slice only
  covers notification service reads and readbacks used by current consumers.
