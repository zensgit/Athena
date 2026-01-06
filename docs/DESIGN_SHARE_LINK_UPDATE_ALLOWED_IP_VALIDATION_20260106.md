# Design: Share Link Update Allowed IP Validation (2026-01-06)

## Goal
Ensure invalid `allowedIps` input on share-link update is rejected with HTTP 400.

## Scope
- Controller-layer behavior for `PUT /api/v1/share/{token}`.
- No API schema changes.

## Approach
- Mock `ShareLinkService.updateShareLink` to throw `IllegalArgumentException`.
- Verify `RestExceptionHandler` maps to HTTP 400.

## Files
- `ecm-core/src/test/java/com/ecm/core/controller/ShareLinkControllerValidationTest.java`
