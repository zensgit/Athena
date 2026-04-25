# P5 PR-144A RM Notification Preflight CI Wiring Contract Design

## Goal

Make the RM notification closeout preflight verify the CI workflow contract, not only the scripts and frontend tests that the workflow eventually runs.

## Problem

The notification lane already had a concrete workflow/script drift failure: CI called `scripts/p5-rm-notification-acceptance-gate.sh`, but the script was not present on the pushed commit.

`PR-144` made the closeout preflight run early in the cheap `frontend` job, but the preflight still only parsed workflow YAML. A future edit could silently remove or move the live acceptance gate, remove backend Surefire artifacts, or drop the fast preflight step itself while still producing valid YAML.

## Change

`scripts/p5-rm-notification-closeout-preflight.sh` now checks CI workflow wiring semantics:

- `Run RM notification closeout preflight` exists
- `scripts/p5-rm-notification-closeout-preflight.sh` is called from the workflow
- the fast preflight step keeps `working-directory: .`
- `Run RM notification acceptance gate` exists
- `scripts/p5-rm-notification-acceptance-gate.sh` is called from the workflow
- `ecm-core/target/surefire-reports` remains in failure artifacts
- the fast preflight runs after dependency install and before lint
- the live acceptance gate runs after Keycloak realm readiness and before the core E2E gate

The script also accepts `CI_WORKFLOW_FILE` for deterministic negative tests against a temporary mutated workflow file.

## Boundaries

- this does not replace the live Docker-backed acceptance gate
- this does not prove GitHub Actions acceptance has passed
- this does not start the email delivery lane or consume `PR-145`
- no runtime endpoint, database schema, or product behavior changed
