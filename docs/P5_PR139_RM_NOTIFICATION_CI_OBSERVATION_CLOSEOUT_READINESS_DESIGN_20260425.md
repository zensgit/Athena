# P5 PR-139 RM Notification CI Observation Closeout Readiness Design

## Goal

Freeze the closeout criteria for the RM notification lane so the next executor can promote the lane from development-complete to accepted only after real CI evidence exists.

## Problem

The code and gate are now wired and hardened, but local execution still cannot prove full acceptance:

- backend targeted tests use the Docker-backed Maven wrapper
- the Playwright acceptance flows use live backend, frontend, Keycloak, and database state
- this sandbox cannot access Docker socket for the full gate
- this sandbox cannot read GitHub Actions because `api.github.com` is unreachable

Without a closeout-readiness note, it would be easy to overstate the lane as accepted based only on static checks and test discovery.

## Closeout Rule

Do not mark the notification lane accepted until GitHub Actions shows:

- `frontend_e2e_core` completed successfully
- the `Run RM notification acceptance gate` step completed successfully
- the gate executed the five backend targeted test classes
- the gate executed the four `@rm-notification-acceptance` Playwright flows

## Evidence To Capture

When CI access is available, capture:

- workflow run id
- commit SHA
- `frontend_e2e_core` conclusion
- log excerpt or artifact proving `p5_rm_notification_acceptance_gate: ok`
- confirmation that the failure artifact bundle includes `ecm-core/target/surefire-reports` if the gate fails

## Boundaries

- no runtime behavior changed
- no test selection changed
- no closeout status is promoted in this slice
- email/webhook delivery remains out of scope
