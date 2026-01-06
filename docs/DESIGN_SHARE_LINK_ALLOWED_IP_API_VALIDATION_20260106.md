# Design: Share Link Allowed IP API Validation (2026-01-06)

## Goal
Ensure invalid `allowedIps` input returns a clear HTTP 400 from the share-link API.

## Scope
- Controller layer validation behavior (via service exception mapping).
- No API schema changes.

## Approach
- Mock ShareLinkService to throw `IllegalArgumentException` on invalid input.
- Rely on `RestExceptionHandler` to map to HTTP 400.

## Files
- `ecm-core/src/test/java/com/ecm/core/controller/ShareLinkControllerValidationTest.java`
