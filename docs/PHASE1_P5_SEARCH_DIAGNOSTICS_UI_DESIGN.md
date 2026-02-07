# Search + Preview UI P5 — Score + Queue Summary

Date: 2026-02-06

## Goals
- Surface search relevance score alongside matched fields.
- Provide a lightweight summary of preview queue retries.

## Design
1) **Score chip**
- Display `Score <value>` alongside matched fields for each result.

2) **Preview queue summary**
- Show a small summary under preview status filters when there are queued retry entries.

## Files Changed
- `ecm-frontend/src/pages/SearchResults.tsx`

