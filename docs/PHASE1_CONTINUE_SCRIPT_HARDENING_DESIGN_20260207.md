# Phase 1 Continue Script Hardening Design (2026-02-07)

## Goal

Harden local reliability scripts so ongoing development does not get blocked by:

- protected health endpoints (`/actuator/health` returns `401/403`)
- recurring RabbitMQ `plugins-expand` startup corruption

## Problems Addressed

1. `scripts/smoke.sh`
- hard-failed when `/actuator/health` was not publicly accessible.
- this produced false negatives while backend APIs were actually healthy.

2. `scripts/restart-ecm.sh`
- rebuilt/restarted only `ecm-core` and `ecm-frontend`.
- did not auto-heal RabbitMQ stale `*-plugins-expand` dirs that caused restart loops.

## Design Decisions

1. Keep behavior strict for real outages:
- health still fails for non-`200`/`401`/`403` status.

2. Treat `401/403` health as protected endpoint, not outage:
- when token exists, verify readiness through authenticated `/api/v1/system/status`.
- without token, continue best-effort and let later authenticated steps decide.

3. Add RabbitMQ preflight auto-heal in restart script:
- freeze restart policy
- stop RabbitMQ container
- move stale `*-plugins-expand` dirs to timestamped `.stale.*`
- restore restart policy and start RabbitMQ
- continue normal `ecm-core`/`ecm-frontend` rebuild flow

## Files Changed

- `scripts/smoke.sh`
- `scripts/restart-ecm.sh`

## Compatibility

- No API/schema/data model changes.
- Script-level resilience only.
- Existing workflows remain valid.

