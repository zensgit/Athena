# Phase C Step 4 Design: Share Link Hardening

## Goal
Extend share-link verification to cover password protection, access limits, and manual deactivation.

## Scope
- Password-protected share links require a password and reject incorrect passwords.
- Access-limit share links enforce max access count.
- Deactivated share links block public access.

## Implementation
- Update `scripts/verify-phase-c.py` to:
  - Create a password-protected link; verify `401` without password and `200` with correct password.
  - Create a link with `maxAccessCount=2`; verify third access returns `403`.
  - Create a link, deactivate it, and verify access returns `403`.
  - Confirm access count increments for standard link via `/api/v1/share/{token}`.

## Success Criteria
- Password requirement enforced (401 + `passwordRequired=true`).
- Access limit enforced on third access.
- Deactivated links are invalid for public access.

## Rollback
- Revert the new share-link checks in `scripts/verify-phase-c.py`.
