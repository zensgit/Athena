# Phase 357 - Alfresco Workflow Activity Filter UI Parity

## Goal

把 workflow `Process Activity Timeline` 的服务端筛选能力真正补齐到两个主工作台，避免出现“后端已支持 `assignee/activityType`，但前端只能输全文 query”的能力断层。

## Scope

- `TasksPage`
  - `Process Activity Timeline` 增加 `Assignee`
  - `Process Activity Timeline` 增加 `Activity type`
- `WorkflowProcessesPage`
  - `Process Activity Timeline` 增加 `Assignee`
  - `Process Activity Timeline` 增加 `Activity type`
- 两个页面都把以上字段透传到已有的 `getProcessActivities` contract
- 保留现有 `All / Running / Ended / Human / System` quick scopes 为前端本地细筛

## Design

Phase 356 已经补了 backend contract：

- `query`
- `assignee`
- `activityType`

本阶段只补 UI parity，不再扩 backend。

### Interaction Model

- 三个 activity 过滤输入都做 `250ms debounce`
- 服务端先按：
  - `query`
  - `assignee`
  - `activityType`
  做粗筛
- 前端 quick scopes 再对已加载 activity 集合做本地细筛

### Filter Semantics

- `query`
  - 继续兼容当前全文搜索输入
- `assignee`
  - 前后端都按精确值收敛
- `activityType`
  - 前后端都按精确值收敛

这样可以避免 UI 过渡成“模糊输入看起来能用，但服务端实际按精确匹配”的错觉。

## Files

- `ecm-frontend/src/pages/TasksPage.tsx`
- `ecm-frontend/src/pages/WorkflowProcessesPage.tsx`

## Result

Athena 的 workflow activity triage 现在在 task workbench 和 process browser 两个入口都具备一致的 server-filter UI，用户可以按全文、assignee、activity type 收缩 activity 数据，再用 quick scopes 做轻量排障。
