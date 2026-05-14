# Discussion Service Shape Guards Design and Verification

## Context

The frontend hardening line continues to close service boundaries that can
mistake SPA HTML fallback or malformed JSON for valid API data.
`discussionService` is the only consumer of `DiscussionController`, which
backs `DiscussionPage` topic and reply CRUD. The previous service untyped
every response as the expected DTO via `api.get<TopicDto>(...)`, so an HTML
fallback or a backend shape drift would crash a renderer at the first field
read instead of failing fast at the service boundary.

`DiscussionController.TopicDto` and `ReplyDto` are clear: topic `content` is
nullable in the entity and reply `parentReplyId` is nullable for top-level
replies. Both pages are standard Spring `Page` envelopes. This slice guards
malformed responses without rejecting the valid nullable backend states.

## Design

- Add a shared `DISCUSSION_UNEXPECTED_RESPONSE_MESSAGE` for malformed
  discussion responses.
- Structural validators:
  - `TopicDto`: string `id`, `siteId`, `title`, `createdBy`, `createdDate`;
    nullable-string `content`; `status` restricted to `OPEN | CLOSED |
    PINNED`; `tags` must be an array of strings; numeric `replyCount`.
  - `ReplyDto`: string `id`, `topicId`, `content`, `createdBy`,
    `createdDate`; nullable-string `parentReplyId`.
  - `TopicPage` and `ReplyPage`: required `content` array of valid items;
    numeric `totalElements`, `totalPages`, `number`, and `size`.
- Guard `listTopics`, `getTopic`, `createTopic`, `updateTopic`,
  `listReplies`, `createReply`, and `updateReply` readbacks at the service
  boundary.
- Keep `deleteTopic` and `deleteReply` unchanged. They are no-content delete
  endpoints; current consumers only await HTTP success/failure.
- Preserve the existing endpoint paths, pagination params, and request
  bodies verbatim — this slice is response-shape hardening, not a wiring
  change.

## Files Changed

- `ecm-frontend/src/services/discussionService.ts`
- `ecm-frontend/src/services/discussionService.test.ts`
- `docs/DISCUSSION_SERVICE_SHAPE_GUARDS_DESIGN_VERIFICATION_20260514.md`

## Verification

### Targeted Service Tests

```bash
cd ecm-frontend
CI=true npm test -- --runTestsByPath src/services/discussionService.test.ts \
  --watchAll=false
```

Result:

- 1 suite passed
- 20 tests passed
- New coverage rejects HTML fallback for topic and reply pages; rejects
  malformed topic/reply page items and malformed readbacks; accepts nullable
  topic content and nullable reply parent IDs; preserves delete endpoint wiring.

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
git diff --check -- ecm-frontend/src/services/discussionService.ts \
  ecm-frontend/src/services/discussionService.test.ts \
  docs/DISCUSSION_SERVICE_SHAPE_GUARDS_DESIGN_VERIFICATION_20260514.md
```

Result: passed.

### Remote CI

Run: `25848961285`

Commit: `46cf6fe fix(discussions): guard service responses`

Result: passed.

- Backend Verify: passed
- Frontend Build & Test: passed
- Phase C Security Verification: passed
- Property Encryption Closeout Gate: passed
- Frontend E2E Core Gate: passed
- Acceptance Smoke (3 admin pages): passed
- Phase 5 Mocked Regression Gate: passed

## Residual Work

- This does not add new discussion product capability.
- `deleteTopic` and `deleteReply` still trust HTTP success/failure rather
  than response-body shape because they are designed as no-content
  endpoints.
- `DiscussionPage` component tests are unchanged in this slice; this slice
  covers the service contract only.
- Other frontend services may still need similar shape guards; this slice
  only covers discussion service reads and readbacks used by current
  consumers.
