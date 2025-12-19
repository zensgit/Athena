# Sprint 3 开发报告：安全与合规 (MFA + 审计导出)

**日期**：2025-12-18
**状态**：已完成

> 说明：本报告对应 `docs/CLAUDE_EXECUTION_PLAN.md` 中定义的 Sprint 3（MFA + 审计导出/保留），
> 与项目早期 `docs/SPRINT_3_*` 文档属于不同阶段/不同主题。

---

## 概述

Sprint 3 实现了企业级安全与合规功能：
1. MFA（多因素认证）- 利用 Keycloak 原生 OTP 能力
2. 审计日志导出（CSV 格式）
3. 审计日志保留策略（自动清理过期日志）

---

## 3.1 MFA（多因素认证）

### 架构设计

采用 **Keycloak 原生 OTP Required Action** 方案，避免在业务代码中重复实现 TOTP 逻辑。

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│   ECM Frontend  │────▶│    Keycloak     │────▶│  Authenticator  │
│   SettingsPage  │     │   OTP Policy    │     │      App        │
└─────────────────┘     └─────────────────┘     └─────────────────┘
         │                      │
         │ 读取 token.amr/acr   │ 验证 TOTP
         ▼                      ▼
   ┌─────────────────────────────────────┐
   │  显示 MFA 状态 + 配置入口链接        │
   └─────────────────────────────────────┘
```

### Keycloak 配置

**文件**：`keycloak/realm-export.json`

启用 OTP 策略：
```json
{
  "otpPolicyType": "totp",
  "otpPolicyAlgorithm": "HmacSHA1",
  "otpPolicyInitialCounter": 0,
  "otpPolicyDigits": 6,
  "otpPolicyLookAheadWindow": 1,
  "otpPolicyPeriod": 30,
  "requiredActions": [
    {
      "alias": "CONFIGURE_TOTP",
      "name": "Configure OTP",
      "providerId": "CONFIGURE_TOTP",
      "enabled": true,
      "defaultAction": false
    }
  ]
}
```

### 前端 MFA 指引

**文件**：`ecm-frontend/src/pages/SettingsPage.tsx`

```typescript
// 从 token 读取 MFA 状态
const tokenParsed = (keycloak.tokenParsed || {}) as {
  amr?: string[];   // Authentication Methods Reference
  acr?: string;     // Authentication Context Class Reference
  otpConfigured?: boolean;
};

const hasMfaConfigured = tokenParsed.amr?.includes('otp') ||
                         tokenParsed.otpConfigured === true;
```

UI 功能：
- 显示当前 OTP 状态（Configured / Not Configured）
- 显示 `amr`（认证方法）和 `acr`（认证上下文）
- 提供"去 Keycloak 配置 OTP"的链接

```tsx
<Button
  variant="outlined"
  startIcon={<OpenInNewIcon />}
  href={`${env.keycloakUrl}/realms/${env.keycloakRealm}/account/#/security/signingin`}
  target="_blank"
>
  Manage MFA in Keycloak
</Button>
```

---

## 3.2 审计日志导出

### 后端 API

**文件**：`ecm-core/src/main/java/com/ecm/core/controller/AnalyticsController.java`

#### 导出接口

```java
@GetMapping("/audit/export")
@Operation(summary = "Export Audit Logs",
           description = "Export audit logs as CSV within a time range")
public ResponseEntity<byte[]> exportAuditLogs(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {

    String csvContent = analyticsService.exportAuditLogsCsv(from, to);
    String filename = String.format("audit_logs_%s_to_%s.csv",
        from.format(DateTimeFormatter.ofPattern("yyyyMMdd")),
        to.format(DateTimeFormatter.ofPattern("yyyyMMdd")));

    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + filename + "\"")
        .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
        .body(csvContent.getBytes(StandardCharsets.UTF_8));
}
```

#### 使用示例

```bash
# 导出 2025 年 12 月 1 日至 18 日的审计日志
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:7700/api/v1/analytics/audit/export?from=2025-12-01T00:00:00&to=2025-12-18T23:59:59" \
  -o audit_logs.csv
```

### CSV 格式

```csv
ID,Event Type,Node ID,Node Name,Username,Event Time,Details,Client IP,User Agent
abc123,NODE_CREATED,doc-456,report.pdf,admin,2025-12-18 10:30:00,"Created DOCUMENT: report.pdf",192.168.1.100,"Mozilla/5.0 ..."
```

---

## 3.3 审计日志保留策略

### 配置项

**文件**：`ecm-core/src/main/resources/application.yml`

```yaml
ecm:
  audit:
    retention-days: ${ECM_AUDIT_RETENTION_DAYS:365}  # 默认保留 365 天（<=0 表示禁用清理）
```

### 后端实现

**文件**：`ecm-core/src/main/java/com/ecm/core/service/AnalyticsService.java`

```java
@Value("${ecm.audit.retention-days:365}")
private int auditRetentionDays;

@Scheduled(cron = "0 0 2 * * *")
@Transactional
public void cleanupExpiredAuditLogs() {
    if (auditRetentionDays <= 0) {
        return;
    }

    LocalDateTime threshold = LocalDateTime.now().minusDays(auditRetentionDays);
    long count = auditLogRepository.countByEventTimeBefore(threshold);
    if (count > 0) {
        auditLogRepository.deleteByEventTimeBefore(threshold);
    }
}
```

说明：
- 当前实现中清理 cron 为固定值（每天 2:00），未暴露 `cleanup.enabled`/`cleanup.cron` 配置项；
- 可通过设置 `ecm.audit.retention-days<=0` 来禁用清理。

### API 接口

#### 查询保留策略信息

```java
@GetMapping("/audit/retention")
@Operation(summary = "Audit Retention Info")
public ResponseEntity<Map<String, Object>> getAuditRetentionInfo() {
    return ResponseEntity.ok(Map.of(
        "retentionDays", analyticsService.getAuditRetentionDays(),
        "expiredLogCount", analyticsService.getExpiredAuditLogCount()
    ));
}
```

#### 手动触发清理

```java
@PostMapping("/audit/cleanup")
@Operation(summary = "Trigger Audit Cleanup")
public ResponseEntity<Map<String, Object>> triggerAuditCleanup() {
    long deletedCount = analyticsService.manualCleanupExpiredAuditLogs();
    return ResponseEntity.ok(Map.of(
        "deletedCount", deletedCount,
        "retentionDays", analyticsService.getAuditRetentionDays(),
        "message", deletedCount > 0
            ? String.format("Deleted %d expired audit logs", deletedCount)
            : "No expired audit logs to delete"
    ));
}
```

### 使用示例

```bash
# 查询保留策略
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:7700/api/v1/analytics/audit/retention"

# 响应
{
  "retentionDays": 365,
  "expiredLogCount": 1234
}

# 手动触发清理
curl -X POST -H "Authorization: Bearer $TOKEN" \
  "http://localhost:7700/api/v1/analytics/audit/cleanup"

# 响应
{
  "deletedCount": 1234,
  "retentionDays": 365,
  "message": "Deleted 1234 expired audit logs"
}
```

---

## 3.4 Repository 扩展

**文件**：`ecm-core/src/main/java/com/ecm/core/repository/AuditLogRepository.java`

```java
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    // 实际字段为 eventTime
    List<AuditLog> findByTimeRangeForExport(LocalDateTime from, LocalDateTime to);
    void deleteByEventTimeBefore(LocalDateTime threshold);
    long countByEventTimeBefore(LocalDateTime threshold);
}
```

---

## 3.5 前端 Admin Dashboard 增强

### 导出与清理按钮

Admin Dashboard 页面增加 “Export CSV” 按钮（默认导出最近 30 天审计日志）：

```tsx
<Button
  variant="outlined"
  onClick={handleExportAuditLogs}
  startIcon={<DownloadIcon />}
>
  Export CSV
</Button>
```

并展示审计保留策略信息（Retention days / expired count），当存在过期日志时显示 “Cleanup” 按钮进行手动清理。

> TODO：若需要可配置导出时间范围，可在前端增加 from/to 选择对话框并传入导出接口。

---

## 3.6 规则执行审计

### 实现概述

规则执行审计记录自动化规则的执行结果，包括：
- 规则匹配与执行结果（成功/失败）
- 执行动作汇总（避免每个 action 刷爆日志）
- 定时规则批量执行摘要

### AuditService 扩展

**文件**：`ecm-core/src/main/java/com/ecm/core/service/AuditService.java`

```java
/**
 * Log rule execution result (summary level, not per-action)
 */
public void logRuleExecution(RuleExecutionResult result, String username) {
    String eventType = result.isSuccess() ? "RULE_EXECUTED" : "RULE_EXECUTION_FAILED";
    String details = String.format(
        "Rule '%s' [%s] on document '%s': %s (actions: %d/%d succeeded, duration: %dms)",
        rule.getName(), result.getTriggerType(), result.getDocumentName(),
        result.isSuccess() ? "SUCCESS" : "FAILED",
        result.getSuccessfulActionCount(), result.getTotalActionCount(),
        result.getDurationMs()
    );
    logEvent(eventType, result.getDocumentId(), result.getDocumentName(), username, details);
}

/**
 * Log scheduled rule batch execution summary
 */
public void logScheduledRuleBatchExecution(AutomationRule rule, int documentsProcessed,
        int successCount, int failureCount, long durationMs, String username) {
    String eventType = failureCount == 0 ? "SCHEDULED_RULE_BATCH_COMPLETED" : "SCHEDULED_RULE_BATCH_PARTIAL";
    String details = String.format(
        "Scheduled rule '%s' batch execution: %d documents processed (%d succeeded, %d failed) in %dms",
        rule.getName(), documentsProcessed, successCount, failureCount, durationMs
    );
    logEvent(eventType, rule.getId(), rule.getName(), username, details);
}
```

### 审计事件类型

| 事件类型 | 描述 |
|---------|------|
| `RULE_EXECUTED` | 规则执行成功 |
| `RULE_EXECUTION_FAILED` | 规则执行失败 |
| `SCHEDULED_RULE_BATCH_COMPLETED` | 定时规则批量执行完成（无失败） |
| `SCHEDULED_RULE_BATCH_PARTIAL` | 定时规则批量执行部分失败 |

### 调用点

1. **RuleEngineService.executeRule()** - 单个规则执行后记录审计
2. **ScheduledRuleRunner.executeScheduledRule()** - 批量执行后记录汇总审计

---

## 3.7 安全增强汇总

| 功能 | 实现方式 | 状态 |
|------|---------|------|
| MFA (OTP/TOTP) | Keycloak 原生 Required Action | 已完成 |
| MFA 状态显示 | Settings 页面读取 token.amr | 已完成 |
| 审计日志导出 | CSV 格式，支持时间范围 | 已完成 |
| 审计保留策略 | 可配置天数 + 定时清理 | 已完成 |
| 手动清理 | Admin API 触发 | 已完成 |
| 规则执行审计 | RuleEngineService → AuditService | 已完成 |

---

## 文件清单

| 文件 | 变更类型 | 描述 |
|------|---------|------|
| `keycloak/realm-export.json` | 修改 | 启用 OTP 策略 |
| `ecm-frontend/src/pages/SettingsPage.tsx` | 修改 | MFA 状态显示 + 配置链接 |
| `ecm-core/.../controller/AnalyticsController.java` | 修改 | 添加导出/保留/清理 API |
| `ecm-core/.../service/AnalyticsService.java` | 修改 | 实现导出/清理逻辑 |
| `ecm-core/.../repository/AuditLogRepository.java` | 修改 | 添加查询/删除方法 |
| `ecm-core/src/main/resources/application.yml` | 修改 | 添加审计保留配置 |
| `ecm-core/.../service/AuditService.java` | 修改 | 添加规则执行审计方法 |
| `ecm-core/.../service/RuleEngineService.java` | 修改 | 注入 AuditService，执行后写审计 |
| `ecm-core/.../service/ScheduledRuleRunner.java` | 修改 | 批量执行后写汇总审计 |
| `ecm-core/.../entity/RuleExecutionResult.java` | 修改 | 添加 getTotalActionCount() |

---

## API 汇总

| 方法 | 路径 | 描述 | 权限 |
|------|------|------|------|
| GET | `/api/v1/analytics/audit/export` | 导出审计日志 CSV | ADMIN |
| GET | `/api/v1/analytics/audit/retention` | 查询保留策略 | ADMIN |
| POST | `/api/v1/analytics/audit/cleanup` | 手动触发清理 | ADMIN |

---

## 验收标准

- [x] Keycloak OTP 策略已启用
- [x] Settings 页面显示 MFA 状态
- [x] 可导出指定时间范围的审计日志
- [x] 保留策略可配置（默认 365 天）
- [x] 定时清理任务已启用
- [x] 手动清理 API 可用
- [x] 规则执行写入 audit_log（汇总级别，避免刷爆日志）
- [x] 定时规则批量执行写入 audit_log

---

## 下一步

- Sprint 4：内容安全（ClamAV 病毒扫描 + 加密存储）
