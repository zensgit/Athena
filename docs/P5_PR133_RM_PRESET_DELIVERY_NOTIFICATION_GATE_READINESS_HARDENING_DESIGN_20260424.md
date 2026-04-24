# P5 PR-133 RM Preset Delivery Notification Gate Readiness Hardening Design

## Goal

Reduce false-negative CI failures in the newly wired RM notification acceptance gate without changing the product behavior or test semantics.

## Problem

`PR-132` attaches the gate to `frontend_e2e_core`, but the gate still used one-shot `curl` checks for:

- backend health
- Keycloak realm discovery
- UI reachability

The CI stack is already waited on before the gate starts, but transient readiness gaps can still happen after backend targeted tests or under runner load. A single failed `curl` would make the gate fail before Playwright produced useful browser evidence.

## Change

`scripts/p5-rm-notification-acceptance-gate.sh` now uses a shared `wait_for_url` helper for the three readiness checks.

New environment knobs:

- `CHECK_RETRIES`, default `12`
- `CHECK_SLEEP_SECONDS`, default `5`
- `CURL_TIMEOUT_SECONDS`, default `5`

The default envelope gives each readiness target up to roughly one minute while preserving strict failure if the target never becomes reachable.

## Boundaries

- backend targeted tests still run before live-service checks
- Playwright acceptance selection is unchanged
- CI workflow placement is unchanged
- no runtime endpoint, table, migration, or frontend behavior changed

## Operational Effect

Failures after this change should be more meaningful:

- if the target becomes reachable during the retry window, the gate proceeds
- if the target stays unreachable, the log names the failed target and URL
- if Playwright fails, the existing `frontend_e2e_core` artifacts still capture browser output and Docker logs
