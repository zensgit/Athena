# Design: Share Link Allowed IP Normalization (2026-01-06)

## Goal
Normalize `allowedIps` input to a consistent comma-separated format and avoid persisting blank values.

## Scope
- Share-link create/update flows only.

## Approach
- Trim and split entries by comma.
- Drop empty entries.
- If no entries remain, persist `null`.
- Keep validation on the normalized value.

## Files
- `ecm-core/src/main/java/com/ecm/core/service/ShareLinkService.java`
- `ecm-core/src/test/java/com/ecm/core/service/ShareLinkServiceTest.java`
