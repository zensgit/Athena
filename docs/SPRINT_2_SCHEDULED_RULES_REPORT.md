# Sprint 2 开发报告：定时规则 (Scheduled Rules) + 执行可观测

**日期**：2025-12-18
**状态**：已完成

> 说明：本报告对应 `docs/CLAUDE_EXECUTION_PLAN.md` 中定义的 Sprint 2（Scheduled Rules），
> 与项目早期 `docs/SPRINT_2_COMPLETION_REPORT.md`（工作流引擎集成）属于不同阶段/不同主题。

---

## 概述

Sprint 2 实现了定时规则（Scheduled Rules）功能，支持基于 cron 表达式的定时规则执行，并提供执行状态可观测性。

---

## 2.1 数据模型扩展

### 数据库迁移

**文件**：`ecm-core/src/main/resources/db/changelog/changes/013-add-scheduled-rule-columns.xml`

本次迁移新增字段（仅针对 “定时规则”）：
| 字段 | 类型 | 描述 |
|------|------|------|
| `cron_expression` | VARCHAR(100) | Cron 表达式（如 `0 0 * * * *`）|
| `timezone` | VARCHAR(50) | 时区（默认 UTC）|
| `last_run_at` | TIMESTAMP | 上次执行时间 |
| `next_run_at` | TIMESTAMP | 下次执行时间 |
| `max_items_per_run` | INT | 每次执行最大处理文档数（默认 200）|

说明：
- `scope_folder_id` / `scope_mime_types` 已在 `automation_rules` 基础模型中存在（用于规则作用域限制），不属于本次迁移新增字段。

### 索引优化

迁移中创建的索引为普通复合索引（未做 partial index）：

- `idx_rules_scheduled_next_run(enabled, trigger_type, next_run_at)`

---

## 2.2 后端实现

### ScheduledRuleRunner.java

**文件**：`ecm-core/src/main/java/com/ecm/core/service/ScheduledRuleRunner.java`

核心功能：
1. **定时轮询**：`@Scheduled(fixedDelayString = "${ecm.rules.scheduled.poll-interval-ms:60000}")`
2. **查询到期规则**：`ruleRepository.findScheduledRulesDue(now)`
3. **执行规则**：对符合条件的文档执行规则动作
4. **更新下次执行时间**：使用 Spring `CronExpression` 计算

```java
@Scheduled(fixedDelayString = "${ecm.rules.scheduled.poll-interval-ms:60000}")
@Transactional
public void runScheduledRules() {
    if (!rulesEnabled || !scheduledRulesEnabled) {
        return;
    }

    LocalDateTime now = LocalDateTime.now();
    List<AutomationRule> dueRules = ruleRepository.findScheduledRulesDue(now);

    for (AutomationRule rule : dueRules) {
        try {
            executeScheduledRule(rule);
        } catch (Exception e) {
            log.error("Failed to execute scheduled rule '{}': {}",
                rule.getName(), e.getMessage());
            rule.incrementFailureCount();
            updateNextRunTime(rule);
        }
    }
}
```

### 候选文档选择逻辑

```java
private void executeScheduledRule(AutomationRule rule) {
    LocalDateTime since = rule.getLastRunAt();
    if (since == null) {
        // 首次运行：处理过去 24 小时的文档
        since = LocalDateTime.now().minusHours(24);
    }

    int maxItems = rule.getMaxItemsPerRun() != null ? rule.getMaxItemsPerRun() : 200;

    Page<Document> candidateDocuments;
    if (rule.getScopeFolderId() != null) {
        candidateDocuments = documentRepository.findModifiedSinceInFolder(
            since, rule.getScopeFolderId(), pageRequest);
    } else {
        candidateDocuments = documentRepository.findModifiedSince(since, pageRequest);
    }

    // 逐个文档执行规则...
}
```

### Cron 表达式验证 API

后端提供：
- `POST /api/v1/rules/validate-cron`：验证 cron 并返回未来 5 次执行时间（JSON body）
- `POST /api/v1/rules/{ruleId}/trigger`：手动触发一次 scheduled rule（便于测试，不必等待 poll）

---

## 2.3 实体扩展

### AutomationRule.java

**文件**：`ecm-core/src/main/java/com/ecm/core/entity/AutomationRule.java`

与定时规则相关的字段：

```java
@Column(name = "cron_expression", length = 100)
private String cronExpression;

@Column(name = "timezone", length = 50)
private String timezone = "UTC";

@Column(name = "last_run_at")
private LocalDateTime lastRunAt;

@Column(name = "next_run_at")
private LocalDateTime nextRunAt;

@Column(name = "max_items_per_run")
private Integer maxItemsPerRun = 200;

// 辅助方法
public boolean isScheduledRule() {
    return triggerType == TriggerType.SCHEDULED
        && cronExpression != null
        && !cronExpression.isBlank();
}

public boolean isMimeTypeInScope(String mimeType) {
    // 支持精确匹配与通配：image/*（以 prefix 匹配）
}
```

说明：
- `scopeMimeTypes` 目前采用 “逗号分隔 + 精确/`type/*` 通配” 匹配（不是正则）。

---

## 2.4 Repository 扩展

### AutomationRuleRepository.java

**文件**：`ecm-core/src/main/java/com/ecm/core/repository/AutomationRuleRepository.java`

新增查询方法：

```java
@Query("SELECT r FROM AutomationRule r " +
       "WHERE r.triggerType = 'SCHEDULED' " +
       "AND r.enabled = true " +
       "AND r.deleted = false " +
       "AND r.cronExpression IS NOT NULL " +
       "AND (r.nextRunAt IS NULL OR r.nextRunAt <= :now) " +
       "ORDER BY r.priority ASC")
List<AutomationRule> findScheduledRulesDue(@Param("now") LocalDateTime now);

@Modifying
@Query("UPDATE AutomationRule r " +
       "SET r.lastRunAt = :lastRun, r.nextRunAt = :nextRun " +
       "WHERE r.id = :id")
int updateScheduledRunTimes(
    @Param("id") UUID id,
    @Param("lastRun") LocalDateTime lastRun,
    @Param("nextRun") LocalDateTime nextRun);
```

---

## 2.5 配置项

本功能使用以下配置项（均有默认值，可选覆盖）：

```yaml
ecm:
  rules:
    enabled: true
    scheduled:
      enabled: true
      poll-interval-ms: 60000  # 轮询间隔（毫秒）
```

说明：
- 当前仓库默认未在 `application.yml` 显式声明 `ecm.rules.*`，运行时使用 `@Value` 的默认值；
- 可通过 `application.yml` / `application-docker.yml` 或环境变量覆盖。

---

## 2.6 前端支持

### Rules 页面 (RulesPage.tsx)

当 `triggerType = SCHEDULED` 时：
- 显示 Cron 表达式输入框
- 显示时区下拉选择
- 显示 `nextRunAt`（只读；初始可能为 `null`，在首次执行并更新后可见）
- 提供 Cron 预设（每小时、每天、每周）

### Cron 预设示例

| 名称 | Cron 表达式 |
|------|------------|
| 每小时 | `0 0 * * * *` |
| 每天凌晨 2 点 | `0 0 2 * * *` |
| 每周一上午 9 点 | `0 0 9 * * MON` |

---

## 2.7 执行流程图

```
┌─────────────────────────────────────────────────────────────┐
│                    ScheduledRuleRunner                       │
│                                                             │
│   @Scheduled(fixedDelay=60s)                               │
│   ┌─────────────────────────────────────────────────────┐   │
│   │ 1. 查询到期规则 (nextRunAt <= now)                  │   │
│   │ 2. 对每个规则：                                      │   │
│   │    a. 查询候选文档 (modifiedSince > lastRunAt)      │   │
│   │    b. 过滤 MIME 类型                                 │   │
│   │    c. 执行规则动作                                   │   │
│   │    d. 记录日志/更新统计（executionCount/failureCount）│   │
│   │ 3. 更新 lastRunAt / nextRunAt                       │   │
│   └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

---

## 2.8 验收测试

### API Smoke Test

```bash
# 创建定时规则（示例：手动触发，不必等待 poll）
curl -X POST "http://localhost:7700/api/v1/rules" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Daily Auto Tag",
    "description": "created by sprint-2 report",
    "triggerType": "SCHEDULED",
    "cronExpression": "0 0 2 * * *",
    "timezone": "Asia/Shanghai",
    "maxItemsPerRun": 100,
    "condition": {"type": "ALWAYS_TRUE"},
    "actions": [{"type": "ADD_TAG", "params": {"tagName": "processed"}}]
  }'

# 手动触发执行一次
curl -X POST -H "Authorization: Bearer $TOKEN" \
  "http://localhost:7700/api/v1/rules/{ruleId}/trigger"

# 验证 cron 表达式（POST JSON）
curl -X POST "http://localhost:7700/api/v1/rules/validate-cron" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"cronExpression":"0 0 2 * * *","timezone":"UTC"}'
```

---

## 文件清单

| 文件 | 变更类型 | 描述 |
|------|---------|------|
| `ecm-core/.../entity/AutomationRule.java` | 修改 | 添加定时规则字段 |
| `ecm-core/.../repository/AutomationRuleRepository.java` | 修改 | 添加定时规则查询 |
| `ecm-core/.../service/ScheduledRuleRunner.java` | 新增 | 定时规则执行器 |
| `ecm-core/.../db/changelog/changes/013-add-scheduled-rule-columns.xml` | 新增 | 数据库迁移 |
| `ecm-frontend/src/pages/RulesPage.tsx` | 修改 | 支持 cron 配置 |

---

## 下一步

- 补齐回归覆盖（对齐 `docs/CLAUDE_EXECUTION_PLAN.md` 验收要求）：
  - `scripts/smoke.sh`：增加 scheduled rule 创建→触发→验 tag→清理
  - `ecm-frontend/e2e/`：增加 scheduled rule E2E 用例
  - 更新 `docs/RERUN_REPORT.md`（附日志）
