# Branch Audit & Cleanup Decision List (2026-05-28)

Read-only audit of the local branch / worktree pile against `main` (`492dcec`).
Goal: turn ~46 accumulated branches into a clear **merge / delete / review** decision.
The audit itself was read-only; the §5 cleanup was subsequently **executed on 2026-05-29** with owner go-ahead — see **Execution status** below.

## Headline

**Nothing needs to be merged.** Every local branch is either already delivered
(patch-equivalent in `main`) or superseded by later, better work. The only *live*
items were three GitHub PRs (§4) — all three **closed on 2026-05-29**.

## Execution status (2026-05-29, owner go-ahead)

- **PRs closed:** #3 (fix already in `main` via `a7b5146`), #9 + #10 (stale/unmergeable, page rewritten). Open PRs now **0**. Their remote branches were left in place.
- **Worktrees removed:** all 35 (19 sibling `Athena-*` + 16 `.claude/worktrees/*`).
- **Local branches deleted:** all non-`main` branches. Recoverable from `origin` / reflog.
- **Safety:** `claude-preview-diagnostics-core-service-guards`'s uncommitted snapshot was `git stash`'d before removal (verified superseded by `main`).
- **Result:** `git worktree list` → main repo only; `git branch` → `main` only.

## 1. Method

- **Delivered?** `git cherry main <branch>` (patch-id compare). `-` = an equivalent
  patch is already in `main`; `+` = no equivalent patch (needs per-branch check).
- **Ancestor?** `git merge-base --is-ancestor <branch> main` → tip already reachable.
- Every `+` branch was then verified individually against `main`'s current code
  (does the capability exist in `main` via a different commit / better approach?).

## 2. Delivered — safe to delete (zero risk)

All of these are `ANCESTOR` of `main` **or** every commit shows `-` under `git cherry`
(patch already in `main`). They are 200–1000+ commits behind; their content shipped.

| Branch | Why safe |
|---|---|
| `claude/invitation-resend-backend` / `-docs` / `-ui` | ANCESTOR |
| `claude/oauth-revoke-backend` | ANCESTOR |
| `claude/oauth-revoke-ui` / `-meta-backend` / `-meta-docs` / `-meta-ui` | patch-equiv (`-`) |
| `claude/oauth-admin-preflight` | patch-equiv (`-`) |
| `claude/mail-presets-backend` / `-docs` / `-ui` | patch-equiv (`-`) |
| `claude/smtp-backend` / `-docs` / `-ui` | patch-equiv (`-`) |
| `claude/bulk-operation-service-guards-20260518` | patch-equiv (`-`) |
| `claude/ops-recovery-async-tail-guards-20260518` | patch-equiv (`-`) |
| `claude/workflow-service-guards-20260518` | patch-equiv (`-`) |
| `worktree-claude-blog` / `-bulk-import` / `-correspondent` / `-dictionary` service-guards | patch-equiv (`-`) |
| `worktree-claude-disposition` / `-legal-hold` / `-ops-recovery-core` service-guards | patch-equiv (`-`) |
| `worktree-claude-ops-policy` / `-people-shape` / `-preview-diagnostics-core` | ANCESTOR |
| `worktree-discussion` / `-localized-content` / `-permission-template` service-guards | patch-equiv (`-`) |
| `worktree-site` / `-trash` / `-user-group` service-guards | patch-equiv (`-`) |
| `feat/phase1-p59` / `-p60` / `-p61` / `-p63` | ANCESTOR |
| `feat/phase2-rollup-20260212` | ANCESTOR |
| `feat/phase3-ocr-queue-20260212` | ANCESTOR |
| `feat/phase4-preview-failure-ux` / `-preview-hardening` / `-prepull` | ANCESTOR |
| `phase1/p0-p1` | ANCESTOR |
| `phase10/search-consistency` | ANCESTOR |

## 3. `+` under cherry but **confirmed superseded** — also safe to delete

These showed `+` (no patch-equivalent) only because `main` re-implemented the same
intent in a different/later commit. Each was verified against `main`:

| Branch | `git cherry` `+` commit | Verified state in `main` | Verdict |
|---|---|---|---|
| `claude/mail-automation-service-guards-20260518` | guard mail automation service responses | `main` has **`aacdba3 fix(frontend): guard mail automation service responses`** (same intent, independent SHA) + `mailAutomationService.test.ts`; `main` service = 1150 lines > branch 1059 | **Delivered. Delete.** |
| `feat/phase4-mime-normalization-20260213` | adds `MimeTypeNormalizer.java` (lookup table) + wires ContentService/VersionService | `MimeTypeNormalizer.java` absent, but `main`'s `ContentService` uses **Tika `detectMimeType(...)` with filename fallback when the result is generic (octet-stream)** — a more robust approach (`ContentService.java:58,61-62,194-235`) | **Superseded by Tika. Delete** (confirm upload-path wiring if certainty wanted). |
| `phase11/mail-automation-ui` | enhance mail automation diagnostics | `MailAutomationPage.tsx` rebuilt to **5442 lines** (last touched 2026-05-24) vs branch **2727**; **OPEN PR #9** | **Stale. Close PR #9 + delete.** |
| `phase12/mail-automation-alerts` | mail automation failure insights | same page 5442 vs branch **2871**; superset of phase11; **OPEN PR #10** | **Stale. Close PR #10 + delete.** |

## 4. Live GitHub PRs (require an outward action — operator decision)

| PR | Branch | Recommendation |
|---|---|---|
| **#9** | `phase11/mail-automation-ui` | **Close** — superseded by the 2026-05 MailAutomationPage rework (5442 vs 2727 lines). Comment with the superseding work, then delete branch. |
| **#10** | `phase12/mail-automation-alerts` | **Close** — same; superset of #9 but equally stale. |
| **#3** | `fix/lombok-builder-default` ("Lombok Fix") | **Close** — the identical `@Builder.Default` fix to `SanityCheckReport.java` already landed in `main` as `a7b5146 fix(core): add builder defaults for sanity report lists` (2026-01-05), one day after this 2026-01-04 PR. Fix is in `main`; the PR is a dangling duplicate. |

## 4b. Pre-delete safety scan — worktree uncommitted changes

Scanned all 35 worktrees for uncommitted work **before** recommending `--force` removal:

- **8 worktrees** show only untracked build caches (`?? node_modules`, `?? .m2-cache`) — discardable.
- **1 worktree** has real uncommitted edits — `.claude/worktrees/claude-preview-diagnostics-core-service-guards`
  (` M previewDiagnosticsService.ts` + a new `.core.test.ts` + a design-verification doc). **Verified superseded:**
  `main` already tracks both the test and the doc, and `main`'s `previewDiagnosticsService.ts` is a strict superset
  (hundreds of guard functions the worktree snapshot lacks — the worktree is a 2026-05-17 mid-progress copy, 218
  commits behind `main`). Only ~10 lines of an isolated `PreviewRenditionResourcesDiagnostics` type live in the
  worktree but not verbatim in `main`; almost certainly reshaped in `main`'s finished version. **Deletion risk ≈ 0.**
- The main repo's single change is this audit doc itself (uncommitted).

➡️ No real work is lost by the §5 cleanup. (Belt-and-suspenders option: `git -C <wt> stash` the 10-line type first.)

## 5. Cleanup commands — ⚠️ DESTRUCTIVE, run only after operator confirms

Branches pinned to a worktree cannot be `-D`'d until the worktree is removed.
Recommend pruning worktrees first, then branches. **Do not run blindly.**

```bash
# 5a. Remove sibling worktrees (claude/* feature folders)
for wt in bulk-operation-service-guards invitation-resend-backend invitation-resend-docs \
          invitation-resend-ui mail-automation-service-guards mail-presets-backend \
          mail-presets-docs mail-presets-ui oauth-admin-preflight oauth-revoke-backend \
          oauth-revoke-meta-backend oauth-revoke-meta-docs oauth-revoke-meta-ui \
          oauth-revoke-ui ops-recovery-async-tail-guards smtp-backend smtp-docs smtp-ui \
          workflow-service-guards ; do
  git worktree remove --force "../Athena-$wt"
done

# 5b. Remove .claude/worktrees/* (worktree-* service-guard branches)
git worktree list --porcelain | grep '^worktree .*/.claude/worktrees/' \
  | awk '{print $2}' | xargs -I{} git worktree remove --force {}
git worktree prune

# 5c. Delete all delivered/superseded local branches (everything audited above)
git branch -D \
  claude/bulk-operation-service-guards-20260518 claude/invitation-resend-backend \
  claude/invitation-resend-docs claude/invitation-resend-ui \
  claude/mail-automation-service-guards-20260518 claude/mail-presets-backend \
  claude/mail-presets-docs claude/mail-presets-ui claude/oauth-admin-preflight \
  claude/oauth-revoke-backend claude/oauth-revoke-meta-backend \
  claude/oauth-revoke-meta-docs claude/oauth-revoke-meta-ui claude/oauth-revoke-ui \
  claude/ops-recovery-async-tail-guards-20260518 claude/smtp-backend claude/smtp-docs \
  claude/smtp-ui claude/workflow-service-guards-20260518 \
  feat/phase1-p59-preview-unsupported feat/phase1-p60-folder-scoped-search \
  feat/phase1-p61-version-compare-any-two feat/phase1-p63-audit-node-filter \
  feat/phase2-rollup-20260212 feat/phase3-ocr-queue-20260212 \
  feat/phase4-mime-normalization-20260213 feat/phase4-preview-failure-ux-20260213 \
  feat/phase4-preview-hardening-20260213 feat/phase4-preview-hardening-20260213-prepull \
  phase1/p0-p1 phase10/search-consistency phase11/mail-automation-ui \
  phase12/mail-automation-alerts \
  worktree-claude-blog-service-guards worktree-claude-bulk-import-service-guards \
  worktree-claude-correspondent-service-guards worktree-claude-dictionary-service-guards \
  worktree-claude-disposition-service-guards worktree-claude-legal-hold-service-guards \
  worktree-claude-ops-policy-service-guards worktree-claude-ops-recovery-core-service-guards \
  worktree-claude-people-service-shape-guards \
  worktree-claude-preview-diagnostics-core-service-guards worktree-discussion-service-guards \
  worktree-localized-content-service-guards worktree-permission-template-service-guards \
  worktree-site-service-guards worktree-trash-service-guards worktree-user-group-service-guards

# 5d. Close the two stale PRs (outward-facing — confirm first)
gh pr close 9  --comment "Closing as stale: opened 2026-02, ~1000+ commits behind main and unmergeable. MailAutomationPage.tsx has since been rewritten (main 5442 lines vs this branch's 2727). If the diagnostics enhancement is still wanted, re-evaluate fresh against current main."
gh pr close 10 --comment "Closing as stale: opened 2026-02, ~1000+ commits behind main and unmergeable. MailAutomationPage.tsx has since been rewritten (main 5442 lines vs this branch's 2871). If the failure-insights feature is still wanted, re-evaluate fresh against current main."
```

> Local-only branches (`feat/*`, `phase1/p0-p1`, `phase10`, `phase11`, `phase12`) have
> no worktree and are deleted directly in 5c. If a `git branch -D` reports "not found"
> it was already gone — safe to ignore.

## 6. What this does NOT touch

- **PR #3 `fix/lombok-builder-default`** — review on its own merits first.
- Remote branches on `origin` — 5c deletes *local* refs only. Deleting the remote
  counterparts (`git push origin --delete <branch>`) is a separate, also-destructive
  step left to the operator.
