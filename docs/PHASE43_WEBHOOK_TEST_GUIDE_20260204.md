# Phase 43 - Webhook Test Guide (2026-02-04)

## Goal
Validate webhook subscription delivery, signature header, and status tracking.

## Steps
1. Open `/admin/webhooks` and create a subscription targeting a request bin (e.g., https://webhook.site or your own endpoint).
2. Choose event types or leave empty for ALL.
3. Click the test action (paper plane icon) to send a test event.
4. Verify the request payload contains:
   - `eventType`
   - `deliveryId`
   - `timestamp`
   - `payload`
5. If a secret is configured, ensure the request has `X-ECM-Signature` header (Base64 HMAC SHA256).
6. Back in Athena, confirm the subscription row shows the latest status code.

## Expected
- Subscription status updates with HTTP status code.
- `lastSuccessAt` or `lastFailureAt` updates accordingly.
