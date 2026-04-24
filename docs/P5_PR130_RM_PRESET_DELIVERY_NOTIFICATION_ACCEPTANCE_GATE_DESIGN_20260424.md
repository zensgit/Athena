# P5 PR-130 RM Preset Delivery Notification Acceptance Gate Design

## Goal

Turn the remaining RM preset delivery notification acceptance work into one repeatable gate command.

## Problem

The notification lane had the right targeted checks, but they were split across notes:

- backend service/controller/security tests
- live Playwright success/failure notification smoke
- live Playwright disabled-preference smoke

That made the final acceptance path easy to misreport, especially when local Docker was unavailable.

## Changes

- add `ecm-frontend` script `e2e:rm-notification:acceptance`
- add top-level script `scripts/p5-rm-notification-acceptance-gate.sh`
- keep backend and frontend commands explicit rather than hiding environmental requirements
- make the gate fail early when backend tests or live services are unavailable

## Gate Contents

Backend targeted tests:

```text
RmReportPresetDeliveryServiceTest
RmReportPresetControllerTest
RmReportPresetControllerSecurityTest
ActivityServiceTest
NotificationInboxServiceTest
```

Frontend Playwright acceptance tests:

```text
@rm-notification-acceptance
```

## Usage

With backend, frontend, Keycloak, database, and supporting services running:

```bash
scripts/p5-rm-notification-acceptance-gate.sh
```

Override defaults if needed:

```bash
ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 KEYCLOAK_URL=http://localhost:8180 scripts/p5-rm-notification-acceptance-gate.sh
```

Frontend-only discovery:

```bash
cd ecm-frontend && npm run e2e:rm-notification:acceptance -- --list
```

## Non-Goals

- no GitHub Actions workflow change in this slice
- no Docker stack startup automation
- no test semantic changes
- no runtime endpoint, table, or migration change

## Subsequent CI Attachment

`PR-132` attaches this gate to GitHub Actions after the `PR-131` hardening work. The `no GitHub Actions workflow change` boundary above remains accurate for the original `PR-130` slice.
