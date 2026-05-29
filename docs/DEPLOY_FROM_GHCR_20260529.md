# Deploying Athena from ghcr.io published images (2026-05-29)

The **Release images to ghcr.io** workflow publishes the three app images on
each `v*` tag, so a networked host can deploy without building from source.

## What "download from GitHub" actually means here

It is **three pieces, not one file**:

| Piece | Source |
|---|---|
| App images | `ghcr.io/zensgit/athena-{ecm-core,ecm-frontend,ml-service}` (pulled) |
| Compose files | this repo: `docker-compose.yml` + `docker-compose.prod.yml` + `docker-compose.ghcr.yml` |
| `prod.env` | you create it — **MUST set the prod-only required vars** (e.g. `GF_SECURITY_ADMIN_USER`, `ELASTIC_PASSWORD`, `MINIO_ROOT_*`) or `up` fails with `required variable ... is missing` |

Third-party images (postgres, elasticsearch, keycloak, …) are pulled from their
normal registries per the base compose files.

## Steps (networked target host)

1. Get the compose files (clone the repo, or copy the three `docker-compose*.yml` + `.env.example`).
2. `cp .env.example prod.env` and fill it (real secrets, hostnames, TLS, and the prod-only required vars above).
3. (only if the ghcr packages are **private**) `echo "$GHCR_TOKEN" | docker login ghcr.io -u <user> --password-stdin`
4. Pick a tag: `export ATHENA_TAG=v2026.05.29` (or `latest`).
5. Pull, then start without building:
   ```bash
   docker compose -f docker-compose.yml -f docker-compose.prod.yml -f docker-compose.ghcr.yml \
     --env-file prod.env pull ecm-core ecm-frontend ml-service
   docker compose -f docker-compose.yml -f docker-compose.prod.yml -f docker-compose.ghcr.yml \
     --env-file prod.env up -d --no-build
   ```
6. Before going live: `scripts/prod-deploy-preflight.sh --env-file prod.env --require-daemon`.

For a **fully offline** target (no GitHub access), use `scripts/build-offline-bundle.sh` instead.

## Publishing (maintainers) — the `[skip ci]` gotcha

Push a `v*` tag **whose head commit message does NOT contain `[skip ci]`**.
That token skips *all* workflows for the push — including a tag push — so a tag
pointing at a `[skip ci]` commit silently fails to trigger this workflow.

Then: GitHub → Actions → **Release images to ghcr.io** runs; confirm the three
images appear under **Packages**, and set their visibility to **Public** for
login-free pulls (first publish is private by default).
