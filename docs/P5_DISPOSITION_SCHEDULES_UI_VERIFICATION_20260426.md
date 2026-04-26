# Disposition Schedules Admin UI — Gap #8 Frontend Closeout (design + verification)

## Date
2026-04-26

## Status
Frontend implementation complete. Closes the frontend gap for Gap #8 (Full Disposition Schedules).
Backend was already complete (`DispositionScheduleController`, `DispositionScheduleService`,
`DispositionScheduler`).

---

## Scope

Provide an admin UI for viewing and managing disposition schedules that control automatic
cutoff, archival, and destruction of records in file-plan folders.

---

## Files added / modified

| File | Change |
|------|--------|
| `ecm-frontend/src/services/dispositionScheduleService.ts` | new — typed API client |
| `ecm-frontend/src/pages/DispositionSchedulesPage.tsx` | new — admin page (~700 lines) |
| `ecm-frontend/src/App.tsx` | + route `/admin/disposition-schedules` (ROLE_ADMIN) |
| `ecm-frontend/src/components/layout/MainLayout.tsx` | + nav entry "Disposition Schedules" |

---

## API client — `dispositionScheduleService.ts`

Class-instance singleton following the `legalHoldService` pattern.

```typescript
// Exported types
DispositionScheduleDto {
  id, folderId, folderName, folderPath,
  enabled, includeSubfolders,
  cutoffAfterDays, archiveAfterCutoffDays, destroyAfterArchiveDays,
  archiveStorageTier, maxCandidatesPerAction,
  lastDryRunAt, lastExecutedAt, lastError
}

DispositionScheduleUpsertRequest {
  enabled?, includeSubfolders?,
  cutoffAfterDays?, archiveAfterCutoffDays?, destroyAfterArchiveDays?,
  archiveStorageTier?, maxCandidatesPerAction?
}

DispositionCandidateDto { nodeId, name, nodeType, path, actionType, eligibleAt, blockedByHoldNames }
DispositionDryRunDto { folderId, folderName, includeSubfolders, archiveStorageTier,
                       maxCandidatesPerAction, cutoffCount, archiveCount, destroyCount, candidates }
DispositionExecutionDto { folderId, folderName, cutoffCount, archiveCandidateCount,
                          archivedNodeCount, destroyCandidateCount, destroyedNodeCount,
                          failureCount, blockedCount, failures, error }
DispositionBatchExecutionDto { executedSchedules, cutoffCount, archivedNodeCount,
                               destroyedNodeCount, blockedCount, failureCount, results }
DispositionActionExecutionDto { id, actionType, status, nodeId, nodeName, nodeType, nodePath,
                                affectedNodeCount, details, actor, executedAt }
DispositionPage<T> { content, totalElements, totalPages, number, size }
```

Eight methods:
- `listSchedules()` → `GET /api/v1/disposition-schedules`
- `getSchedule(folderId)` → `GET /api/v1/folders/{folderId}/disposition-schedule`
- `upsertSchedule(folderId, data)` → `PUT /api/v1/folders/{folderId}/disposition-schedule`
- `deleteSchedule(folderId)` → `DELETE /api/v1/folders/{folderId}/disposition-schedule` (void)
- `dryRun(folderId, data)` → `POST /api/v1/folders/{folderId}/disposition-schedule/dry-run`
- `execute(folderId)` → `POST /api/v1/folders/{folderId}/disposition-schedule/execute`
- `listExecutions(folderId, page?, size?)` → `GET /api/v1/folders/{folderId}/disposition-schedule/executions`
- `runAll()` → `POST /api/v1/disposition-schedules/run`

---

## Page design — `DispositionSchedulesPage.tsx`

Route: `/admin/disposition-schedules`

### Layout
Master-detail Grid (4/8): left schedule list, right detail panel.

**Left panel:**
- Each row: folder name, folder path (caption), enabled chip (ENABLED=success/green, DISABLED=default/grey)
- Note caption: "Lists schedules attached to records folders"
- "Add Schedule" button in panel header

**Right panel (schedule selected):**
- Folder name + path header
- Settings grid: enabled, includeSubfolders, cutoffAfterDays, archiveAfterCutoffDays,
  destroyAfterArchiveDays, archiveStorageTier, maxCandidatesPerAction
- Metadata: lastDryRunAt, lastExecutedAt, lastError (shown as Alert when present)
- Action buttons: Edit Schedule, Dry Run, Execute Now (requires confirmation), Delete Schedule
- Execution history: paginated MUI Table (10/page), columns: actionType chip, status chip,
  node name, affected count, actor, executedAt

**Page header:**
- "Run All Schedules" button → `DispositionBatchExecutionDto` stats dialog

### Sub-components (embedded)

| Component | Purpose |
|-----------|---------|
| `AddScheduleDialog` | Folder UUID text field + full schedule settings form |
| `EditScheduleDialog` | Pre-populated schedule settings form |
| `ScheduleFormFields` | Shared form fields component (eliminates duplication) |
| `DryRunResultDialog` | Summary chips + candidates table (actionType/blockedByHoldNames) |
| `ExecuteResultDialog` | Summary stats + failures list |
| `ConfirmExecuteDialog` | Warning Alert before destructive execute |
| `RunAllResultDialog` | Batch totals + per-schedule results |

### Key design decisions

1. **`archiveStorageTier` sends explicit `null` when "None" selected** — avoids upsert ambiguity
2. **"Execute Now" gated behind confirmation dialog** — destructive action, cannot be undone
3. **After execute**: schedule re-fetched via `getSchedule` to refresh `lastExecutedAt`/`lastError`
4. **Folder ID input for "Add Schedule"**: UUID text field — matches admin-tool pattern; natural
   since only RECORDS_FOLDER nodes are valid targets (backend validates)

---

## Routing and navigation

Route: `/admin/disposition-schedules` — `ROLE_ADMIN` required.

Nav: "Disposition Schedules" added after "Legal Holds" in admin section, using `Schedule` icon
from `@mui/icons-material`.

---

## TypeScript verification

```
cd ecm-frontend && npx tsc --noEmit 2>&1 | grep -v node_modules
# → (no output — zero source-file errors)
```

---

## Backend API reference (already shipped)

| Endpoint | Auth | Description |
|----------|------|-------------|
| `GET /api/v1/disposition-schedules` | ROLE_ADMIN | List all schedules (file-plan folders only) |
| `GET /api/v1/folders/{folderId}/disposition-schedule` | ROLE_ADMIN | Get folder schedule |
| `PUT /api/v1/folders/{folderId}/disposition-schedule` | ROLE_ADMIN | Create or update schedule |
| `DELETE /api/v1/folders/{folderId}/disposition-schedule` | ROLE_ADMIN | Delete schedule |
| `POST /api/v1/folders/{folderId}/disposition-schedule/dry-run` | ROLE_ADMIN | Preview candidates |
| `POST /api/v1/folders/{folderId}/disposition-schedule/execute` | ROLE_ADMIN | Execute actions |
| `GET /api/v1/folders/{folderId}/disposition-schedule/executions` | ROLE_ADMIN | Execution history |
| `POST /api/v1/disposition-schedules/run` | ROLE_ADMIN | Run all enabled schedules |

---

## Non-goals (deferred)

- Inline folder picker (UUID text field is sufficient for admin use)
- Real-time execution progress / SSE stream
- Schedule creation from within the folder browser / properties dialog
