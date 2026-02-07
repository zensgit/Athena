# Search + Preview P2 — Verification

Date: 2026-02-06

## Automated Tests
- `cd ecm-frontend && npm run lint`
- `cd ecm-core && mvn -q -DskipTests package`

## Manual Verification Checklist
1. **Search explanation**
   - Run a search with a keyword that appears in name/description/content.
   - Confirm result snippets show labeled highlights like `Name: ...` or `Content: ...`.
   - If multiple fields match, verify two snippets appear separated by `•`.

2. **Force rebuild failed previews**
   - Filter search results by `Failed` preview status.
   - Click “Force rebuild failed”.
   - Confirm previews are queued and status chips update as the queue progresses.
