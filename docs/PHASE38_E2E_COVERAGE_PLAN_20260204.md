# Phase 38 E2E Coverage Plan (Next Targets)

## High Priority (P0)
- Mail Automation: OAuth error path (missing refresh token), ensure warning + disabled Test Connection.
- Search: Similar search empty state + back-to-results recovery in prod build.
- Permissions: explicit deny override visualization + copy ACL confirmation toast.
- Versioning: compare dialog content assertions across minor/major version changes.

## Medium Priority (P1)
- Preview: server-render fallback banner + retry queue states.
- Audit: preset with missing user/event type shows warning and blocks export.
- Rules: scheduled rule manual trigger backfill boundary validation.

## Lower Priority (P2)
- Mail Automation: retention cleanup UI (bulk select + delete) and export CSV field toggles.
- Search: sort stability across pagination for mixed mime types.
