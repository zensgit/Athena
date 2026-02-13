# Design: Frontend Prebuilt Build Workflow (Dockerfile.prebuilt)

## Context

This repo supports two ways to build the frontend container:

1. `ecm-frontend/Dockerfile` (source build)
   - Runs `npm ci` and `npm run build` inside Docker.
2. `ecm-frontend/Dockerfile.prebuilt` (prebuilt static assets)
   - Only copies `ecm-frontend/build/` into an Nginx image.

Local development commonly enables **prebuilt** via `docker-compose.override.yml`:

- `ecm-frontend` service uses `dockerfile: Dockerfile.prebuilt`

## Problem

When `Dockerfile.prebuilt` is used:

- Updating `ecm-frontend/src/**` does **not** automatically update what the running UI serves.
- `docker compose up -d --build ecm-frontend` will rebuild the container, but it will still serve whatever is currently in `ecm-frontend/build/`.

This can create a mismatch where:

- Repo source and Playwright E2E tests expect a UI element/behavior,
- But the running UI does not include the change because `build/` is stale.

## Goal

Make the prebuilt workflow explicit and repeatable so developers (and automation) can reliably:

- regenerate `ecm-frontend/build/`, and
- refresh the `ecm-frontend` container to serve the new assets,
- before running E2E against `ECM_UI_URL=http://localhost:5500`.

## Solution

Add a small helper script:

- `scripts/rebuild-frontend-prebuilt.sh`

Behavior:

1. Ensures frontend deps exist (runs `npm ci` only if `node_modules/` is missing).
2. Runs `npm run build` to refresh `ecm-frontend/build/`.
3. Runs `docker compose up -d --build ecm-frontend` to refresh the running container.

## Operational Notes

- `ecm-frontend/build/` is git-ignored (by the repo-wide `build/` ignore rule).
  - This is expected: it is an artifact used for local prebuilt container builds.
- This script intentionally does not touch OAuth secrets/tokens.

## Alternatives Considered

1. Remove `docker-compose.override.yml` (always build from source in Docker)
   - More reproducible, but slower for local iteration.
2. Change `docker-compose.override.yml` to use `Dockerfile` instead of `Dockerfile.prebuilt`
   - Similar tradeoff: slower but always in sync.

We keep the prebuilt workflow (for speed) and provide an explicit rebuild path.

