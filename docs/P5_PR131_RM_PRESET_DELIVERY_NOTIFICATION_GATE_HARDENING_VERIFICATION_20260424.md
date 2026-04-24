# P5 PR-131 RM Preset Delivery Notification Gate Hardening Verification

## Static Script Check

Command:

```bash
bash -n scripts/p5-rm-notification-acceptance-gate.sh
```

Result:

- passed

## Frontend Acceptance Discovery

Command:

```bash
cd ecm-frontend && npm run e2e:rm-notification:acceptance -- --list
```

Result:

- discovered `RM failed scheduled preset delivery creates inbox notification @rm-notification-acceptance (full-stack)`
- discovered `RM successful scheduled preset delivery creates inbox notification @rm-notification-acceptance (full-stack)`
- discovered `RM disabled success notification preference suppresses inbox alert @rm-notification-acceptance (full-stack)`
- discovered `RM disabled failure notification preference suppresses inbox alert @rm-notification-acceptance (full-stack)`
- `Total: 4 tests in 1 file`

## Full Gate Attempt

Command:

```bash
scripts/p5-rm-notification-acceptance-gate.sh
```

Result:

- blocked at backend targeted tests
- gate invoked the expanded backend set:
  - `RmReportPresetDeliveryServiceTest`
  - `RmReportPresetControllerTest`
  - `RmReportPresetControllerSecurityTest`
  - `ActivityServiceTest`
  - `NotificationInboxServiceTest`
- Docker socket access failed with `permission denied while trying to connect to the docker API at unix:///Users/chouhua/.docker/run/docker.sock`

Required acceptance:

- run in an environment with Docker socket access or native Maven plus live backend/frontend/Keycloak services
- after `PR-132`, the authoritative runner is the GitHub Actions `frontend_e2e_core` job step named `Run RM notification acceptance gate`

## Static Diff Check

Command:

```bash
git diff --check
```

Result:

- passed
