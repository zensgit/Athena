# Docker Infrastructure Setup Report — 2026-04-13

## Context

New machine onboarding after device switch. The project had a complete Docker Compose definition (18 services) but was missing several bind-mount directories, config files, and had startup-blocking code issues that prevented the stack from running.

## Environment

- **Machine**: Apple Silicon (arm64), macOS 26.4.1, 24GB RAM, 43GB disk
- **Docker**: Docker Desktop 4.68.0, Engine 29.3.1, Compose v5.1.1
- **Install method**: `brew install --cask docker`

## Issues Found & Fixed

### 1. Missing Bind-Mount Directories (Severity: Blocker)

`docker compose up` would fail immediately because these directories referenced in volume mounts did not exist:

| Directory | Purpose | Fix |
|-----------|---------|-----|
| `init-scripts/` | PostgreSQL init scripts | Created with `.gitkeep` |
| `odoo/config/` | Odoo container config | Created with `odoo.conf` |
| `odoo/addons/` | Odoo custom addons | Created with `.gitkeep` |
| `monitoring/grafana/provisioning/` | Grafana auto-provisioning | Created with datasource + dashboard provider YAML |
| `monitoring/grafana/dashboards/` | Grafana dashboard JSON | Created with ECM overview dashboard |
| `nginx/ssl/` | TLS certificates | Created with `.gitkeep` |

### 2. Missing `ecm-core/.dockerignore` (Severity: Performance)

Without `.dockerignore`, the entire build context (~500MB+ including `.git`, `target/`, `logs/`) was sent to the Docker daemon on every build. Created `.dockerignore` excluding `.git`, `target/`, `logs/`, IDE files, and `src/test/`.

### 3. Dockerfile `syntax` Directive (Severity: Blocker in China network)

`ecm-core/Dockerfile` line 1 contained:

```dockerfile
# syntax=docker/dockerfile:1.6
```

This forces Docker BuildKit to pull the `docker/dockerfile:1.6` frontend image from Docker Hub on every build. In restricted network environments (China), this consistently fails with `connection reset by peer`. Removed the directive — Docker Desktop's built-in BuildKit already supports `--mount=type=cache` natively.

### 4. Multi-Constructor Spring Bean Ambiguity (Severity: Blocker)

Two `@Service` classes had multiple constructors without `@Autowired`, causing Spring to fail with `No default constructor found`:

| Class | File | Fix |
|-------|------|-----|
| `BulkImportService` | `ecm-core/src/main/java/com/ecm/core/service/BulkImportService.java` | Added `@Autowired` to public 7-param constructor |
| `TransferReplicationService` | `ecm-core/src/main/java/com/ecm/core/service/TransferReplicationService.java` | Added `@Autowired` to public 8-param constructor |

**Root cause**: Both classes use a telescoping constructor pattern (public constructor delegates to package-private constructor that accepts an `Executor`). When Spring finds multiple constructors and none is annotated with `@Autowired`, it falls back to looking for a no-arg constructor, which doesn't exist.

### 5. JODConverter / LibreOffice Mismatch (Severity: Blocker)

Building with `SKIP_LIBREOFFICE=true` (to speed up image build) while `JODCONVERTER_LOCAL_ENABLED=true` caused a startup crash:

```
officeHome doesn't exist or is not a directory: /usr/lib/libreoffice
```

**Fix**: Set `JODCONVERTER_LOCAL_ENABLED=false` in `.env`. Document conversion is a non-core feature and can be re-enabled when LibreOffice is installed in the image.

### 6. Missing Health Checks & Startup Ordering (Severity: Reliability)

`docker-compose.yml` had basic `depends_on` (list style) which only waits for container creation, not readiness. Services would start before their dependencies were actually ready.

**Changes**:

| Service | Before | After |
|---------|--------|-------|
| ecm-core depends_on | List of 9 services | 6 services with `condition: service_healthy` |
| ecm-core healthcheck | 60s start_period, 3 retries | 90s start_period, 5 retries |
| keycloak | No healthcheck | TCP health check on `/health/ready` + `KC_HEALTH_ENABLED=true` |
| postgres-keycloak | No healthcheck | `pg_isready` |
| postgres-odoo | No healthcheck | `pg_isready` |
| ecm-frontend | No healthcheck | `wget` on port 80 |
| nginx | No healthcheck | `wget` on `/health` |
| All depends_on | List style | `condition: service_healthy` or `service_started` |

### 7. Docker Hub Network Issues (Severity: Environment)

Docker Hub access from China was intermittent (`connection reset by peer`). Mitigated by:
- Pre-pulling base images with retry loops before building
- Removing the `syntax` directive that required an extra image fetch
- Adding `registry-mirrors` to `daemon.json` (partially effective)

## New Files Added

| File | Purpose |
|------|---------|
| `.env.example` | Committed environment variable template (79 lines) |
| `ecm-core/.dockerignore` | Docker build context exclusions |
| `scripts/bootstrap.sh` | One-command new machine setup (`--core-only`, `--skip-build` flags) |
| `monitoring/grafana/provisioning/datasources/prometheus.yml` | Auto-register Prometheus in Grafana |
| `monitoring/grafana/provisioning/dashboards/provider.yml` | Auto-discover dashboard JSON files |
| `monitoring/grafana/dashboards/ecm-overview.json` | ECM overview dashboard (JVM heap, HTTP rate/latency, DB connections, Redis) |
| `odoo/config/odoo.conf` | Odoo container configuration |
| `init-scripts/.gitkeep` | PostgreSQL init scripts placeholder |
| `odoo/addons/.gitkeep` | Odoo custom addons placeholder |
| `nginx/ssl/.gitkeep` | TLS certificate placeholder |

## Final Service Status

All 10 core containers running and healthy:

```
NAME                         STATUS              PORTS
athena-ecm-core-1            Up (healthy)        0.0.0.0:7700->8080
athena-ecm-frontend-1        Up (healthy)        0.0.0.0:5500->80
athena-elasticsearch-1       Up (healthy)        0.0.0.0:9200->9200
athena-keycloak-1            Up (healthy)        0.0.0.0:8180->8080
athena-keycloak-db-1         Up                  5432
athena-minio-1               Up (healthy)        0.0.0.0:9205->9000
athena-postgres-1            Up (healthy)        0.0.0.0:5432->5432
athena-postgres-keycloak-1   Up (healthy)        5432
athena-rabbitmq-1            Up (healthy)        0.0.0.0:5672->5672
athena-redis-1               Up (healthy)        0.0.0.0:6390->6379
```

**Verification**:
- `GET http://localhost:7700/actuator/health` returns `{"status":"UP"}`
- `GET http://localhost:5500/` returns HTTP 200

## Services Not Started (Deferred)

These services are commented out in ecm-core's `depends_on` due to Docker Hub image pull failures. Re-enable when images are cached locally:

| Service | Image | Reason Deferred |
|---------|-------|-----------------|
| ml-service | Build from `./ml-service` (python:3.11-slim) | Base image pull failed |
| collabora | collabora/code:24.04.13.2.1 | Image pull failed |
| clamav | clamav/clamav:stable (linux/amd64) | Image pull failed |
| odoo | odoo:16 | Not needed for core verification |
| nginx | nginx:alpine (already cached) | No upstream services to proxy yet |
| prometheus | prom/prometheus:latest | Monitoring not critical for verification |
| grafana | grafana/grafana:latest | Monitoring not critical for verification |
| greenmail | greenmail/standalone:2.0.0 | Mail testing not needed |

## Commit

```
253eaa9 infra: harden Docker setup for new machine onboarding
14 files changed, 529 insertions(+), 17 deletions(-)
```

## Recommended Next Steps

1. **Pull remaining images** when network stabilizes: `docker compose pull`
2. **Re-enable optional services** in `docker-compose.yml` ecm-core depends_on
3. **Run Playwright smoke test**: `cd ecm-frontend && npx playwright test e2e/frontend-acceptance-smoke.spec.ts --project=chromium`
4. **Build with LibreOffice** for document conversion: `docker compose build ecm-core` (without `SKIP_LIBREOFFICE`)
