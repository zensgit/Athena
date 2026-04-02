# Phase368ZV Subscription Following Backbone Verification

## Focused backend tests

Passed:

```bash
cd ecm-core && mvn -q -Dtest=FollowingServiceTest,FollowingControllerTest,ActivityServiceTest,ActivityControllerTest test
```

## Focused frontend lint

Passed:

```bash
cd ecm-frontend && ./node_modules/.bin/eslint \
  src/pages/SitesPage.tsx \
  src/pages/ActivityFeedPage.tsx \
  src/services/activityService.ts \
  src/services/followingService.ts \
  src/services/siteService.ts
```

## Frontend build

Passed:

```bash
cd ecm-frontend && npm run -s build
```

Notes:

- Build still reports two pre-existing warnings outside this phase:
  - `src/components/share/ShareLinkManager.tsx`
  - `src/pages/AdminDashboard.tsx`

## Diff hygiene

Passed:

```bash
git diff --check -- \
  ecm-core/src/main/java/com/ecm/core/controller/ActivityController.java \
  ecm-core/src/main/java/com/ecm/core/controller/FollowingController.java \
  ecm-core/src/main/java/com/ecm/core/entity/FollowSubscription.java \
  ecm-core/src/main/java/com/ecm/core/entity/FollowTargetType.java \
  ecm-core/src/main/java/com/ecm/core/repository/ActivityRepository.java \
  ecm-core/src/main/java/com/ecm/core/repository/FollowSubscriptionRepository.java \
  ecm-core/src/main/java/com/ecm/core/service/ActivityService.java \
  ecm-core/src/main/java/com/ecm/core/service/FollowingService.java \
  ecm-core/src/main/resources/db/changelog/changes/049-create-follow-subscriptions-table.xml \
  ecm-core/src/main/resources/db/changelog/db.changelog-master.xml \
  ecm-core/src/test/java/com/ecm/core/controller/ActivityControllerTest.java \
  ecm-core/src/test/java/com/ecm/core/controller/FollowingControllerTest.java \
  ecm-core/src/test/java/com/ecm/core/service/ActivityServiceTest.java \
  ecm-core/src/test/java/com/ecm/core/service/FollowingServiceTest.java \
  ecm-frontend/src/pages/ActivityFeedPage.tsx \
  ecm-frontend/src/pages/SitesPage.tsx \
  ecm-frontend/src/services/activityService.ts \
  ecm-frontend/src/services/followingService.ts \
  docs/PHASE368ZV_SUBSCRIPTION_FOLLOWING_BACKBONE_DEV_20260402.md \
  docs/PHASE368ZV_SUBSCRIPTION_FOLLOWING_BACKBONE_VERIFICATION_20260402.md
```

## Coverage validated

- follow/unfollow validation for `SITE`, `USER`, and `NODE` targets
- grouped followed target extraction for personalized feed queries
- `GET /api/v1/activities/following` controller contract
- `Following` feed scope in the shared activity page
- selected-site follow/unfollow action in `SitesPage`
