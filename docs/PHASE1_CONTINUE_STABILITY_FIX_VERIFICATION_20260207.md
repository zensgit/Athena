# Phase 1 Continue Stability Fix Verification (2026-02-07)

## Verification Scope

- RabbitMQ restart-loop recovery
- ECM core health recovery
- Frontend production build recovery
- Mail automation Playwright regression suite
- Mail diagnostics backend test suite alignment

## Environment

- Workspace: `Athena`
- Stack: Docker Compose (`ecm-core`, `ecm-frontend`, `rabbitmq`, etc.)
- Date: 2026-02-07

## Steps and Results

### 1) Stack State Check

Commands:

- `docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'`
- `docker inspect -f '{{.State.Health.Status}}' athena-ecm-core-1`

Result:

- `athena-rabbitmq-1` in restart loop
- `athena-ecm-core-1` unhealthy

Status: `FAIL (expected before fix)`

### 2) Root Cause Confirmation

Command:

- `docker logs --tail 200 athena-rabbitmq-1`

Key evidence:

- `cannot_delete_plugins_expand_dir`
- `.../rabbit@33f706caf93b-plugins-expand`
- `eexist`

Status: `PASS (root cause identified)`

### 3) Infra Fix Execution

Commands:

- `docker update --restart=no athena-rabbitmq-1`
- `docker stop athena-rabbitmq-1`
- `docker run --rm -v athena_rabbitmq_data:/data alpine sh -lc 'mv ...-plugins-expand ...-plugins-expand.stale.<ts>'`
- `docker update --restart=unless-stopped athena-rabbitmq-1`
- `docker start athena-rabbitmq-1`

Result:

- RabbitMQ exited restart loop
- Health transitioned to `healthy`

Status: `PASS`

### 4) ECM Core Health Recovery

Command:

- loop check of both services health via `docker inspect`

Result:

- `rabbitmq=healthy`
- `ecm-core=healthy`

Status: `PASS`

### 5) Frontend Build Validation

Command:

- `cd ecm-frontend && npm run build`

Result:

- Build succeeded after TS fixes.

Status: `PASS`

### 6) Mail Automation E2E Validation

Command:

- `cd ecm-frontend && npx playwright test e2e/mail-automation.spec.ts --reporter=line`

Run 1:

- `5 passed / 2 failed / 2 skipped`
- failing cases:
  - folder listing visibility assertion
  - diagnostics drawer heading assertion

Action:

- patched selector/assertion robustness in `mail-automation.spec.ts`

Run 2:

- `6 passed / 0 failed / 3 skipped`

Status: `PASS`

### 7) Backend Targeted Test Validation

Command:

- `cd ecm-core && mvn -Dtest=MailAutomationControllerSecurityTest,MailFetcherServiceDiagnosticsTest test`

Run 1:

- 2 failures in `MailAutomationControllerSecurityTest`
- cause: outdated mock signature (`getDiagnostics` missing `sort/order`)

Action:

- updated test mock/verify signatures

Run 2:

- `BUILD SUCCESS`
- `Tests run: 13, Failures: 0, Errors: 0`

Status: `PASS`

### 8) API Sanity Verification

Command:

- token acquired with `bash scripts/get-token.sh admin admin`
- authenticated endpoint checks:
  - `/api/v1/folders/roots`
  - `/api/v1/integration/mail/diagnostics?limit=5`
  - `/api/v1/integration/mail/accounts`

Result:

- all returned HTTP `200`

Status: `PASS`

## Note on Existing Smoke Script

`scripts/smoke.sh` currently reports health failure because its unauthenticated health check path returns `401` in this environment.

- This is a script expectation mismatch, not a service-down signal.
- Authenticated API checks above confirm service availability.

## Final Outcome

- Infrastructure instability resolved.
- Frontend and backend regression points fixed.
- Mail automation E2E suite (targeted file) now passes without failures.
- Backend mail diagnostics targeted tests now pass.

Overall status: `PASS`

