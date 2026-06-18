# Athena ECM Publish Receiver — Network Topology (cross-reference)

Date: 2026-06-17
Status: Athena-side compose change is **split**: the base `docker-compose.yml`
stays ECM-publish-unaware (only a no-semantic `networks:` list→dict
conversion), and a new opt-in override file `docker-compose.ecm-publish.yml`
scopes the cross-stack reachability. Full design + verification recipe lives
in the Yuantus repo (see *Authoritative source* below).

## Why this doc exists

A lightweight pointer for Athena reviewers and operators who land on
`docker-compose.yml` and see the `ecm-network: {}` dict form (a base-only
change with no semantic effect on its own) and / or on
`docker-compose.ecm-publish.yml` and want to know what it is, why it is
separate, and how to apply it. The change is part of the PLM → ECM publish
integration (Yuantus `ecm-publication-worker` → Athena Transfer Receiver);
this file gives reviewer context without a hop across repos to understand
intent.

## Authoritative source

`Yuantus/docs/DEVELOPMENT_ECM_PUBLISH_DURABLE_REACHABILITY_TASKBOOK_20260617.md`

- §5.1 — idempotent network creation
  (`docker network inspect … >/dev/null 2>&1 || docker network create …`)
- §5.2 — **Athena-side compose split (this slice)**: base `networks:`
  list→dict (no semantic), NEW `docker-compose.ecm-publish.yml` override,
  pre-execution config checks
- §5.3 — Yuantus-side compose diffs (api gate flag, top-level networks,
  new profile-gated `ecm-publication-worker` service)
- §5.4 — Yuantus RUNBOOK rollout section
- §6 — verification recipe S1-S5 (idempotent network, config checks, DNS
  evidence, persistence proof, resilience proof) — owner-executed on the
  deploy host
- §7 — receipt skeleton for slice close-out

## Athena-side change summary

**Base `Athena/docker-compose.yml` — no-semantic only.**

`ecm-core` service's `networks:` block is converted from the list form
(`- ecm-network`) to dict form (`ecm-network: {}`). No new network is
referenced. The conversion exists solely so the override file below can
MERGE additional network entries into the service's network map — compose
merges dict-form network maps across `-f` overrides, but the list form is
replaced wholesale. Top-level `networks:` is unchanged; the base does NOT
declare `ecm-publish-net`.

**New `Athena/docker-compose.ecm-publish.yml` — opt-in override.**

Adds the `ecm-publish-net` join + the network alias `athena-ecm-core` to
`ecm-core`, and declares `ecm-publish-net` as an `external: true` top-level
network. Apply by appending `-f docker-compose.ecm-publish.yml` after the
base compose (and after any other override such as `docker-compose.prod.yml`
or a ghcr-image override):

```bash
docker compose -f docker-compose.yml \
               -f docker-compose.ecm-publish.yml up -d
```

The alias `athena-ecm-core` decouples the in-cluster URL from the
auto-generated container name (`athena-ecm-core-1`, project-name- and
replica-index-dependent and brittle). The Yuantus drainer uses
`YUANTUS_PUBLICATION_ECM_BASE_URL=http://athena-ecm-core:8080`.

No other Athena service joins `ecm-publish-net`; only `ecm-core` is
reachable to outsiders. No env block change on the Athena side. No
application-level change.

## Pre-execution config checks (run on the deploy host before `up`)

```bash
# 1) Base ALONE must NOT reference ecm-publish-net.
docker compose -f docker-compose.yml config \
  | grep -q ecm-publish-net \
  && { echo "FAIL: base compose leaks ecm-publish-net"; exit 1; } || true

# 2) Base + override MUST keep BOTH ecm-network AND add ecm-publish-net on
# ecm-core in the merged config. The grep-alias check alone is insufficient:
# it would pass even if the override REPLACED ecm-core.networks — leaving
# ecm-core off ecm-network and unable to reach postgres / elasticsearch /
# redis / rabbitmq / minio / keycloak. The jq assertion catches that.
docker compose -f docker-compose.yml -f docker-compose.ecm-publish.yml \
    config --format json \
  | jq -e '.services["ecm-core"].networks | has("ecm-network") and has("ecm-publish-net")' \
    >/dev/null \
  || { echo "FAIL: override broke ecm-core networks (lost ecm-network or missing ecm-publish-net)"; exit 1; }

# 3) Override must produce the alias athena-ecm-core (semantic check).
docker compose -f docker-compose.yml -f docker-compose.ecm-publish.yml config \
  | grep -q athena-ecm-core \
  || { echo "FAIL: override missing athena-ecm-core alias"; exit 1; }
```

`jq` is widely available; install it on the deploy host if missing
(`apt-get install jq` / `brew install jq`).

## What lives on the Yuantus side

Yuantus owns the full publish path (`release()` hook → outbox row →
`ecm-publication-worker` daemon → Athena Transfer Receiver call). The
Yuantus-side compose changes (taskbook §5.3) add:

- `api` service producer enqueue gate (`YUANTUS_ECM_PUBLISH_ENABLED`)
- new profile-gated `ecm-publication-worker` service that joins
  `ecm-publish-net` and reaches Athena via the alias above
- top-level `ecm-publish-net` external declaration mirroring this side's
  override

## Verification

S1-S5 in taskbook §6 — owner-executed on the deploy host:

- S1 brings the stack up with the idempotent network create + pre-execution
  config checks + the override-file invocation, then verifies the Yuantus
  profile-gating sanity (G3.4 — unprofiled `config` does NOT include
  `ecm-publication-worker`).
- S2 collects DNS evidence from inside the drainer container (G2).
- S3 verifies live drain via SQL against the same assertion bar the smoke
  script uses.
- S4 (persistence proof) recreates the drainer container and verifies the
  pipeline still works without manual `docker network connect`.
- S5 (resilience proof, optional) stops/starts Athena `ecm-core` and
  verifies the retry-wait observable matches the design (state='pending',
  attempt_count >= 1, next_attempt_at > now()).

Receipt skeleton (taskbook §7) collects commit hashes, config-check passes,
DNS evidence, smoke results, and the persistence + resilience proofs. The
Transfer Receiver secret is never written into this doc, the override file,
the receipt, or any operator screenshot.
