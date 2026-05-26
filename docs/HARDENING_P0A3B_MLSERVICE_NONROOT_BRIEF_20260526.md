# P0a-3b — ml-service non-root (matrix A11) — READ-ONLY BRIEF

Date: 2026-05-26 · Status: **read-only, awaiting gate** · Matrix: §8 A11 of `docs/PRODUCTION_READINESS_ASSESSMENT_20260525.md`
Split-from: P0a-3 (A11 carved out because CI never builds/starts ml-service — see `docs/HARDENING_P0A3_PROD_COMPOSE_VERIFICATION_20260526.md` §A11).

## ⚠️ This slice has NO CI gate, by design

CI builds only `ecm-core ecm-frontend` and **never builds or starts ml-service** — verified: **zero** `ml-service` mentions in `.github/workflows/` (`grep -rn ml-service .github/workflows` → none); compose dep is commented (`docker-compose.yml:81`). The runtime hardening guarantee (a non-root process can boot, serve `/health`, and write the model) is therefore **owner-signed off-box runtime verification = gate item B4** — not a CI pass. This is the whole reason A11 is its own slice; the brief must not soften it into "CI will catch it."

## Current state (verified)

- `ml-service/Dockerfile`: `FROM python:3.11-slim`, installs tesseract/poppler/curl, `pip install -r requirements.txt`, `COPY app/`, `RUN mkdir -p /var/ml-service`, `EXPOSE 8080`, `CMD ["uvicorn","app.main:app","--host","0.0.0.0","--port","8080"]`. **No `USER` directive → runs as root.**
- Writable paths at runtime:
  - `/var/ml-service` — named volume `ml_models` (`docker-compose.yml:294,464`); `MODEL_PATH=/var/ml-service/model.pkl`. `/train` does `open(MODEL_PATH,"wb"); pickle.dump(...)` (`app/main.py:307`). Startup `load_model()` reads it (`:136`).
  - `/tmp` — pdf2image/pytesseract OCR scratch (default tempdir). World-writable on `-slim`; **fine as-is, not in scope**.
- Health: `GET /health` (NOT `/actuator/health` — that's ecm-core; do not conflate).

## Proposed change (A11) — Dockerfile only

Add a fixed-uid non-root user; chown the writable dirs **before** dropping privilege:

```dockerfile
# after the COPY app/ and mkdir lines, before EXPOSE:
RUN groupadd -r app -g 10001 && useradd -r -g app -u 10001 app \
    && chown -R 10001:10001 /var/ml-service /app
USER 10001:10001
```

- **Fixed numeric uid `10001`** (not just name): stable across rebuilds, deterministic for the migration chown, drops into K8s `runAsUser` later.
- chown covers `/var/ml-service` (volume root) and `/app` (WORKDIR / app code).
- No other Dockerfile change. **compose volume mount stays exactly as-is** — only the *owner of the volume root* changes.

## The load-bearing trap: named-volume ownership (brownfield)

Docker seeds an **empty** named volume's root with the image dir's ownership on first mount → fresh deployments inherit `10001:10001` and `/train` works. **But an existing `ml_models` volume from a root-era deployment stays root-owned forever.** Failure mode is a *partial success*, not a crash:
- `load_model()` reads existing root-owned `model.pkl` fine (644) → `/health` reports `modelLoaded:true` ✅
- `/train` → `open(...,"wb")` → **PermissionError, silent-ish 500** ❌

Operator runbook (B4 / owner, off-box, one-time):
```bash
# FIRST confirm the REAL volume name — Compose prefixes with the project name.
# Default project = lowercased dir → the volume is `athena_ml_models`, NOT `ml_models`.
docker compose -f docker-compose.yml config --format json | python3 -c \
  'import sys,json;print(json.load(sys.stdin)["volumes"]["ml_models"]["name"])'   # → athena_ml_models
docker volume ls | grep ml_models                                                 # cross-check

# Option A: re-own the existing volume (keeps trained model). Use the RESOLVED name.
docker run --rm -v athena_ml_models:/v alpine chown -R 10001:10001 /v
# Option B: recreate volume (loses model → must retrain)
docker volume rm athena_ml_models
```
Using the bare `ml_models` here is a footgun: it would create/chown a *new, unused* volume and
leave the real `athena_ml_models` root-owned. This runbook is the single thing most likely to
bite a real upgrade — it must be in the deployment notes.

## Verification boundary (honest, ranked)

| Layer | Where | What it proves |
|---|---|---|
| **Static** | on-box, no daemon | Dockerfile text contains `USER 10001`, `chown -R 10001:10001`, and is not reset to root later. Catches *accidental regression of the USER directive only* — nothing about runtime correctness. |
| **Runtime** | **off-box, daemon required → B4** | `docker build` → `docker run` → `curl /health` 200 → `POST /train` (≥5 docs) proves non-root volume write → restart proves model survives. **The actual hardening guarantee.** |
| **Brownfield** | **off-box, daemon → owner runbook** | mount a pre-existing root-owned volume, reproduce the `/train` failure, exercise the chown migration. |

This box has **no Docker daemon** (`docker info` → cannot connect; no `ml_models` volume present), so only the Static layer is runnable here.

### Static-check placement — GATE DECISION REQUIRED (pick one)

1. **`scripts/ml-service-dockerfile-check.sh`** — greps the Dockerfile for the required directives. Consistent with the `scripts/backend-preflight.sh` (E1) shell-script pattern. *(Recommended — most honest; can be wired into CI as a follow-up if desired, but is not auto-gated by merely existing.)*
2. **Java test in ecm-core** reading `../ml-service/Dockerfile` as text (mirrors `ProdProfileHardeningTest.readSource()`). Rides the existing Backend Verify gate — but the cross-module relative path is fragile (surefire basedir = ecm-core; `../ml-service` must be verified to resolve) and couples a Python service's Dockerfile to the Java module.
3. **No static check** — accept that the slice's only gate is owner runtime sign-off (B4).

## Explicitly OUT of scope (do not pull in)

- read-only rootfs / `--read-only`; capability drop (`cap_drop: [ALL]`)
- moving healthcheck from compose → Dockerfile
- OCR tempdir audit (pdf2image/pytesseract `/tmp` is fine)
- pip cache / multi-stage build / base-image bump
- any compose change beyond what A11 needs (none — mount unchanged)
- `.env` / secrets (S1/S2); P0b TLS/Keycloak-prod/backup

## Gate rulings (2026-05-26 — accepted)

- **D1 — static-check placement: option 1** (`scripts/ml-service-dockerfile-check.sh`). No ecm-core Java test for a Python service Dockerfile.
- **D2 — uid `10001` accepted.** No repo-wide numeric UID convention exists; deterministic + deployment-friendly.
- **D3 — proceed now** with Dockerfile + static script + verification doc; runtime guarantee marked **pending B4/owner**. Owner must accept the corrected brownfield volume-chown runbook (uses resolved `athena_ml_models`) before production rollout.
- **Brownfield runbook fix (Medium):** resolved volume name is `athena_ml_models` (verified via `docker compose config`); runbook updated to confirm-then-chown the real name.

Implementation scope: **Dockerfile only + static script + verification doc.** No compose change, no CI workflow rewrite, no claim that A11 runtime is proven from this box.
