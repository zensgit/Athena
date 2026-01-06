# Design: E2E Share Link Access (2026-01-06)

## Goal
- Extend E2E coverage to validate share link access rules (password gating, deactivation, access limits).

## Approach
- Create share links via API for a test document.
- Validate password-required responses, successful access with password, deactivation blocking, and access-limit enforcement.

## Files
- ecm-frontend/e2e/version-share-download.spec.ts
