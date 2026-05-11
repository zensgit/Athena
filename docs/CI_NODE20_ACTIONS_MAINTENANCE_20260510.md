# CI Node 20 Actions Maintenance

Date: 2026-05-10

## Context

CI run `25630086406` was fully green, but GitHub Actions emitted Node.js 20
deprecation warnings for official action runtimes. This is not a product
failure, but it is a low-risk maintenance item because the workflow already has
stable green gates.

## Scope

Update only `.github/workflows/ci.yml`:

| Action | Before | After |
|---|---:|---:|
| `actions/checkout` | `v4` | `v6` |
| `actions/setup-java` | `v4` | `v5` |
| `actions/setup-node` | `v4` | `v6` |

No product code, test logic, Docker command, package version, Java version, or
Node version was changed. Existing Maven and npm cache configuration remains
explicit.

## Design Notes

- Keep the CI job graph unchanged so this commit only validates action runtime
  compatibility.
- Keep `node-version: '20'` unchanged because the warning is about the action
  runtime, not the project runtime.
- Keep explicit `cache: 'maven'` and `cache: 'npm'` settings to avoid accidental
  cache behavior changes.

## Local Verification

| Gate | Command | Result |
|---|---|---|
| Legacy action scan | `rg "actions/(checkout\|setup-java\|setup-node)@v4" .github/workflows/ci.yml` | no matches |
| New action scan | `rg "actions/(checkout\|setup-java\|setup-node)@v(5\|6)" .github/workflows/ci.yml` | all targeted actions present |
| YAML parse | `ruby -e "require 'yaml'; YAML.load_file('.github/workflows/ci.yml'); puts 'yaml ok'"` | `yaml ok` |
| Whitespace | `git diff --check` | clean |

## GitHub Actions Verification

Push to `origin/main` at `85495c0` triggered CI run `25645408410`.

| Job | Result | Duration |
|---|---|---:|
| Backend Verify | success | 2m24s |
| Frontend Build & Test | success | 10m11s |
| Phase C Security Verification | success | 5m10s |
| Acceptance Smoke (3 admin pages) | success | 6m32s |
| Property Encryption Closeout Gate | success | 4m53s |
| Frontend E2E Core Gate | success | 12m14s |
| Phase 5 Mocked Regression Gate | success | 5m37s |

Run outcome: 7/7 jobs green.

Follow-up log scan:

```bash
gh run view 25645408410 --log \
  | rg -i "node20|node\.js 20|actions/checkout@v4|actions/setup-node@v4|actions/setup-java@v4|following actions use node.js 20|deprecated node.js version|node.js 20 actions"
```

Result: no matches. The GitHub Actions Node.js 20 action-runtime deprecation
warning is no longer present.

The run still contains dependency-level warnings from `npm ci` and Java
compilation, for example deprecated transitive frontend packages and deprecated
Java APIs. Those are separate dependency/runtime modernization items and were
not changed in this workflow-only maintenance slice.
