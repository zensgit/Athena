# Phase 348 - Alfresco Workflow History And Variable Quick Scopes

## Goal

把 workflow workbench 从“可搜索”推进到“可分层筛查”，让用户在任务页和流程页里都能更快定位目标历史记录和变量集合。

## Scope

- `TasksPage`
  - `Workflow History` 增加更细粒度 quick scopes
  - `Process Variables` 增加 type-based quick scopes
  - `Task Variables` 增加 type-based quick scopes
- `WorkflowProcessesPage`
  - `Workflow History` 增加更细粒度 quick scopes
  - `Process Variables` 增加 type-based quick scopes

## Frontend Design

### Workflow History

在原有 `All / Running / Ended / Approved / Rejected` 的基础上，补两个更贴近排障/审计场景的 scope：

- `Commented`
  - 匹配 `startComment` 或 `comment`
- `Reviewed`
  - 匹配 `reviewedBy` 或 `reviewedAt`

原则：

- 继续走本地过滤，不新增 backend query contract
- quick scopes 与文本 filter 做 AND 叠加
- chips 直接显示当前 history 集合里的本地计数

### Variables

变量区块统一补 `type-based quick scopes`，避免只靠自由文本搜索：

- `All`
- `String`
- `Number`
- `Boolean`
- `Structured`
- `Other`

分类规则基于已有 `variable.type` 做轻量归类，不增加后端字段。

交互原则：

- quick scopes 与现有文本 filter 叠加
- 任务页与流程页保持一致
- 有数据但当前 scope 无匹配时，继续显示明确 empty-state

## Files

- `ecm-frontend/src/pages/TasksPage.tsx`
- `ecm-frontend/src/pages/WorkflowProcessesPage.tsx`

## Result

Athena 的 workflow workbench 现在更接近面向运营/排障的工作台，而不只是单纯的列表与搜索页。
