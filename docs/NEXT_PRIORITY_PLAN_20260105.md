# Next Priority Plan (2026-01-05)

## High Priority
- Add CI pipeline to run `mvn test` and frontend lint/test on PRs.
- Add regression coverage for search ACL edge cases (admin bypass, missing node IDs, deleted nodes).
- Harden audit export: validation for empty ranges, max range limit, and user feedback.

## Medium Priority
- Search performance: verify ES index refresh + pagination correctness with large datasets.
- PDF preview UX: loading states and error recovery for unsupported/empty documents.
- Expand E2E coverage for version history and download/share flows.

## Low Priority
- Document the duplicate `org.json` warning and clean dependency tree.
- Consolidate verification docs with a single dashboard summary.
