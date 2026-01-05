# Design: Share Link CIDR Enforcement (2026-01-05)

## Goal
Harden share-link IP restrictions by enforcing true CIDR matching for IPv4 and IPv6, preventing prefix-based bypasses.

## Scope
- Share-link access IP evaluation only.
- No API contract changes.

## Approach
- Parse IP/CIDR using `InetAddress`.
- Support single IP entries and CIDR ranges (`/prefix`).
- Reject invalid CIDR inputs during evaluation (treated as non-matching).
- Keep the allowed IP list as a comma-separated string.

## Implementation Notes
- Matching is done by comparing network prefix bits (byte + bit mask).
- IPv4/IPv6 are handled by `InetAddress` and validated for address-length parity.

## Files
- `ecm-core/src/main/java/com/ecm/core/service/ShareLinkService.java`
- `ecm-core/src/test/java/com/ecm/core/service/ShareLinkServiceTest.java`
