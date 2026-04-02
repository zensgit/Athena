# Phase 292 - Alfresco 对标：Folder Rule Set Dry-run + Reorder（Dev）

## Date
- 2026-03-13

## Goal
- 补齐 Alfresco 风格的“目录级规则集管理”能力：
  - 查看某目录下的规则集（按优先级）
  - 对目录规则进行批量重排（priority 重写）
  - 在不执行动作的前提下进行 dry-run 预演，输出可处理性与跳过原因

## Scope

### Backend API
- `GET /api/v1/rules/folders/{folderId}`
  - 返回该 scope folder 下规则（分页 + priority 排序）
- `POST /api/v1/rules/folders/{folderId}/reorder`
  - 入参：`ruleIds[]`, `basePriority`, `step`
  - 行为：按传入顺序重写 priority；未传到的规则按原有顺序自动追加
- `POST /api/v1/rules/folders/{folderId}/dry-run`
  - 入参：`triggerType`, `testData`, `limit`
  - 行为：仅评估 MIME/condition 与动作可执行性，不落地执行 action
  - 输出：`found/scanned/matched/processable/skipped/errors/skipReasons/results[]`

### Backend Implementation
- Files:
  - `ecm-core/src/main/java/com/ecm/core/repository/AutomationRuleRepository.java`
  - `ecm-core/src/main/java/com/ecm/core/service/RuleEngineService.java`
  - `ecm-core/src/main/java/com/ecm/core/controller/RuleController.java`
- 关键实现：
  - repository 新增 scope folder 查询（paged + order by priority）
  - service 新增：
    - `getRulesByScopeFolder`
    - `reorderRulesByScopeFolder`
    - `dryRunRulesByScopeFolder`
  - dry-run 统计规则：
    - `matched`: 条件命中
    - `processable`: 命中且不含不支持动作（当前 `EXECUTE_SCRIPT`）
    - `skipReasons`: `mime_out_of_scope` / `condition_not_matched` / `contains_unsupported_action` / `evaluation_error`

### Frontend
- Files:
  - `ecm-frontend/src/services/ruleService.ts`
  - `ecm-frontend/src/pages/RulesPage.tsx`
- 改动：
  - `ruleService` 新增 scope folder 相关类型与 API
  - `RulesPage` 新增 `Folder Rule Set (Dry-run & Reorder)` 面板：
    - 输入 folderId + trigger + limit + dry-run JSON
    - `Load Scoped Rules`
    - 上下移动并 `Save Order`
    - `Run Dry-run` 后展示 summary chips 与 skip reason chips

## Security
- 列表接口保持认证可见（与现有 `/api/**` 规则一致）
- 重排与 dry-run 受 `@PreAuthorize(hasAnyRole('ADMIN','EDITOR'))` 保护

## Compatibility
- 增量接口，未破坏现有 Rule CRUD 与执行链路
- 目录规则 dry-run 失败仅影响预演结果，不影响正式规则执行
