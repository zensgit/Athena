# Design: Share Link Allowed IP Validation (2026-01-06)

## Goal
Reject invalid IP/CIDR entries at share-link creation/update time to prevent silent misconfiguration.

## Scope
- Validate `allowedIps` in share-link create and update flows.
- Use existing error handling (`IllegalArgumentException` -> HTTP 400).

## Approach
- Parse `allowedIps` as comma-separated entries.
- Accept blank entries as no-op.
- For each entry:
  - If no `/`, require a valid IP address.
  - If `/` present, require valid IP and prefix length within address bit size.

## Behavior
- Invalid entries throw `IllegalArgumentException("Invalid allowedIps entry: ...")`.
- Empty string clears IP restrictions on update.

## Files
- `ecm-core/src/main/java/com/ecm/core/service/ShareLinkService.java`
- `ecm-core/src/test/java/com/ecm/core/service/ShareLinkServiceTest.java`
