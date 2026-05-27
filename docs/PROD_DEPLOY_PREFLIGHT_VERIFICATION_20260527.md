# Production Deploy Preflight Verification (2026-05-27)

## Trigger

GitHub issue #18 reported a server deployment attempt on commit `a0d1a6e` where legacy
`docker-compose` v1 rejected `docker-compose.prod.yml` because it does not understand Compose v2
YAML tags such as `!reset`. The same attempt also used a temporary
`docker-compose.ddl-update.yml` workaround to force Hibernate `ddl-auto=update`.

## Decision

Do **not** make the production override v1-compatible. Production hardening intentionally relies
on Compose v2 merge semantics (`!reset` / `!override`) to remove dev env files, close internal
ports, and swap prod nginx config. The production path is:

1. Install/use Docker Compose v2 plugin: `docker compose`.
2. Provide a real external prod env file, for example `/etc/athena/prod.env`.
3. Run `scripts/prod-deploy-preflight.sh --env-file /etc/athena/prod.env --require-daemon`.
4. Only then run `docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d`.

`ddl-auto=update` is not a production deployment path. If `ddl-auto=validate` fails, treat it as a
schema/migration issue and fix it with Liquibase before cutover.

## Artifact

- `scripts/prod-deploy-preflight.sh`

The script validates without printing secret values:

- Docker Compose v2 plugin is available.
- Legacy `docker-compose` v1 is not used for prod.
- Docker daemon is reachable when `--require-daemon` is passed.
- Prod env file exists and contains required key names.
- `.tmp.prod.env` is not used.
- `JWT_SECRET` is absent.
- `.env` and `ecm-frontend/.env` remain untracked.
- Temporary `SPRING_JPA_HIBERNATE_DDL_AUTO=update` compose overrides are absent.
- `scripts/b1b2-prod-config-check.sh` still passes.
- Merged base+prod compose validates and keeps `SPRING_PROFILES_ACTIVE=prod`, no
  `SPRING_JPA_HIBERNATE_DDL_AUTO=update`, and no `ecm-core` `env_file`.

## Local Verification

Commands run on the development host:

```bash
bash -n scripts/prod-deploy-preflight.sh
scripts/prod-deploy-preflight.sh --help
```

Result: passed.

Synthetic env-file verification used fake non-secret placeholder values only:

```bash
scripts/prod-deploy-preflight.sh --env-file <synthetic-placeholder-env>
```

Expected result on this host: static/config checks pass; Docker daemon reachability is only a
warning unless `--require-daemon` is passed.

Result: passed.

Negative checks:

```bash
scripts/prod-deploy-preflight.sh --env-file <missing-file>
scripts/prod-deploy-preflight.sh --env-file <synthetic-env-containing-JWT_SECRET>
```

Expected result: both fail without printing values.

Result: passed.

## Runtime Boundary

This preflight does not prove B1/B2 runtime cutover, TLS certificate validity, Keycloak token
issuer matching, backup/restore, or B4 full-stack smoke. Those remain owner/ops tasks on a host
with a Docker daemon, real domains, real certs, and rotated secrets.
