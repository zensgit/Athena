# Phase 1 (P77) - Mail Reporting Single-option Auto-select Design (2026-02-11)

## Goal

Reduce empty-filter confusion in `Mail Reporting`:

- If there is exactly one mail account, preselect it.
- If there is exactly one rule, preselect it.
- If the user manually changes back to `All`, do not force re-select again.

## Problem

In the current UI, Account/Rule selectors can stay empty even when only one option exists, while the report tables already contain data. This looks inconsistent and increases operator confusion.

## Scope

- Frontend only:
  - `ecm-frontend/src/pages/MailAutomationPage.tsx`
  - `ecm-frontend/e2e/mail-automation.spec.ts`
- No backend API changes.
- No DB schema changes.

## Design

### State additions

- Add two interaction flags:
  - `reportAccountTouched`
  - `reportRuleTouched`

These flags prevent auto-selection from overriding explicit user choices.

### Auto-selection rules

- On account/rule list refresh:
  - If list is empty, clear selected value and reset touched flag.
  - If selected value no longer exists, clear value and reset touched flag.
  - If value is empty, touched flag is `false`, and list length is `1`, auto-select the only option.

### User interaction

- In `Select` `onChange` for Account/Rule:
  - Set touched flag to `true`.
  - Update selected value.

This preserves manual `All accounts / All rules` choices after first interaction.

## API / Contract impact

- No public API/interface/type changes.
- Existing report request format unchanged:
  - `accountId?: string`
  - `ruleId?: string`
  - `days: number`

## Risk and mitigation

- Risk: test environment points to stale deployed frontend (`:5500`) and does not include latest source changes.
- Mitigation: validate against local source dev server (`:3000`) when verifying new frontend behavior.
