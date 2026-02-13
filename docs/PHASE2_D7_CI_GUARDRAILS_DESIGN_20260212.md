# Phase 2 Day 7 - Ops / CI Guardrails (Design)

Date: 2026-02-12

## Problem

`docker-compose.yml` loads mail automation settings via:

- `env_file: [.env, .env.mail]`

This is correct for keeping OAuth/IMAP secrets out of the repo (since `.env.mail` is gitignored), but it has an onboarding failure mode:

- A clean checkout without a local `.env.mail` causes `docker compose ...` to fail before any containers start.
- `scripts/restart-ecm.sh` and `scripts/verify.sh` call `docker compose`, so they fail the same way.

## Goals

- Local developer scripts should not fail when `.env.mail` is intentionally absent.
- The default path remains secure:
  - never commit secrets
  - `.env.mail` stays gitignored
  - `.env.mail.example` remains the safe template for real OAuth configuration

## Non-goals

- Auto-provisioning OAuth tokens or populating real mail credentials.
- Changing the actual docker compose contract (we keep `env_file: .env.mail` because the app expects those env vars when mail automation is enabled).

## Design

### Guardrail: auto-create a safe `.env.mail`

Add a small guard to the scripts that call docker compose:

1. If `.env.mail` exists: do nothing.
2. Else if `.env.mail.example` exists:
   - copy it to `.env.mail`
   - set `chmod 600` (best-effort)
   - log a single line noting it is placeholders only
3. Else:
   - create a minimal placeholder `.env.mail` file with comments
   - set `chmod 600` (best-effort)
   - log a single line

This makes `docker compose` invocations deterministic on clean machines without requiring secrets.

### Files

- `scripts/restart-ecm.sh`
  - adds `ensure_env_mail` and calls it before any `docker compose` usage
- `scripts/verify.sh`
  - creates `.env.mail` (placeholder) after argument parsing and before any `docker compose` usage
  - logs a clear warning once the logging helpers are initialized

### CI alignment

CI already writes a placeholder `.env.mail` in jobs that bring up docker compose (see `.github/workflows/ci.yml`). The local script guardrail mirrors that behavior.

## Acceptance Criteria

- On a clean checkout where `.env.mail` is missing:
  - `bash scripts/restart-ecm.sh` succeeds past compose parsing (and proceeds to build/up).
  - `./scripts/verify.sh --no-restart --smoke-only --skip-build --skip-wopi` runs without failing due to missing `.env.mail`.
- The generated `.env.mail` contains no secrets and remains gitignored.
