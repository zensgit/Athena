# Phase367Y: Context Compare Direct Lineage Fallback

## Goal

Remove the last remaining page-state dependency from `VersionHistoryDialog` context-menu compare actions by switching them to direct checkout-lineage fallback data.

## Scope

- Update `Compare with checkout baseline` to use effective baseline lineage version, not only loaded page markers.
- Update `Compare with checkout current` to use effective current lineage version, not only loaded page markers.

## Design

After the direct checkout-lineage API was added, the main `Compare checkout lineage` banner action already no longer depended on loaded version pages.  
This phase finishes the same job for row-level context actions so every compare entry point behaves consistently.

## File

- `ecm-frontend/src/components/dialogs/VersionHistoryDialog.tsx`

## Claude Code

Claude Code was used as a parallel design assistant to validate that switching context compare actions to effective lineage versions was the smallest next cleanup slice after the direct checkout-lineage API. Final implementation and validation were completed in this workspace.
