# P5 PR-135 RM Notification Gate Failure Artifacts Verification

## Workflow Evidence

The `frontend_e2e_core` failure artifact path includes:

```text
ecm-core/target/surefire-reports
```

This captures Maven Surefire reports produced by the backend targeted tests in `scripts/p5-rm-notification-acceptance-gate.sh`.

## Static Workflow Check

Command:

```bash
ruby -e 'require "yaml"; YAML.load_file(".github/workflows/ci.yml"); puts "yaml ok"'
```

Result:

- passed
- output: `yaml ok`

## Static Diff Check

Command:

```bash
git diff --check
```

Result:

- passed
