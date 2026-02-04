# Phase 42 - Webhook Subscriptions Development (2026-02-04)

## Scope
Introduce admin-managed webhook subscriptions and event delivery for key system notifications.

## Backend Changes
- Added `WebhookSubscription` entity + repository.
- Added `WebhookNotificationService` to deliver signed event payloads.
- Added `WebhookSubscriptionService` for CRUD + test delivery.
- Added `WebhookController` endpoints:
  - `GET /api/v1/webhooks`
  - `GET /api/v1/webhooks/event-types`
  - `POST /api/v1/webhooks`
  - `PUT /api/v1/webhooks/{id}`
  - `DELETE /api/v1/webhooks/{id}`
  - `POST /api/v1/webhooks/{id}/test`
- Hooked `NotificationService` to dispatch webhook notifications in parallel with RabbitMQ.

## Database
- Liquibase change `026-add-webhook-subscriptions.xml` creates:
  - `webhook_subscriptions`
  - `webhook_subscription_event_types`

## Frontend Changes
- Added admin page `/admin/webhooks` to create/list/test/delete subscriptions.
- Added admin menu item "Webhooks".

## Files Updated
- `ecm-core/src/main/java/com/ecm/core/entity/WebhookSubscription.java`
- `ecm-core/src/main/java/com/ecm/core/repository/WebhookSubscriptionRepository.java`
- `ecm-core/src/main/java/com/ecm/core/integration/webhook/WebhookNotificationService.java`
- `ecm-core/src/main/java/com/ecm/core/integration/webhook/WebhookSubscriptionService.java`
- `ecm-core/src/main/java/com/ecm/core/controller/WebhookController.java`
- `ecm-core/src/main/java/com/ecm/core/service/NotificationService.java`
- `ecm-core/src/main/resources/db/changelog/changes/026-add-webhook-subscriptions.xml`
- `ecm-frontend/src/pages/WebhookSubscriptionsPage.tsx`
- `ecm-frontend/src/components/layout/MainLayout.tsx`
- `ecm-frontend/src/App.tsx`
- `ecm-frontend/src/components/layout/MainLayout.menu.test.tsx`
