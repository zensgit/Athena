# Deploying Athena from ghcr.io published images (2026-05-29)

> **Status (2026-05-29):** first publish done via `workflow_dispatch` (run 26629788357, all 3 jobs green).
> `ghcr.io/zensgit/athena-{ecm-core,ecm-frontend,ml-service}` are **public** with `latest` + `sha-` tags.
> Note: push/tag auto-trigger was not firing at publish time, so releases currently go via manual dispatch.

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

Then confirm the three images appear under **Packages**. (Here they published
**public** by default — verified `visibility=public`; if yours come up private,
set them Public for login-free pulls.)

## If push / tag does not trigger Actions

Observed 2026-05-29: pushes to `main` and `v*` tag pushes produced **no
check-suites** (no run started), while `workflow_dispatch` worked. Ruled out via
API: repo is public (Actions free), `enabled=true`, `allowed_actions=all`, all
workflows `state=active`, engine healthy (a dispatched run finished green). That
points to a GitHub platform / account-side condition, not a workflow bug.

Owner checks (web UI / account — not reachable from the CLI):
1. Repo → **Actions** tab: look for a banner (dormant-repo notice, "Enable workflows", or other alert).
2. **Settings → Actions → General**: confirm permissions aren't narrowed; no unexpected branch/event restriction.
3. **github.com/settings/billing**: a payment/spending hold can suppress auto-triggers even with public (free) Actions.
4. Edit + commit a workflow from the web UI (sometimes re-registers triggers).
5. Still nothing → open a GitHub Support ticket (push-trigger failure on a public repo is a platform anomaly).

**Until then, publish via manual dispatch:** `gh workflow run release-images.yml`
— that is how the first publish was done. The deploy steps above are unaffected.
