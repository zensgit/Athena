# P5 PR-144 RM Notification Preflight Fast CI Gate Design

## Goal

Run the RM notification closeout preflight in the non-Docker frontend CI job so missing scripts, broken tagged-test discovery, and focused frontend contract regressions fail early.

## Problem

The live acceptance gate runs inside `frontend_e2e_core`, after Docker stack startup. That is the right place for full-stack proof, but it is late and expensive for local/static regressions such as:

- missing `scripts/p5-rm-notification-acceptance-gate.sh`
- missing `scripts/p5-rm-notification-closeout-preflight.sh`
- invalid workflow YAML
- broken acceptance test discovery
- reintroduced bare Playwright API `response.ok()` assertions
- People preference service contract regressions
- Records Management preference rollback regressions

## Change

The `frontend` job now runs:

```bash
scripts/p5-rm-notification-closeout-preflight.sh
```

after `npm ci` and before `Lint`, `Type check`, `Build`, and general unit tests.

## Placement Rationale

The step is intentionally in the fast `frontend` job, not the Docker-backed E2E job.

Reasons:

- it requires frontend dependencies but not a live backend
- it fails before Docker build/startup
- it proves the scripts that later CI steps depend on are present
- it keeps full acceptance semantics with `frontend_e2e_core`

## Boundaries

- this does not replace the live `Run RM notification acceptance gate` step
- this does not promote the notification lane to accepted
- no runtime endpoint, table, or migration changed
