# P0a-3b — ml-service non-root (matrix A11) — Verification

Date: 2026-05-26 · Brief: `docs/HARDENING_P0A3B_MLSERVICE_NONROOT_BRIEF_20260526.md` (gate-approved, D1/D2/D3).
Matrix §8 A11 of `docs/PRODUCTION_READINESS_ASSESSMENT_20260525.md`.

## Changes shipped

- **`ml-service/Dockerfile`** — added fixed-uid non-root user, chown writable paths before privilege drop:
  ```dockerfile
  RUN mkdir -p /var/ml-service \
      && groupadd -r app -g 10001 \
      && useradd -r -g app -u 10001 app \
      && chown -R 10001:10001 /var/ml-service /app
  USER 10001:10001
  ```
- **`scripts/ml-service-dockerfile-check.sh`** (new, executable) — static regression guard (D1, option 1): asserts `USER 10001`, the `chown -R 10001:10001 /var/ml-service /app`, the `useradd` with the fixed uid, and that privilege is **not** reset to root after the drop.

No compose change. No CI workflow change. No app code change.

## Verification — honest, ranked

### Static (on-box, no daemon) — DONE ✅
```
./scripts/ml-service-dockerfile-check.sh            → rc=0 "OK: ... non-root (uid 10001) ..."
# negatives prove the guard bites:
  Dockerfile + trailing 'USER root'                 → rc=1 "privilege is reset to root ... AFTER the non-root drop"
  Dockerfile with USER lines stripped               → rc=1 "missing 'USER 10001:10001' (non-root) directive"
```
Catches *regression of the USER directive only* — nothing about runtime.

### Runtime (off-box, daemon required) — PENDING B4 / owner ⏳
This box has **no Docker daemon** (`docker info` → cannot connect to socket; no `athena_ml_models` volume present), so the runtime layer was **not** run here. The actual hardening guarantee — to be executed by the owner on a daemon-equipped box:
```
docker compose build ml-service
docker compose up -d ml-service
curl -f http://<host>:8082/health                   # expect 200, modelLoaded reflects volume
# prove non-root can WRITE the model volume:
curl -X POST .../train  (>=5 documents)             # expect 200 "trained" (pickle.dump succeeds as uid 10001)
docker compose restart ml-service && curl .../health # model survives restart
docker compose exec ml-service id                    # expect uid=10001 gid=10001 (not root)
```

### Brownfield migration (off-box, daemon) — owner runbook ⏳
Resolved volume name is **`athena_ml_models`** (verified via `docker compose -f docker-compose.yml config` → `ml_models` → `athena_ml_models`; project prefix from dir name). A pre-existing root-owned volume from a root-era deployment stays root-owned → `/health` still reports `modelLoaded:true` but `/train` fails on write (partial success). One-time fix (confirm the real name first, then chown):
```
docker run --rm -v athena_ml_models:/v alpine chown -R 10001:10001 /v   # keeps trained model
# or: docker volume rm athena_ml_models   (loses model → retrain)
```
**Owner must accept this runbook before production rollout** (gate D3).

## CI

**No CI gate for this slice, by design** — CI never builds/starts ml-service (zero `ml-service` mentions in `.github/workflows/`; dep commented `docker-compose.yml:81`). The static script is not auto-wired into any CI job (wiring it is an optional future follow-up); it is an on-box guard. A `[skip ci]`-class commit is appropriate, but a normal push is harmless since the Dockerfile/script are outside the CI build graph.

## Status

- A11 **code/config + static guard: DONE** (on-box static verification green).
- A11 **runtime + brownfield migration: PENDING B4 / owner sign-off** (no daemon on this box).
- This closes the CI-gateable / design portion of A11. Runtime proof and the brownfield runbook acceptance remain owner deliverables.
