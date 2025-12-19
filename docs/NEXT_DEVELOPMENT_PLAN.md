# Athena ECM 下一阶段开发计划（建议）

日期：2025-12-18

## 当前基线（已具备/已验证）

- 环境默认端口：前端 `5500`、后端 `7700`、Keycloak `8180`、Redis `6390`
- 已通过复跑：API Smoke + Playwright E2E + 前端 Jest/Build（见 `docs/RERUN_REPORT.md`）
- 关键能力：上传/版本/WOPI 在线编辑/搜索与分面/收藏/保存搜索/分享链接/回收站/规则引擎/工作流/系统状态与监控/用户组管理

## 目标与原则

- **纵向切片**：每个 Sprint 交付“可用 + 可复跑验证”的端到端功能闭环。
- **可回归**：每个新增能力都要落在 `scripts/smoke.sh` 或 `ecm-frontend/e2e/`（至少其一）。
- **可配置**：尽量通过配置/特性开关交付（Keycloak/ECM 配置优先于硬编码）。

## 推荐路线（未来 4 个 Sprint，2 周/迭代）

### Sprint A（稳定性 + 发布准备）

**目标**：把“本地可用”固化成“一键可复跑”，降低回归成本。

- CI/自动化
  - 增加 `scripts/verify.sh`：一键 `restart → create-test-users → get-token → smoke → e2e`
  - 引入 GitHub Actions（可选）：PR 自动跑 `frontend test/build` + `backend mvn test`（有条件再跑 docker e2e）
- 文档与配置
  - 汇总统一入口：`docs/SMOKE_TEST.md`（现有）+ 补齐常见故障排查（Keycloak Web origins/Redirect URIs/端口变更）
  - 更新“功能对比/路线图”中已完成项，避免文档与实现脱节
- 质量门禁
  - 对关键接口（上传、版本、搜索、回收站、规则、workflow、WOPI）增加稳定重试/超时与更清晰错误输出

**验收**：
- 新人只需 `bash scripts/restart-ecm.sh` + `bash scripts/smoke.sh` 即可稳定得到 PASS

### Sprint B（规则引擎闭环增强：计划任务 + 工作流动作）

**目标**：让规则引擎覆盖更企业化的自动化场景（定时触发、自动启动审批）。

- Scheduled Trigger（`TriggerType.SCHEDULED`）
  - 规则新增 `schedule`（cron/interval）字段
  - Spring Scheduler/Quartz 触发规则扫描与执行
  - UI：Rules 页面增加“定时规则”配置（cron helper + next run 预览）
- `START_WORKFLOW` Action 落地
  - RuleEngine 的 `START_WORKFLOW` 动作接入现有 Flowable（调用 `WorkflowService`）
  - 动作参数：workflowKey + variables（可选）
- 执行可观测性
  - 规则执行结果列表/详情（成功/失败/耗时/错误原因）
  - 与审计日志打通（RuleExecutionResult -> AuditLog）

**验收**：
- 新建“每天 9:00 自动触发审批/或自动打标签”规则，可在 UI 与日志看到执行记录

### Sprint C（安全与合规：MFA/审计保留/导出）

**目标**：把“认证/审计”提升到更可交付的企业水平。

- MFA（推荐优先走 Keycloak 原生能力）
  - Realm 启用 OTP（TOTP）Required Action/Authentication Flow（无需业务代码）
  - ECM UI Settings 页面提示当前用户 MFA 状态（读取 token/或调用 Keycloak Admin API（可选））
- 审计日志增强
  - 审计保留策略（按天/按容量），支持一键清理过期日志
  - 审计导出（CSV/JSON，管理员权限）
- 权限模型补齐（按需）
  - “权限模板/角色模板”定义（配置化），便于项目落地时快速套用

**验收**：
- 管理员可开启 MFA；审计日志可导出；保留策略可配置并可验证生效

### Sprint D（内容安全：病毒扫描 + 加密存储）

**目标**：解决企业落地常见的“上传安全”与“数据静态保护”。

- 病毒扫描（ClamAV）
  - docker-compose 增加 `clamav` 服务
  - Pipeline 加入 `VirusScanProcessor`（发现风险：拒绝入库/隔离并记录审计）
- 加密存储（分层推进）
  - 第一阶段：优先使用存储端能力（MinIO SSE 或宿主机磁盘加密）+ 文档说明
  - 第二阶段（可选）：应用层 envelope encryption（AES-GCM + key rotation/KMS）

**验收**：
- 上传 EICAR 测试文件：被拦截并产生审计记录；正常文件不受影响

## 更长期 Backlog（按价值/复杂度排队）

- Paperless-ngx 借鉴：Inbox/Consumption（扫描入库）、Document Type、OCR 强化、自动匹配算法（ANY/ALL/REGEX/FUZZY）
- Alfresco 借鉴：模型驱动内容类型（Content Model）、权限定义/角色模板配置化、策略钩子（Policy）
- Webhook 订阅中心（外部系统集成）、事件投递可靠性（重试/死信）
- 数据治理：Retention/Legal Hold、电子签章、批量操作（批量下载 ZIP/批量标签/批量移动）

## 建议的下一步（我建议从这里开始）

1) Sprint A：补齐“一键 verify”脚本 + CI（让回归成本更低）  
2) Sprint B：Scheduled Rules + START_WORKFLOW（让规则引擎真正闭环）  
3) Sprint C：Keycloak 原生 MFA + 审计导出/保留（企业落地门槛）

