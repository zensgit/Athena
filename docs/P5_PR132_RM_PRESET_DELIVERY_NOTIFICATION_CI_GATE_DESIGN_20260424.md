# P5 PR-132 RM Preset Delivery Notification CI Gate Design

## Goal

Attach the hardened RM preset delivery notification acceptance gate to GitHub Actions so the owner-inbox notification lane is no longer dependent on ad hoc local execution.

## Problem

`PR-130` and `PR-131` created a repeatable command, but full execution was still blocked locally by Docker socket access. That left the final acceptance proof outside the normal required CI path.

## Change

`frontend_e2e_core` now runs:

```bash
bash scripts/p5-rm-notification-acceptance-gate.sh
```

after the full E2E stack is started and after Keycloak realm discovery is reachable.

The workflow passes CI-specific URLs explicitly:

- `ECM_API_URL=http://localhost:7700`
- `ECM_UI_URL=http://localhost:5500`
- `KEYCLOAK_URL=http://localhost:8180`
- `KEYCLOAK_REALM=ecm`
- `PW_PROJECT=chromium`
- `PW_WORKERS=1`

## Placement Rationale

The gate is intentionally attached to the existing `frontend_e2e_core` job instead of a new job.

Reasons:

- it reuses the already-started backend, frontend, database, Keycloak, and supporting services
- it avoids a second Docker build/startup cycle
- it runs after the existing Keycloak realm readiness check, which the gate requires
- failures reuse the existing E2E artifact upload path for Playwright output and Docker logs

## Gate Contents

Backend targeted tests:

```text
RmReportPresetDeliveryServiceTest
RmReportPresetControllerTest
RmReportPresetControllerSecurityTest
ActivityServiceTest
NotificationInboxServiceTest
```

Frontend Playwright acceptance:

```text
@rm-notification-acceptance
```

The four tagged browser flows cover failed delivery notification, successful delivery notification, disabled success preference suppression, and disabled failure preference suppression.

## Non-Goals

- no runtime endpoint, table, or migration change
- no test semantic change
- no new Docker stack startup script
- no claim that local full-gate execution passed in this sandbox
