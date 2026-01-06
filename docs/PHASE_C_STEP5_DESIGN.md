# Phase C Step 5 Design: Share Link Expiry & IP Restrictions

## Goal
Validate that share links respect IP allowlists and expiry timestamps.

## Scope
- Create a share link with restrictive IP allowlist and verify public access is denied.
- Create an already-expired share link and verify public access is denied.

## Implementation
- Update `scripts/verify-phase-c.py` to:
  - Create a link with `allowedIps` set to `203.0.113.1/32` and confirm `403` on access.
  - Create a link with `expiryDate` set to now minus 1 minute and confirm `403` on access.
- Note: `allowedIps` accepts comma-separated IP/CIDR entries, is normalized (trimmed, empty entries removed), and invalid entries return HTTP 400.

## Success Criteria
- IP-restricted link denies access from non-allowed IPs.
- Expired link denies access.

## Rollback
- Revert the new checks in `scripts/verify-phase-c.py`.
