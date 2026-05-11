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

## Expected CI Validation

The push should run the normal 7-job CI matrix:

- Backend Verify
- Frontend Build & Test
- Property Encryption Closeout Gate
- Acceptance Smoke (3 admin pages)
- Phase 5 Mocked Regression Gate
- Frontend E2E Core Gate
- Phase C Security Verification

Success criteria: all 7 jobs remain green and the Node.js 20 action-runtime
deprecation warning disappears.
