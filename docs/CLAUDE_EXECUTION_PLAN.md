# Athena ECM 下一阶段详细执行计划（给 Claude 实施 / GPT 复核）

日期：2025-12-18  
目标：由 Claude 按步骤实现；实现后我负责在本仓库环境中复跑验证并更新 `docs/RERUN_REPORT.md`。

---

## 0. 基线与前置条件（必须先通过）

### 0.1 环境端口约定（本地 Docker）
- UI：`http://localhost:5500`
- API：`http://localhost:7700`
- Keycloak：`http://localhost:8180`

### 0.2 一键回归入口（用于每个 PR / Sprint 的 Done）
```bash
bash scripts/restart-ecm.sh
bash scripts/keycloak/create-test-users.sh
bash scripts/get-token.sh admin admin

ECM_UPLOAD_FILE="/Users/huazhou/Downloads/J0924032-02上罐体组件v2-模型.pdf" \
  ECM_API=http://localhost:7700 \
  ECM_TOKEN_FILE=tmp/admin.access_token \
  bash scripts/smoke.sh

cd ecm-frontend
CI=true npm test -- --watchAll=false
npm run build
npm run e2e
```

### 0.3 现有关键修复点（不要回退）
- Keycloak 登录回跳/URL 闪跳修复：`ecm-frontend/src/components/auth/PrivateRoute.tsx`
- Keycloak init 完成时清理 login flag：`ecm-frontend/src/index.tsx`
- 前端 Keycloak 默认 clientId：`ecm-frontend/src/auth/keycloak.ts`（默认 `unified-portal`）

---

## 1. Sprint 1（优先级 P0）：规则引擎真正“自动化”闭环

> 现状：规则管理 UI/API 已有，但**规则未与真实业务事件可靠联动**（主要依赖手工 `/rules/{id}/test`）。  
> 目标：上传/改名/移动/打标签/新版本/评论 这些真实操作能自动触发规则，并可在 UI/日志看到执行结果。

### 1.1 后端：把规则触发挂到真实业务链路（不依赖异步事件线程）

原则：**在请求线程内触发**（有 SecurityContext），避免 Async listener 丢失认证导致写操作失败。

#### A) DOCUMENT_CREATED（上传）
- 落点：在 Pipeline 末端（建议在 `InitialVersionProcessor` 或 `EventPublishingProcessor` 之后新增 `RuleTriggerProcessor`）
  - 文件：`ecm-core/src/main/java/com/ecm/core/pipeline/processor/RuleTriggerProcessor.java`（新增）
  - Order：建议 `470`（在 `MLClassificationProcessor(460)`/`AutoMatchingProcessor(450)` 后、`SearchIndexProcessor(500)` 前）
  - 行为：
    - 如果 `context.getDocument()` 为 `Document` 且 rule engine enabled：
      - `ruleEngineService.evaluateAndExecute(document, TriggerType.DOCUMENT_CREATED)`
    - 捕获异常：不要中断上传（记录到 context errors + AuditLog）

#### B) DOCUMENT_UPDATED（改名/编辑属性）
- 落点：`NodeService.updateNode(...)` 保存成功后触发
  - 文件：`ecm-core/src/main/java/com/ecm/core/service/NodeService.java`
  - 触发条件：仅对 `Document`（nodeType=DOCUMENT）触发

#### C) DOCUMENT_MOVED（移动）
- 落点：`NodeService.moveNode(...)` 成功后触发
  - 文件：`ecm-core/src/main/java/com/ecm/core/service/NodeService.java`
  - 触发条件：仅 Document

#### D) DOCUMENT_TAGGED（标签增删）
- 落点：`TagService.addTagToNode/removeTagFromNode` 成功后触发
  - 文件：`ecm-core/src/main/java/com/ecm/core/service/TagService.java`
  - 触发条件：仅 Document

#### E) DOCUMENT_CATEGORIZED（分类变更）
- 落点：分类绑定/解绑处（按当前实现选择一个中心点）
  - 推荐：在 `NodeService.updateNode(...)` 处理 categories 变更后触发
  - 或：在 `CategoryService.assign/remove`（若存在）触发

#### F) VERSION_CREATED（新版本）
- 落点：`VersionService.createNewVersion(...)` 成功后触发
  - 文件：`ecm-core/src/main/java/com/ecm/core/service/VersionService.java`

#### G) COMMENT_ADDED（评论）
- 落点：`CommentService.addComment(...)` 成功后触发
  - 文件：`ecm-core/src/main/java/com/ecm/core/service/CommentService.java`

### 1.2 后端：START_WORKFLOW 动作落地（不再只是 log）

现状：`RuleEngineService.executeStartWorkflow` 仅打印日志。  
目标：规则可直接启动 Flowable 流程（至少支持 `documentApproval`）。

实现建议：
- 在 `RuleEngineService.executeStartWorkflow(...)`：
  - 支持两种方式：
    1) `workflowKey=documentApproval` 且 params 包含 `approvers`（list/string） -> 调 `WorkflowService.startDocumentApproval(documentId, approvers, comment)`
    2) 其它 `workflowKey` -> 调 `runtimeService.startProcessInstanceByKey(workflowKey, documentId.toString(), variables)`
- 默认 variables 建议注入：
  - `documentId/documentName/initiator`（来自 `SecurityService.getCurrentUser()`）
- 错误处理：
  - 规则动作失败要写入 RuleExecutionResult（已有结构），并在 stats 中累积 failureCount

涉及文件（建议）：
- `ecm-core/src/main/java/com/ecm/core/service/RuleEngineService.java`
- `ecm-core/src/main/java/com/ecm/core/service/WorkflowService.java`（如需暴露更通用的 start 方法）

### 1.3 前端：Rules 页面增强（非必需，但建议）

目标：把最常用的规则配置从“JSON 文本框”升级成“表单 + 可视化模板”（最小可交付）。

最小增强项：
- 在 `RulesPage.tsx` 新增两个“动作模板按钮”
  - “上传后自动打标签”：触发=DOCUMENT_CREATED，动作=ADD_TAG
  - “上传后自动发起审批”：触发=DOCUMENT_CREATED，动作=START_WORKFLOW（workflowKey=documentApproval + approvers）

文件：
- `ecm-frontend/src/pages/RulesPage.tsx`
- `ecm-frontend/src/services/ruleService.ts`（如需扩展 DTO）

### 1.4 自动化回归（必须）

#### 更新 `scripts/smoke.sh`（新增规则触发验证）
- 新增步骤：
  1) 创建一条规则：DOCUMENT_CREATED + ADD_TAG(tagName=smoke-auto)
  2) 上传测试文档
  3) 调 `GET /api/v1/nodes/{id}` 或 `GET /api/v1/nodes/{id}/tags` 验证 tag 已自动添加
  4) 清理规则与测试文档

#### 更新 Playwright E2E（新增 1 条用例即可）
- 用 admin 登录
- 创建规则（可走 UI JSON 方式）
- 上传文件
- 打开 Properties/Tags（或行内 Tags）确认 tag 出现

验收标准（Sprint 1 Done）：
- 规则可在“真实上传”场景自动执行（不用手工 test）
- smoke + e2e 复跑全绿
- `docs/RERUN_REPORT.md` 更新最新日志

---

## 2. Sprint 2（优先级 P1）：Scheduled Rules（定时规则）+ 执行可观测

> 目标：支持 cron 定时规则，并能看到下一次运行时间/执行记录。

### 2.1 数据模型与迁移

- 给 `automation_rules` 增加字段（Liquibase）：
  - `cron_expression`（varchar, nullable）
  - `timezone`（varchar, default `UTC`）
  - `last_run_at`（timestamp）
  - `next_run_at`（timestamp）
  - `max_items_per_run`（int, default 200）
- 新增索引：`idx_rules_scheduled_next_run`（enabled + triggerType + next_run_at）

### 2.2 后端：定时执行器

- 新增 `ScheduledRuleRunner`（`@Scheduled(fixedDelay=... )`）
  - 查询：所有 `enabled=true AND triggerType=SCHEDULED AND nextRunAt <= now()`
  - 选取候选文档集合（最小实现建议）：
    - 仅处理 `lastModifiedDate > lastRunAt` 的文档，且满足 scopeFolderId（可先只支持 folder 过滤）
    - 限制 maxItemsPerRun，避免全库扫爆
  - 对每个候选文档执行：`evaluateAndExecute(doc, TriggerType.SCHEDULED)`
  - 更新 `lastRunAt/nextRunAt`（用 Spring `CronExpression` 计算）

### 2.3 前端：Rules UI 支持 cron 字段

- 当 triggerType=SCHEDULED：
  - 展示 cron 输入框 + 时区下拉
  - 显示 nextRunAt（只读）
  - 提供 cron presets（每小时/每天/每周）

### 2.4 验收与回归

- smoke 增加：
  - 创建一个 scheduled rule（每分钟），动作=ADD_TAG
  - 上传文件 -> 等待下一次运行 -> 校验 tag 被打上（允许重试/等待）
- e2e 增加 1 条用例（允许较长 timeout）

---

## 3. Sprint 3（优先级 P1）：安全与合规（优先走 Keycloak 原生能力）

### 3.1 MFA（OTP/TOTP）

推荐方案：**Keycloak 原生 OTP Required Action**，避免在业务代码里重复实现。

- 修改 `keycloak/realm-export.json`（可导入即可生效）
  - 启用 OTP policy（或 Required Action）
  - 可选：对 `admin`/`editor` 强制 OTP
- UI：`SettingsPage` 增加 MFA 指引
  - 展示“去 Keycloak Account 设置 OTP”的链接（可带 realm）
  - 展示当前 token 中的 `amr`/`acr`（若有）

### 3.2 审计导出 + 保留策略

后端：
- 增加审计导出接口（admin only）
  - `GET /api/v1/analytics/audit/export?format=csv&from=...&to=...`
- 增加保留策略（`ecm.audit.retention-days`）
  - `@Scheduled` 清理超期日志
- 记录规则执行/病毒扫描拒绝等关键事件到审计表

前端：
- Admin Dashboard 增加 “Export audit log” 按钮（下载 CSV）

验收：
- 可导出审计；可配置 retention 并可验证删除生效（在测试环境）

---

## 4. Sprint 4（优先级 P2）：内容安全（病毒扫描）+ 加密存储（分阶段）

### 4.1 病毒扫描（ClamAV）

docker-compose：
- 增加 `clamav` 服务（`clamd` 3310）
- ecm-core 配置：
  - `ecm.security.antivirus.enabled=true/false`
  - `ecm.security.antivirus.clamd-host/clamd-port`

后端：
- 新增 `AntivirusService` + `ClamAvClient`
- Pipeline 增加 `VirusScanProcessor`（建议 order=150，基于 `contentId` 从 storage 读取再扫描）
  - 命中病毒：
    - 删除已写入的 content（contentService.delete）
    - 终止 pipeline（fatal）
    - 写审计：`VIRUS_DETECTED`

回归：
- 增加 EICAR 测试文件用例（仅在本地/开发，不进入正式数据）

### 4.2 加密存储（建议先做配置与文档）

阶段 1（低风险）：
- 通过 MinIO SSE 或宿主机磁盘加密（文档 + 配置）

阶段 2（高投入）：
- 应用层 envelope encryption（AES-GCM）
- key rotation/KMS（以后再做）

---

## 5. Claude 提交要求（便于我复核）

每个 PR/Sprint：
- 必须更新 `docs/RERUN_REPORT.md`（附最新日志路径）
- 必须保持：`scripts/smoke.sh` + `ecm-frontend/e2e/` 至少其一覆盖新增功能
- 不提交任何真实 token/密钥（只提交脚本与说明）

