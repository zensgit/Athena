# Phase 1 P1 - Search Spellcheck Interaction Enhancement (Dev)

## Summary
Improved the search results spellcheck experience to surface suggestions even when results exist, avoid duplicate/identical suggestions, and present clearer guidance for switching to suggested terms.

## Changes
- Trigger spellcheck fetch when the query changes (not only on zero results).
- Filter and de-duplicate suggestions that match the current query.
- Present primary + secondary suggestions with clearer contextual messaging.

## Files Touched
- ecm-frontend/src/pages/SearchResults.tsx

## Notes
- The "Did you mean" text is preserved for existing E2E expectations.
