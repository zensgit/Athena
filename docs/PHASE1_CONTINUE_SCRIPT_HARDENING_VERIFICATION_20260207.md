# Phase 1 Continue Script Hardening Verification (2026-02-07)

## Scope

- verify `smoke.sh` health-check hardening
- verify `restart-ecm.sh` RabbitMQ self-heal path
- verify stack and critical APIs after restart

## Verification Steps

### 1) Script syntax checks

Commands:

- `bash -n scripts/smoke.sh`
- `bash -n scripts/restart-ecm.sh`

Result:

- both passed.

Status: `PASS`

### 2) Smoke script full execution

Command:

- `ECM_API=http://localhost:7700 bash scripts/smoke.sh`

Observed result:

- full smoke flow completed successfully, including:
  - system status/license/sanity
  - analytics + audit export/retention
  - antivirus + EICAR rejection
  - upload/search/facets/favorites
  - mail rule and scheduled rule checks
  - WOPI lock/put/unlock + version increment
  - workflow + trash/restore

Final line:

- `=== Smoke check finished successfully ===`

Status: `PASS`

### 3) Restart script with RabbitMQ self-heal

Command:

- `bash scripts/restart-ecm.sh`

Key output evidence:

- `RabbitMQ stale plugins dir moved: ...-plugins-expand -> ...-plugins-expand.stale.<timestamp>`
- both images rebuilt and services recreated
- script completed with `OK`

Status: `PASS`

### 4) Post-restart health

Commands:

- `docker inspect -f '{{.State.Health.Status}}' athena-rabbitmq-1`
- `docker inspect -f '{{.State.Health.Status}}' athena-ecm-core-1`

Result:

- `rabbitmq: healthy`
- `ecm-core: healthy`

Status: `PASS`

### 5) API sanity after restart

Commands (with admin token):

- `GET /api/v1/system/status`
- `GET /api/v1/integration/mail/diagnostics?limit=5`

Result:

- both returned HTTP `200`.

Status: `PASS`

## Outcome

- smoke script no longer falsely fails due protected health endpoint behavior.
- restart script now proactively heals known RabbitMQ startup corruption.
- stack is healthy and functional after automated restart.

