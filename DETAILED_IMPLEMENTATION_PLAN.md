# Athena ECM 详细实施计划

## 🎯 总体概览

**项目周期**：25周（约6个月）  
**团队规模**：7-10人  
**预算范围**：$443.8K  
**交付模式**：敏捷开发，2周冲刺  

---

## 📅 阶段一：安全增强（第1-4周）

### 🔒 **总体目标**
建立企业级安全基础，确保系统符合安全合规要求

### 📊 **关键指标**
- 安全漏洞数量：0个高危漏洞
- MFA启用率：>95%
- 加密覆盖率：100%文档加密
- 审计日志覆盖率：100%用户操作

---

### **1.1 多因素认证(MFA)系统** 
**时间**：第1-1.5周 | **负责人**：后端开发工程师×2

#### 详细任务分解

**Sprint 1 (第1周)**
- **Day 1-2**: 需求分析和技术选型
  - 调研TOTP标准（RFC 6238）
  - 选择MFA库（Google Authenticator兼容）
  - 设计数据库表结构
  
- **Day 3-5**: 后端核心开发
  ```java
  // 创建文件结构
  com/ecm/core/security/mfa/
  ├── MfaService.java              // MFA核心服务
  ├── TotpService.java            // TOTP实现
  ├── SmsService.java             // SMS验证服务  
  ├── MfaTokenGenerator.java      // 令牌生成器
  ├── MfaValidator.java           // 验证器
  └── MfaConfigurationService.java // 配置管理
  
  com/ecm/core/entity/
  ├── UserMfaSettings.java        // 用户MFA设置
  ├── MfaBackupCode.java          // 备用恢复代码
  └── MfaAuditLog.java            // MFA审计日志
  
  com/ecm/core/controller/
  └── MfaController.java          // MFA API接口
  ```

**Sprint 2 (第1.5周)**  
- **Day 1-3**: 前端组件开发
  ```typescript
  src/components/auth/
  ├── MfaSetup.tsx               // MFA设置页面
  ├── MfaVerification.tsx        // MFA验证组件
  ├── BackupCodes.tsx            // 备用代码管理
  └── MfaSettings.tsx            // MFA偏好设置
  
  src/services/
  └── mfaService.ts              // MFA API调用服务
  ```

#### 技术实现细节

**数据库设计**：
```sql
-- 用户MFA设置表
CREATE TABLE user_mfa_settings (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    mfa_enabled BOOLEAN DEFAULT FALSE,
    secret_key VARCHAR(32), -- Base32编码的密钥
    backup_codes TEXT[], -- 备用恢复代码数组
    sms_number VARCHAR(20), -- 短信号码
    preferred_method VARCHAR(10) DEFAULT 'totp', -- totp/sms
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- MFA审计日志表
CREATE TABLE mfa_audit_logs (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    action VARCHAR(50) NOT NULL, -- setup/verify/disable
    method VARCHAR(10), -- totp/sms/backup
    success BOOLEAN NOT NULL,
    ip_address INET,
    user_agent TEXT,
    created_at TIMESTAMP DEFAULT NOW()
);
```

**测试计划**：
- 单元测试：TOTP生成和验证逻辑
- 集成测试：MFA流程端到端测试
- 安全测试：时间窗口攻击防护测试
- 用户体验测试：移动端扫码测试

**验收标准**：
- ✅ 支持Google Authenticator等TOTP应用
- ✅ 提供SMS备选方案
- ✅ 生成10个备用恢复代码
- ✅ 管理员可强制启用MFA策略
- ✅ 完整的审计日志记录

---

### **1.2 文档加密存储功能**
**时间**：第1.5-3周 | **负责人**：后端开发工程师×2

#### 详细任务分解

**Sprint 1 (第1.5-2周)**
- **Day 1-2**: 加密方案设计
  - 选择AES-256-GCM算法
  - 设计密钥管理架构
  - 制定密钥轮换策略

- **Day 3-5**: 核心加密服务开发
  ```java
  com/ecm/core/security/encryption/
  ├── EncryptionService.java           // 加密服务接口
  ├── AesEncryptionService.java        // AES加密实现
  ├── KeyManagementService.java        // 密钥管理服务
  ├── EncryptedContentStore.java       // 加密内容存储
  ├── KeyRotationService.java          // 密钥轮换服务
  └── EncryptionAuditService.java      // 加密审计服务
  ```

**Sprint 2 (第2-3周)**
- **Day 1-3**: 集成现有ContentService
  ```java
  // 修改现有服务
  com/ecm/core/service/ContentService.java (增强)
  ├── 透明加密存储
  ├── 透明解密读取  
  ├── 加密状态检查
  └── 批量加密迁移
  ```

- **Day 4-5**: 密钥管理界面
  ```typescript
  src/components/admin/
  ├── EncryptionSettings.tsx          // 加密配置
  ├── KeyManagement.tsx               // 密钥管理
  └── EncryptionStatus.tsx            // 加密状态监控
  ```

#### 技术实现细节

**加密架构**：
```
应用层 -> 加密服务层 -> 存储层
         ↓
   密钥管理服务(KMS)
   ├── 主密钥(HSM/云KMS)
   ├── 数据加密密钥(DEK)
   └── 密钥轮换调度
```

**数据库设计**：
```sql
-- 加密密钥表
CREATE TABLE encryption_keys (
    id UUID PRIMARY KEY,
    key_alias VARCHAR(100) UNIQUE NOT NULL,
    encrypted_dek BYTEA NOT NULL, -- 加密的数据加密密钥
    algorithm VARCHAR(20) DEFAULT 'AES-256-GCM',
    key_version INTEGER DEFAULT 1,
    created_at TIMESTAMP DEFAULT NOW(),
    expires_at TIMESTAMP,
    status VARCHAR(20) DEFAULT 'active' -- active/retired/revoked
);

-- 文档加密元数据表
CREATE TABLE document_encryption_metadata (
    document_id UUID PRIMARY KEY REFERENCES documents(id),
    key_id UUID NOT NULL REFERENCES encryption_keys(id),
    encryption_iv BYTEA NOT NULL, -- 初始化向量
    encrypted_at TIMESTAMP DEFAULT NOW(),
    encryption_version INTEGER DEFAULT 1
);
```

**性能考虑**：
- 使用流式加密处理大文件
- 实现密钥缓存减少KMS调用
- 异步后台批量加密现有文档

**验收标准**：
- ✅ 所有新上传文档自动加密
- ✅ 支持现有文档批量加密迁移
- ✅ 密钥轮换不影响文档访问
- ✅ 加密/解密性能损失<10%

---

### **1.3 病毒扫描集成(ClamAV)**
**时间**：第3-3.5周 | **负责人**：后端开发工程师×1

#### 详细任务分解

**Sprint 1 (第3-3.5周)**
- **Day 1**: ClamAV服务部署
  ```yaml
  # docker-compose.yml 新增服务
  clamav:
    image: clamav/clamav:latest
    container_name: athena-clamav
    volumes:
      - clamav_data:/var/lib/clamav
      - clamav_logs:/var/log/clamav
    environment:
      - CLAMD_STARTUP_TIMEOUT=90
    healthcheck:
      test: ["CMD", "clamdscan", "--ping"]
      interval: 60s
      retries: 3
    networks:
      - athena-network
  ```

- **Day 2-3**: 病毒扫描服务开发
  ```java
  com/ecm/core/security/antivirus/
  ├── AntivirusService.java           // 病毒扫描服务接口
  ├── ClamAvClient.java               // ClamAV客户端
  ├── ScanResult.java                 // 扫描结果封装
  ├── QuarantineService.java          // 隔离服务
  └── AntivirusAuditService.java      // 扫描审计服务
  ```

- **Day 4-5**: 集成文件上传流程
  ```java
  // 修改现有上传控制器
  com/ecm/core/controller/DocumentController.java (增强)
  ├── 上传前扫描检查
  ├── 异步扫描处理
  ├── 感染文件隔离
  └── 扫描结果通知
  ```

#### 技术实现细节

**扫描流程**：
```
文件上传 -> 临时存储 -> 病毒扫描 -> 扫描通过 -> 正式存储
           ↓               ↓
         扫描队列        感染隔离
```

**数据库设计**：
```sql
-- 病毒扫描记录表
CREATE TABLE antivirus_scan_logs (
    id UUID PRIMARY KEY,
    document_id UUID REFERENCES documents(id),
    file_hash VARCHAR(64) NOT NULL, -- SHA256哈希
    scan_engine VARCHAR(20) DEFAULT 'clamav',
    scan_result VARCHAR(20) NOT NULL, -- clean/infected/error
    threat_name VARCHAR(200), -- 病毒名称
    scan_duration_ms INTEGER,
    scanned_at TIMESTAMP DEFAULT NOW(),
    quarantined BOOLEAN DEFAULT FALSE
);

-- 隔离文件表  
CREATE TABLE quarantined_files (
    id UUID PRIMARY KEY,
    original_filename VARCHAR(500),
    file_hash VARCHAR(64) UNIQUE,
    quarantine_path VARCHAR(1000),
    threat_name VARCHAR(200),
    uploaded_by UUID REFERENCES users(id),
    quarantined_at TIMESTAMP DEFAULT NOW(),
    reviewed_at TIMESTAMP,
    reviewed_by UUID REFERENCES users(id),
    action_taken VARCHAR(50) -- deleted/restored/pending
);
```

**验收标准**：
- ✅ 所有上传文件自动扫描
- ✅ 感染文件自动隔离
- ✅ 扫描结果实时通知
- ✅ 管理员隔离文件管理界面

---

### **1.4 审计日志增强和保留策略**
**时间**：第3.5-4周 | **负责人**：后端开发工程师×1

#### 详细任务分解

**Sprint 1 (第3.5-4周)**
- **Day 1-2**: 审计框架增强
  ```java
  com/ecm/core/audit/
  ├── AuditService.java (增强)        // 审计服务核心
  ├── AuditEventPublisher.java        // 审计事件发布器  
  ├── AuditEventListener.java         // 审计事件监听器
  ├── RetentionPolicyService.java     // 保留策略服务
  ├── AuditReportService.java         // 审计报告服务
  └── AuditDataArchiver.java          // 审计数据归档器
  ```

- **Day 3-5**: 报告和管理界面
  ```typescript
  src/components/admin/audit/
  ├── AuditLogViewer.tsx              // 审计日志查看器
  ├── AuditReportGenerator.tsx        // 报告生成器
  ├── RetentionPolicySettings.tsx     // 保留策略设置
  └── AuditDashboard.tsx              // 审计仪表板
  ```

#### 技术实现细节

**审计事件类型扩展**：
```java
public enum AuditEventType {
    // 用户操作
    USER_LOGIN, USER_LOGOUT, USER_LOGIN_FAILED,
    PASSWORD_CHANGE, MFA_ENABLED, MFA_DISABLED,
    
    // 文档操作
    DOCUMENT_CREATED, DOCUMENT_VIEWED, DOCUMENT_UPDATED,
    DOCUMENT_DELETED, DOCUMENT_DOWNLOADED, DOCUMENT_SHARED,
    
    // 权限操作  
    PERMISSION_GRANTED, PERMISSION_REVOKED, ROLE_ASSIGNED,
    
    // 系统操作
    SYSTEM_CONFIG_CHANGED, BACKUP_CREATED, BACKUP_RESTORED,
    
    // 安全事件
    VIRUS_DETECTED, ENCRYPTION_KEY_ROTATED, SECURITY_POLICY_CHANGED
}
```

**保留策略配置**：
```sql
-- 审计保留策略表
CREATE TABLE audit_retention_policies (
    id UUID PRIMARY KEY,
    event_category VARCHAR(50) NOT NULL,
    retention_period_days INTEGER NOT NULL,
    archive_after_days INTEGER,
    compression_enabled BOOLEAN DEFAULT TRUE,
    encryption_required BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- 预设策略示例
INSERT INTO audit_retention_policies VALUES
('security-events', 2555), -- 7年保留（合规要求）
('document-operations', 1095), -- 3年保留
('user-activities', 365), -- 1年保留
('system-events', 180); -- 6个月保留
```

**验收标准**：
- ✅ 所有用户操作100%记录
- ✅ 支持灵活的保留策略配置  
- ✅ 自动归档和压缩历史日志
- ✅ 可生成合规性审计报告

---

## 📅 阶段二：API增强（第5-7周）

### 🚀 **总体目标**
提升API性能、可扩展性和集成能力

### 📊 **关键指标**
- API响应时间：<200ms (95分位)
- 限流准确率：>99.9%
- Webhook成功投递率：>99%
- GraphQL查询性能提升：>50%

---

### **2.1 API速率限制**
**时间**：第5-5.5周 | **负责人**：后端开发工程师×1

#### 详细任务分解

**Sprint 1 (第5-5.5周)**
- **Day 1**: 限流方案设计
  - 令牌桶算法实现
  - Redis分布式计数器
  - 限流策略配置化

- **Day 2-3**: 核心限流组件开发
  ```java
  com/ecm/core/ratelimit/
  ├── RateLimitService.java           // 限流服务接口
  ├── RedisRateLimiter.java          // Redis限流实现
  ├── RateLimitFilter.java           // 限流过滤器
  ├── RateLimitConfig.java           // 限流配置
  └── RateLimitExceptionHandler.java // 限流异常处理
  ```

- **Day 4-5**: 配置和监控
  ```yaml
  # application.yml 限流配置
  rate-limit:
    enabled: true
    default:
      requests-per-minute: 100
      burst-capacity: 20
    endpoints:
      - pattern: "/api/v1/documents/upload"
        requests-per-minute: 10
        requests-per-hour: 100
      - pattern: "/api/v1/search"
        requests-per-minute: 50
        requests-per-hour: 1000
    user-tiers:
      free: 
        requests-per-minute: 30
      premium:
        requests-per-minute: 200
      enterprise:
        requests-per-minute: 1000
  ```

#### 技术实现细节

**限流算法**：
```java
@Component
public class TokenBucketRateLimiter {
    private final RedisTemplate<String, String> redisTemplate;
    
    public boolean isAllowed(String key, int limit, Duration window) {
        String script = """
            local current = redis.call('GET', KEYS[1])
            local ttl = redis.call('TTL', KEYS[1])
            
            if current == false then
                redis.call('SETEX', KEYS[1], ARGV[2], 1)
                return 1
            end
            
            if tonumber(current) < tonumber(ARGV[1]) then
                redis.call('INCR', KEYS[1])
                return 1
            else
                return 0
            end
        """;
        
        Long result = redisTemplate.execute(
            new DefaultRedisScript<>(script, Long.class),
            Collections.singletonList(key),
            String.valueOf(limit),
            String.valueOf(window.getSeconds())
        );
        
        return result != null && result == 1;
    }
}
```

**监控指标**：
- 限流触发次数/分钟
- API端点请求分布
- 用户层级请求量统计
- 限流策略命中率

**验收标准**：
- ✅ 支持按用户/IP/端点限流
- ✅ 可配置不同限流策略
- ✅ 提供限流监控仪表板
- ✅ 限流信息在响应头中返回

---

### **2.2 Webhook事件通知系统**
**时间**：第5.5-6.5周 | **负责人**：后端开发工程师×1

#### 详细任务分解

**Sprint 1 (第5.5-6周)**
- **Day 1-2**: Webhook架构设计
  - 事件订阅模型设计
  - 重试机制和失败处理
  - 签名验证和安全机制

- **Day 3-5**: 核心Webhook服务
  ```java
  com/ecm/core/webhook/
  ├── WebhookService.java             // Webhook核心服务
  ├── WebhookEventPublisher.java      // 事件发布器
  ├── WebhookDeliveryService.java     // 投递服务
  ├── WebhookRetryService.java        // 重试服务
  ├── WebhookSecurityService.java     // 安全验证服务
  └── WebhookAuditService.java        // Webhook审计
  
  com/ecm/core/entity/
  ├── WebhookSubscription.java        // 订阅配置
  ├── WebhookEvent.java               // 事件记录
  ├── WebhookDeliveryLog.java         // 投递日志
  └── WebhookEndpoint.java            // 端点配置
  ```

**Sprint 2 (第6-6.5周)**
- **Day 1-3**: 管理界面开发
  ```typescript
  src/components/admin/webhooks/
  ├── WebhookSubscriptions.tsx        // 订阅管理
  ├── WebhookEventLogs.tsx           // 事件日志
  ├── WebhookTestConsole.tsx         // 测试控制台
  └── WebhookSettings.tsx            // Webhook设置
  ```

#### 技术实现细节

**事件类型定义**：
```java
public enum WebhookEventType {
    // 文档事件
    DOCUMENT_CREATED("document.created"),
    DOCUMENT_UPDATED("document.updated"),
    DOCUMENT_DELETED("document.deleted"),
    DOCUMENT_SHARED("document.shared"),
    
    // 用户事件
    USER_REGISTERED("user.registered"),
    USER_ACTIVATED("user.activated"),
    USER_DEACTIVATED("user.deactivated"),
    
    // 工作流事件
    WORKFLOW_STARTED("workflow.started"),
    WORKFLOW_COMPLETED("workflow.completed"),
    WORKFLOW_FAILED("workflow.failed"),
    
    // 系统事件
    BACKUP_COMPLETED("system.backup.completed"),
    MAINTENANCE_STARTED("system.maintenance.started");
}
```

**数据库设计**：
```sql
-- Webhook订阅表
CREATE TABLE webhook_subscriptions (
    id UUID PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    url VARCHAR(1000) NOT NULL,
    secret VARCHAR(100), -- 签名密钥
    events TEXT[] NOT NULL, -- 订阅的事件类型数组
    active BOOLEAN DEFAULT TRUE,
    retry_policy JSONB, -- 重试策略配置
    headers JSONB, -- 自定义请求头
    created_by UUID REFERENCES users(id),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Webhook投递日志表
CREATE TABLE webhook_delivery_logs (
    id UUID PRIMARY KEY,
    subscription_id UUID NOT NULL REFERENCES webhook_subscriptions(id),
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL, -- pending/success/failed/retry
    http_status_code INTEGER,
    response_body TEXT,
    attempt_count INTEGER DEFAULT 1,
    next_retry_at TIMESTAMP,
    delivered_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW()
);
```

**重试策略**：
```json
{
  "max_retries": 5,
  "retry_delays": [1, 5, 15, 60, 300], // 秒
  "backoff_strategy": "exponential",
  "failure_threshold": 10, // 连续失败次数
  "circuit_breaker_timeout": 3600 // 熔断恢复时间(秒)
}
```

**验收标准**：
- ✅ 支持灵活的事件订阅配置
- ✅ 可靠的重试和失败处理机制
- ✅ HMAC签名验证确保安全性
- ✅ 提供详细的投递日志和监控

---

### **2.3 GraphQL API层**
**时间**：第6.5-7周 | **负责人**：后端开发工程师×1

#### 详细任务分解

**Sprint 1 (第6.5-7周)**
- **Day 1**: GraphQL架构设计
  - Schema设计和类型定义
  - Resolver实现策略
  - 性能优化方案(DataLoader)

- **Day 2-3**: Schema和Resolver开发
  ```java
  // GraphQL Schema文件
  src/main/resources/graphql/
  ├── schema.graphqls                 // 根Schema
  ├── document.graphqls               // 文档相关类型
  ├── user.graphqls                   // 用户相关类型
  ├── workflow.graphqls               // 工作流相关类型
  └── search.graphqls                 // 搜索相关类型
  
  // Resolver实现
  com/ecm/core/graphql/
  ├── DocumentResolver.java           // 文档查询解析器
  ├── UserResolver.java               // 用户查询解析器
  ├── WorkflowResolver.java           // 工作流解析器
  ├── SearchResolver.java             // 搜索解析器
  └── MutationResolver.java           // 变更操作解析器
  ```

- **Day 4-5**: 性能优化和前端集成
  ```typescript
  // 前端GraphQL集成
  src/graphql/
  ├── client.ts                       // Apollo客户端配置
  ├── queries/
  │   ├── documentQueries.ts
  │   ├── userQueries.ts
  │   └── searchQueries.ts
  ├── mutations/
  │   ├── documentMutations.ts
  │   └── userMutations.ts
  └── fragments/
      ├── documentFragments.ts
      └── userFragments.ts
  ```

#### 技术实现细节

**Schema设计示例**：
```graphql
type Query {
    # 文档查询
    document(id: ID!): Document
    documents(filter: DocumentFilter, pagination: Pagination): DocumentConnection
    documentVersions(documentId: ID!): [DocumentVersion!]!
    
    # 搜索查询  
    search(query: String!, filters: SearchFilters): SearchResult
    
    # 用户查询
    user(id: ID!): User
    currentUser: User
}

type Mutation {
    # 文档操作
    createDocument(input: CreateDocumentInput!): Document!
    updateDocument(id: ID!, input: UpdateDocumentInput!): Document!
    deleteDocument(id: ID!): Boolean!
    
    # 权限操作
    shareDocument(documentId: ID!, permissions: [PermissionInput!]!): Boolean!
}

type Document {
    id: ID!
    name: String!
    content: String
    mimeType: String!
    size: Long!
    tags: [Tag!]!
    versions: [DocumentVersion!]!
    permissions: [Permission!]!
    createdBy: User!
    createdAt: DateTime!
    updatedAt: DateTime!
}
```

**DataLoader优化**：
```java
@Component
public class DocumentDataLoader {
    
    @Autowired
    private DocumentService documentService;
    
    public DataLoader<UUID, Document> createDocumentLoader() {
        return DataLoader.newDataLoader(documentIds -> 
            CompletableFuture.supplyAsync(() -> 
                documentService.findByIds(documentIds)
            )
        );
    }
    
    public DataLoader<UUID, List<Tag>> createDocumentTagsLoader() {
        return DataLoader.newDataLoader(documentIds ->
            CompletableFuture.supplyAsync(() ->
                tagService.findTagsByDocumentIds(documentIds)
            )
        );
    }
}
```

**验收标准**：
- ✅ 完整的Schema覆盖核心业务对象
- ✅ 支持复杂查询和嵌套关联
- ✅ 查询性能优于REST API 30%+
- ✅ 提供GraphQL Playground调试界面

---

## 📅 阶段三：企业功能（第8-11周）

### 🏢 **总体目标**
构建完整的企业级内容管理和合规能力

### 📊 **关键指标**
- 文档保留策略执行率：100%
- 工作流自动化率：>80%
- 数字签名验证成功率：>99.9%
- 合规报告生成时间：<5分钟

---

### **3.1 文档保留和处置策略**
**时间**：第8-9.5周 | **负责人**：后端开发工程师×2

#### 详细任务分解

**Sprint 1 (第8-8.5周)**
- **Day 1-2**: 保留策略框架设计
  - 保留规则引擎架构
  - 法律保全机制设计
  - 自动处置流程设计

- **Day 3-5**: 核心保留服务开发
  ```java
  com/ecm/core/retention/
  ├── RetentionService.java           // 保留服务接口
  ├── RetentionPolicyEngine.java      // 保留策略引擎
  ├── RetentionRuleEvaluator.java     // 规则评估器
  ├── LegalHoldService.java           // 法律保全服务
  ├── DispositionService.java         // 处置服务
  └── RetentionAuditService.java      // 保留审计服务
  
  com/ecm/core/entity/
  ├── RetentionPolicy.java            // 保留策略
  ├── RetentionSchedule.java          // 保留计划
  ├── LegalHold.java                  // 法律保全记录
  ├── DispositionRecord.java          // 处置记录
  └── RetentionAuditLog.java          // 保留审计日志
  ```

**Sprint 2 (第8.5-9周)**
- **Day 1-3**: 策略配置和执行引擎
  ```java
  com/ecm/core/retention/rules/
  ├── RetentionRule.java              // 保留规则基类
  ├── DocumentTypeRetentionRule.java  // 按文档类型保留
  ├── TagBasedRetentionRule.java      // 按标签保留
  ├── ContentBasedRetentionRule.java  // 按内容保留
  └── CustomRetentionRule.java        // 自定义保留规则
  
  com/ecm/core/retention/scheduler/
  ├── RetentionJobScheduler.java      // 保留任务调度器
  ├── DispositionJobExecutor.java     // 处置任务执行器
  └── RetentionPolicyValidator.java   // 策略验证器
  ```

**Sprint 3 (第9-9.5周)**
- **Day 1-3**: 管理界面开发
  ```typescript
  src/components/admin/retention/
  ├── RetentionPolicies.tsx           // 保留策略管理
  ├── LegalHolds.tsx                  // 法律保全管理
  ├── DispositionSchedule.tsx         // 处置计划
  ├── RetentionReports.tsx            // 保留报告
  └── RetentionAudit.tsx              // 保留审计
  ```

#### 技术实现细节

**保留策略模型**：
```sql
-- 保留策略表
CREATE TABLE retention_policies (
    id UUID PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    trigger_event VARCHAR(50) NOT NULL, -- creation/modification/access
    retention_period_years INTEGER,
    retention_period_days INTEGER,
    disposition_action VARCHAR(50) DEFAULT 'delete', -- delete/archive/review
    applies_to_document_types TEXT[], -- 适用的文档类型
    applies_to_tags TEXT[], -- 适用的标签
    content_criteria JSONB, -- 内容匹配条件
    active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- 文档保留计划表
CREATE TABLE document_retention_schedules (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES documents(id),
    policy_id UUID NOT NULL REFERENCES retention_policies(id),
    retention_start_date DATE NOT NULL,
    retention_end_date DATE NOT NULL,
    disposition_action VARCHAR(50),
    legal_hold_ids UUID[], -- 关联的法律保全ID数组
    status VARCHAR(20) DEFAULT 'active', -- active/suspended/completed
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- 法律保全表
CREATE TABLE legal_holds (
    id UUID PRIMARY KEY,
    case_name VARCHAR(500) NOT NULL,
    case_number VARCHAR(100),
    description TEXT,
    custodians TEXT[], -- 保管人列表
    keywords TEXT[], -- 关键词
    date_range_start DATE,
    date_range_end DATE,
    status VARCHAR(20) DEFAULT 'active', -- active/released/expired
    created_by UUID REFERENCES users(id),
    released_by UUID REFERENCES users(id),
    created_at TIMESTAMP DEFAULT NOW(),
    released_at TIMESTAMP
);
```

**规则引擎示例**：
```java
@Component
public class RetentionRuleEngine {
    
    public List<RetentionPolicy> evaluateApplicablePolicies(Document document) {
        return retentionPolicyRepository.findActive().stream()
            .filter(policy -> matchesDocumentType(policy, document))
            .filter(policy -> matchesTags(policy, document))
            .filter(policy -> matchesContent(policy, document))
            .collect(Collectors.toList());
    }
    
    public LocalDate calculateRetentionEndDate(Document document, RetentionPolicy policy) {
        LocalDate startDate = getRetentionStartDate(document, policy.getTriggerEvent());
        
        if (policy.getRetentionPeriodYears() != null) {
            return startDate.plusYears(policy.getRetentionPeriodYears());
        } else if (policy.getRetentionPeriodDays() != null) {
            return startDate.plusDays(policy.getRetentionPeriodDays());
        }
        
        return LocalDate.MAX; // 永久保留
    }
}
```

**验收标准**：
- ✅ 支持灵活的保留策略配置
- ✅ 法律保全可暂停自动处置
- ✅ 自动化的处置任务执行
- ✅ 完整的保留和处置审计日志

---

### **3.2 高级工作流(并行/条件审批)**
**时间**：第9.5-10.5周 | **负责人**：后端开发工程师×2

#### 详细任务分解

**Sprint 1 (第9.5-10周)**
- **Day 1-2**: 高级工作流设计
  - 并行审批网关配置
  - 条件路由规则引擎
  - 动态任务分配机制

- **Day 3-5**: Flowable扩展开发
  ```java
  com/ecm/core/workflow/
  ├── WorkflowService.java (增强)     // 工作流服务增强
  ├── ParallelApprovalService.java    // 并行审批服务
  ├── ConditionalRoutingService.java  // 条件路由服务
  ├── DynamicTaskService.java         // 动态任务服务
  ├── WorkflowTemplateService.java    // 工作流模板服务
  └── WorkflowAnalyticsService.java   // 工作流分析服务
  
  com/ecm/core/workflow/rules/
  ├── RoutingRule.java                // 路由规则基类
  ├── DocumentValueRule.java          // 文档属性规则
  ├── UserAttributeRule.java          // 用户属性规则
  ├── TimeBasedRule.java              // 时间基础规则
  └── CustomExpressionRule.java       // 自定义表达式规则
  ```

**Sprint 2 (第10-10.5周)**
- **Day 1-3**: 工作流设计器
  ```typescript
  src/components/workflow/
  ├── WorkflowDesigner.tsx            // 工作流可视化设计器
  ├── ProcessCanvas.tsx               // 流程画布
  ├── TaskNodeEditor.tsx              // 任务节点编辑器
  ├── GatewayEditor.tsx               // 网关配置编辑器
  ├── ConditionBuilder.tsx            // 条件构建器
  └── WorkflowPreview.tsx             // 工作流预览
  ```

#### 技术实现细节

**BPMN流程定义扩展**：
```xml
<!-- 并行审批示例 -->
<bpmn:parallelGateway id="ParallelApproval_Gateway" />

<bpmn:userTask id="ManagerApproval" name="部门经理审批">
  <bpmn:extensionElements>
    <ecm:taskConfig>
      <ecm:assignmentRule type="role">DEPARTMENT_MANAGER</ecm:assignmentRule>
      <ecm:timeoutDays>3</ecm:timeoutDays>
      <ecm:escalationRule type="hierarchy">SENIOR_MANAGER</ecm:escalationRule>
    </ecm:taskConfig>
  </bpmn:extensionElements>
</bpmn:userTask>

<bpmn:userTask id="FinanceApproval" name="财务审批">
  <bpmn:extensionElements>
    <ecm:taskConfig>
      <ecm:assignmentRule type="role">FINANCE_APPROVER</ecm:assignmentRule>
      <ecm:requiredWhen>#{document.amount > 10000}</ecm:requiredWhen>
    </ecm:taskConfig>
  </bpmn:extensionElements>
</bpmn:userTask>

<!-- 条件路由示例 -->
<bpmn:exclusiveGateway id="AmountBasedRouting">
  <bpmn:extensionElements>
    <ecm:routingRules>
      <ecm:rule condition="#{document.amount <= 1000}" target="AutoApprove" />
      <ecm:rule condition="#{document.amount <= 10000}" target="ManagerApproval" />
      <ecm:rule condition="#{document.amount > 10000}" target="SeniorApproval" />
    </ecm:routingRules>
  </bpmn:extensionElements>
</bpmn:exclusiveGateway>
```

**数据库设计扩展**：
```sql
-- 工作流模板表
CREATE TABLE workflow_templates (
    id UUID PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    category VARCHAR(100),
    bpmn_definition TEXT NOT NULL,
    version INTEGER DEFAULT 1,
    is_active BOOLEAN DEFAULT TRUE,
    created_by UUID REFERENCES users(id),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- 动态任务分配表
CREATE TABLE dynamic_task_assignments (
    id UUID PRIMARY KEY,
    process_instance_id VARCHAR(100) NOT NULL,
    task_definition_key VARCHAR(100) NOT NULL,
    assignment_rule JSONB NOT NULL,
    assigned_users UUID[],
    assigned_groups VARCHAR(100)[],
    assignment_reason TEXT,
    assigned_at TIMESTAMP DEFAULT NOW()
);

-- 工作流性能指标表
CREATE TABLE workflow_performance_metrics (
    id UUID PRIMARY KEY,
    process_definition_key VARCHAR(100) NOT NULL,
    process_instance_id VARCHAR(100) NOT NULL,
    total_duration_minutes INTEGER,
    active_duration_minutes INTEGER,
    waiting_duration_minutes INTEGER,
    task_count INTEGER,
    participant_count INTEGER,
    completed_at TIMESTAMP DEFAULT NOW()
);
```

**条件规则引擎**：
```java
@Service
public class WorkflowConditionEvaluator {
    
    public boolean evaluateCondition(String condition, Map<String, Object> variables) {
        // 使用SpEL表达式解析
        ExpressionParser parser = new SpelExpressionParser();
        Expression exp = parser.parseExpression(condition);
        
        StandardEvaluationContext context = new StandardEvaluationContext();
        variables.forEach(context::setVariable);
        
        return Boolean.TRUE.equals(exp.getValue(context, Boolean.class));
    }
    
    public List<String> determineNextTasks(String gatewayId, 
                                          Map<String, Object> processVariables) {
        List<RoutingRule> rules = getRoutingRules(gatewayId);
        
        return rules.stream()
            .filter(rule -> evaluateCondition(rule.getCondition(), processVariables))
            .map(RoutingRule::getTargetTask)
            .collect(Collectors.toList());
    }
}
```

**验收标准**：
- ✅ 支持复杂的并行审批流程
- ✅ 灵活的条件路由配置
- ✅ 可视化的工作流设计器
- ✅ 工作流性能分析和优化建议

---

### **3.3 数字签名功能**
**时间**：第10.5-11周 | **负责人**：后端开发工程师×1

#### 详细任务分解

**Sprint 1 (第10.5-11周)**
- **Day 1**: 数字签名方案设计
  - PKI证书管理架构
  - PDF签名集成方案
  - 签名验证流程设计

- **Day 2-3**: 核心签名服务开发
  ```java
  com/ecm/core/signature/
  ├── DigitalSignatureService.java    // 数字签名服务
  ├── CertificateService.java         // 证书管理服务
  ├── PdfSignatureService.java        // PDF签名服务
  ├── SignatureVerificationService.java // 签名验证服务
  ├── TimestampService.java           // 时间戳服务
  └── SignatureAuditService.java      // 签名审计服务
  
  com/ecm/core/entity/
  ├── DigitalCertificate.java         // 数字证书
  ├── DocumentSignature.java          // 文档签名记录
  ├── SignatureVerificationResult.java // 验证结果
  └── SignatureAuditLog.java          // 签名审计日志
  ```

- **Day 4-5**: 前端签名界面
  ```typescript
  src/components/signature/
  ├── SignaturePanel.tsx              // 签名面板
  ├── CertificateManager.tsx          // 证书管理
  ├── SignatureVerifier.tsx           // 签名验证器
  ├── SignatureHistory.tsx            // 签名历史
  └── SignaturePolicySettings.tsx     // 签名策略设置
  ```

#### 技术实现细节

**PKI集成**：
```java
@Service
public class CertificateManagementService {
    
    public X509Certificate uploadCertificate(byte[] certificateData, String password) {
        try {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(new ByteArrayInputStream(certificateData), 
                         password.toCharArray());
            
            String alias = keyStore.aliases().nextElement();
            X509Certificate certificate = (X509Certificate) keyStore.getCertificate(alias);
            
            // 验证证书有效性
            certificate.checkValidity();
            
            // 存储证书信息
            saveCertificateMetadata(certificate, alias);
            
            return certificate;
        } catch (Exception e) {
            throw new CertificateException("Failed to process certificate", e);
        }
    }
    
    public boolean verifyCertificateChain(X509Certificate certificate) {
        // 验证证书链和吊销状态
        // 实现OCSP和CRL检查
        return true;
    }
}
```

**PDF签名集成**：
```java
@Service
public class PdfDigitalSignatureService {
    
    public byte[] signPdfDocument(byte[] pdfContent, 
                                 X509Certificate certificate,
                                 PrivateKey privateKey,
                                 String reason,
                                 String location) {
        try {
            PDDocument document = PDDocument.load(pdfContent);
            
            // 创建签名字典
            PDSignature signature = new PDSignature();
            signature.setFilter(COSName.ADOBE_PPKLITE);
            signature.setSubFilter(COSName.ADBE_PKCS7_DETACHED);
            signature.setName(certificate.getSubjectDN().getName());
            signature.setLocation(location);
            signature.setReason(reason);
            signature.setSignDate(Calendar.getInstance());
            
            // 添加签名到文档
            document.addSignature(signature, new SigningHandler(certificate, privateKey));
            
            // 保存签名后的文档
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.saveIncremental(output);
            document.close();
            
            return output.toByteArray();
        } catch (Exception e) {
            throw new SignatureException("Failed to sign PDF document", e);
        }
    }
}
```

**数据库设计**：
```sql
-- 数字证书表
CREATE TABLE digital_certificates (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    certificate_alias VARCHAR(100) NOT NULL,
    subject_dn TEXT NOT NULL,
    issuer_dn TEXT NOT NULL,
    serial_number VARCHAR(100) NOT NULL,
    not_before DATE NOT NULL,
    not_after DATE NOT NULL,
    key_usage INTEGER[], -- 密钥用法
    certificate_data BYTEA, -- 证书二进制数据
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT NOW()
);

-- 文档签名记录表
CREATE TABLE document_signatures (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES documents(id),
    document_version INTEGER NOT NULL,
    certificate_id UUID NOT NULL REFERENCES digital_certificates(id),
    signature_type VARCHAR(50) DEFAULT 'pkcs7', -- pkcs7/pades/cades
    signature_data BYTEA NOT NULL,
    signature_hash VARCHAR(128),
    signature_algorithm VARCHAR(50),
    signed_at TIMESTAMP DEFAULT NOW(),
    reason TEXT,
    location VARCHAR(200),
    is_valid BOOLEAN DEFAULT TRUE,
    validation_details JSONB
);
```

**验收标准**：
- ✅ 支持PKCS#12证书导入和管理
- ✅ PDF文档数字签名功能
- ✅ 签名完整性和有效性验证
- ✅ 签名审计日志和报告

---

## 📅 阶段四：用户体验（第12-14周）

### 🎨 **总体目标**
提升用户界面和交互体验，支持多端访问

### 📊 **关键指标**
- 移动端用户满意度：>85%
- 页面加载速度提升：>40%
- PWA功能可用性：>95%
- 界面响应时间：<100ms

---

### **4.1 移动端响应式优化**
**时间**：第12-13周 | **负责人**：前端开发工程师×2

#### 详细任务分解

**Sprint 1 (第12-12.5周)**
- **Day 1-2**: 响应式设计系统
  - 设计令牌(Design Tokens)定义
  - 断点系统和网格布局
  - 移动端交互模式设计

- **Day 3-5**: 核心组件响应式改造
  ```typescript
  src/styles/
  ├── breakpoints.css                 // 断点定义
  ├── grid.css                       // 网格系统
  ├── responsive-utilities.css        // 响应式工具类
  └── mobile-specific.css             // 移动端特定样式
  
  src/hooks/
  ├── useResponsive.ts               // 响应式钩子
  ├── useViewport.ts                 // 视口尺寸钩子
  ├── useTouchGestures.ts            // 触摸手势钩子
  └── useOrientation.ts              // 屏幕方向钩子
  ```

**Sprint 2 (第12.5-13周)**
- **Day 1-3**: 移动端专用组件
  ```typescript
  src/components/mobile/
  ├── MobileNavigation.tsx           // 移动端导航
  ├── MobileFileBrowser.tsx          // 移动端文件浏览器
  ├── MobileDocumentViewer.tsx       // 移动端文档查看器
  ├── MobileUploader.tsx             // 移动端上传器
  ├── TouchGestureHandler.tsx        // 触摸手势处理
  └── MobileSearchInterface.tsx      // 移动端搜索界面
  ```

#### 技术实现细节

**响应式断点系统**：
```css
/* breakpoints.css */
:root {
  --breakpoint-xs: 320px;
  --breakpoint-sm: 576px;  
  --breakpoint-md: 768px;
  --breakpoint-lg: 992px;
  --breakpoint-xl: 1200px;
  --breakpoint-xxl: 1400px;
}

/* 移动端优先的媒体查询 */
.container {
  width: 100%;
  padding: 0 1rem;
}

@media (min-width: 576px) {
  .container {
    max-width: 540px;
    margin: 0 auto;
  }
}

@media (min-width: 768px) {
  .container {
    max-width: 720px;
  }
  
  .desktop-only {
    display: block;
  }
}

.mobile-only {
  display: block;
}

@media (min-width: 768px) {
  .mobile-only {
    display: none;
  }
}
```

**触摸手势处理**：
```typescript
export const useTouchGestures = () => {
  const [gestureState, setGestureState] = useState({
    isSwping: false,
    swipeDirection: null,
    isPinching: false,
    pinchScale: 1
  });
  
  const handleTouchStart = useCallback((e: TouchEvent) => {
    const touches = e.touches;
    
    if (touches.length === 1) {
      // 单指触摸 - 滑动手势
      setGestureState(prev => ({
        ...prev,
        startX: touches[0].clientX,
        startY: touches[0].clientY,
        isSwping: true
      }));
    } else if (touches.length === 2) {
      // 双指触摸 - 缩放手势
      const distance = getTouchDistance(touches[0], touches[1]);
      setGestureState(prev => ({
        ...prev,
        isPinching: true,
        initialPinchDistance: distance
      }));
    }
  }, []);
  
  const handleTouchMove = useCallback((e: TouchEvent) => {
    e.preventDefault(); // 防止页面滚动
    
    const touches = e.touches;
    
    if (gestureState.isSwping && touches.length === 1) {
      const deltaX = touches[0].clientX - gestureState.startX;
      const deltaY = touches[0].clientY - gestureState.startY;
      
      // 判断滑动方向
      if (Math.abs(deltaX) > Math.abs(deltaY) && Math.abs(deltaX) > 50) {
        const direction = deltaX > 0 ? 'right' : 'left';
        setGestureState(prev => ({
          ...prev,
          swipeDirection: direction
        }));
      }
    }
    
    if (gestureState.isPinching && touches.length === 2) {
      const distance = getTouchDistance(touches[0], touches[1]);
      const scale = distance / gestureState.initialPinchDistance;
      
      setGestureState(prev => ({
        ...prev,
        pinchScale: scale
      }));
    }
  }, [gestureState]);
  
  return {
    gestureState,
    touchHandlers: {
      onTouchStart: handleTouchStart,
      onTouchMove: handleTouchMove,
      onTouchEnd: handleTouchEnd
    }
  };
};
```

**移动端性能优化**：
```typescript
// 懒加载组件
const MobileDocumentViewer = lazy(() => 
  import('./MobileDocumentViewer').then(module => ({
    default: module.MobileDocumentViewer
  }))
);

// 虚拟滚动长列表
export const VirtualizedFileList: React.FC = ({ files }) => {
  const [visibleRange, setVisibleRange] = useState({ start: 0, end: 50 });
  const containerRef = useRef<HTMLDivElement>(null);
  
  const handleScroll = useCallback(
    throttle(() => {
      if (!containerRef.current) return;
      
      const { scrollTop, clientHeight } = containerRef.current;
      const itemHeight = 60; // 每个文件项的高度
      
      const start = Math.floor(scrollTop / itemHeight);
      const end = Math.min(start + Math.ceil(clientHeight / itemHeight) + 5, files.length);
      
      setVisibleRange({ start, end });
    }, 16), // ~60fps
    [files.length]
  );
  
  return (
    <div 
      ref={containerRef}
      className="file-list-container"
      onScroll={handleScroll}
    >
      <div style={{ height: files.length * 60 }}>
        {files.slice(visibleRange.start, visibleRange.end).map(renderFileItem)}
      </div>
    </div>
  );
};
```

**验收标准**：
- ✅ 所有页面在移动端正常显示
- ✅ 触摸交互流畅自然
- ✅ 移动端加载时间<3秒
- ✅ 支持横竖屏切换

---

### **4.2 深色模式支持**
**时间**：第13-13.5周 | **负责人**：前端开发工程师×1

#### 详细任务分解

**Sprint 1 (第13-13.5周)**
- **Day 1**: 主题系统架构设计
  - CSS变量主题系统
  - 主题切换逻辑设计
  - 用户偏好存储方案

- **Day 2-3**: 主题系统实现
  ```typescript
  src/styles/themes/
  ├── light.css                      // 浅色主题
  ├── dark.css                       // 深色主题
  ├── theme-variables.css            // 主题变量定义
  └── theme-utilities.css            // 主题工具类
  
  src/contexts/
  └── ThemeContext.tsx               // 主题上下文
  
  src/hooks/
  ├── useTheme.ts                    // 主题钩子
  ├── useSystemTheme.ts              // 系统主题检测
  └── useThemePreference.ts          // 主题偏好管理
  
  src/components/layout/
  └── ThemeToggle.tsx                // 主题切换组件
  ```

#### 技术实现细节

**CSS变量主题系统**：
```css
/* theme-variables.css */
:root {
  /* 浅色主题 */
  --color-background: #ffffff;
  --color-surface: #f8f9fa;
  --color-border: #e9ecef;
  --color-text-primary: #212529;
  --color-text-secondary: #6c757d;
  --color-primary: #0d6efd;
  --color-success: #198754;
  --color-warning: #ffc107;
  --color-danger: #dc3545;
  
  /* 阴影 */
  --shadow-sm: 0 1px 2px rgba(0, 0, 0, 0.05);
  --shadow-md: 0 4px 6px rgba(0, 0, 0, 0.07);
  --shadow-lg: 0 10px 15px rgba(0, 0, 0, 0.1);
}

/* 深色主题 */
[data-theme="dark"] {
  --color-background: #1a1a1a;
  --color-surface: #2d2d2d;
  --color-border: #404040;
  --color-text-primary: #ffffff;
  --color-text-secondary: #cccccc;
  --color-primary: #4dabf7;
  --color-success: #51cf66;
  --color-warning: #ffd43b;
  --color-danger: #ff6b6b;
  
  /* 深色模式阴影 */
  --shadow-sm: 0 1px 2px rgba(0, 0, 0, 0.3);
  --shadow-md: 0 4px 6px rgba(0, 0, 0, 0.4);
  --shadow-lg: 0 10px 15px rgba(0, 0, 0, 0.5);
}

/* 组件样式使用变量 */
.card {
  background-color: var(--color-surface);
  border: 1px solid var(--color-border);
  color: var(--color-text-primary);
  box-shadow: var(--shadow-md);
}

.button {
  background-color: var(--color-primary);
  color: var(--color-background);
  border: none;
  transition: all 0.2s ease-in-out;
}

.button:hover {
  background-color: var(--color-primary-hover, var(--color-primary));
  transform: translateY(-1px);
  box-shadow: var(--shadow-lg);
}
```

**主题上下文实现**：
```typescript
interface ThemeContextType {
  theme: 'light' | 'dark' | 'system';
  effectiveTheme: 'light' | 'dark';
  setTheme: (theme: 'light' | 'dark' | 'system') => void;
  toggleTheme: () => void;
}

export const ThemeProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [theme, setThemeState] = useState<'light' | 'dark' | 'system'>(() => {
    const stored = localStorage.getItem('theme');
    return (stored as any) || 'system';
  });
  
  const systemTheme = useSystemTheme(); // 检测系统主题
  
  const effectiveTheme = useMemo(() => {
    return theme === 'system' ? systemTheme : theme;
  }, [theme, systemTheme]);
  
  const setTheme = useCallback((newTheme: 'light' | 'dark' | 'system') => {
    setThemeState(newTheme);
    localStorage.setItem('theme', newTheme);
    
    // 应用到DOM
    if (newTheme === 'system') {
      document.documentElement.setAttribute('data-theme', systemTheme);
    } else {
      document.documentElement.setAttribute('data-theme', newTheme);
    }
  }, [systemTheme]);
  
  const toggleTheme = useCallback(() => {
    const newTheme = effectiveTheme === 'light' ? 'dark' : 'light';
    setTheme(newTheme);
  }, [effectiveTheme, setTheme]);
  
  // 监听系统主题变化
  useEffect(() => {
    if (theme === 'system') {
      document.documentElement.setAttribute('data-theme', systemTheme);
    }
  }, [theme, systemTheme]);
  
  return (
    <ThemeContext.Provider value={{
      theme,
      effectiveTheme,
      setTheme,
      toggleTheme
    }}>
      {children}
    </ThemeContext.Provider>
  );
};
```

**系统主题检测**：
```typescript
export const useSystemTheme = (): 'light' | 'dark' => {
  const [systemTheme, setSystemTheme] = useState<'light' | 'dark'>(() => {
    if (typeof window === 'undefined') return 'light';
    return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  });
  
  useEffect(() => {
    const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
    
    const handleChange = (e: MediaQueryListEvent) => {
      setSystemTheme(e.matches ? 'dark' : 'light');
    };
    
    mediaQuery.addEventListener('change', handleChange);
    
    return () => {
      mediaQuery.removeEventListener('change', handleChange);
    };
  }, []);
  
  return systemTheme;
};
```

**验收标准**：
- ✅ 完整的深色/浅色主题覆盖
- ✅ 自动检测系统主题偏好
- ✅ 主题切换动画流畅
- ✅ 用户偏好持久化存储

---

### **4.3 PWA离线功能**
**时间**：第13.5-14周 | **负责人**：前端开发工程师×1

#### 详细任务分解

**Sprint 1 (第13.5-14周)**
- **Day 1**: PWA配置和Service Worker
  ```typescript
  public/
  ├── manifest.json                  // PWA清单文件
  └── sw.js                         // Service Worker
  
  src/utils/
  ├── cacheStrategies.ts            // 缓存策略
  ├── syncManager.ts                // 后台同步管理
  └── offlineDetector.ts            // 离线检测
  
  src/hooks/
  ├── useOnlineStatus.ts            // 在线状态钩子
  ├── useServiceWorker.ts           // Service Worker钩子
  └── usePWAInstall.ts              // PWA安装钩子
  ```

- **Day 2-3**: 离线缓存和同步
  ```typescript
  src/components/offline/
  ├── OfflineIndicator.tsx          // 离线状态指示器
  ├── OfflineQueue.tsx              // 离线操作队列
  ├── CacheManager.tsx              // 缓存管理
  └── SyncStatus.tsx                // 同步状态显示
  ```

#### 技术实现细节

**PWA清单文件**：
```json
{
  "name": "Athena ECM",
  "short_name": "Athena",
  "description": "Enterprise Content Management System",
  "start_url": "/",
  "display": "standalone",
  "background_color": "#ffffff",
  "theme_color": "#0d6efd",
  "orientation": "portrait-primary",
  "categories": ["business", "productivity"],
  "icons": [
    {
      "src": "/icons/icon-72x72.png",
      "sizes": "72x72",
      "type": "image/png",
      "purpose": "maskable any"
    },
    {
      "src": "/icons/icon-192x192.png", 
      "sizes": "192x192",
      "type": "image/png",
      "purpose": "maskable any"
    },
    {
      "src": "/icons/icon-512x512.png",
      "sizes": "512x512", 
      "type": "image/png",
      "purpose": "maskable any"
    }
  ],
  "screenshots": [
    {
      "src": "/screenshots/desktop.png",
      "sizes": "1280x720",
      "type": "image/png",
      "form_factor": "wide"
    },
    {
      "src": "/screenshots/mobile.png",
      "sizes": "375x812", 
      "type": "image/png",
      "form_factor": "narrow"
    }
  ]
}
```

**Service Worker缓存策略**：
```typescript
// sw.js
const CACHE_NAME = 'athena-ecm-v1';
const STATIC_CACHE = 'athena-static-v1';
const API_CACHE = 'athena-api-v1';

// 缓存策略配置
const cacheStrategies = {
  // 静态资源 - Cache First
  static: [
    '/static/',
    '/icons/',
    '/fonts/',
    '/images/'
  ],
  
  // API请求 - Network First with fallback
  api: [
    '/api/v1/documents',
    '/api/v1/search',
    '/api/v1/user'
  ],
  
  // HTML页面 - Stale While Revalidate
  pages: [
    '/',
    '/documents',
    '/search'
  ]
};

self.addEventListener('install', (event) => {
  event.waitUntil(
    Promise.all([
      // 预缓存关键静态资源
      caches.open(STATIC_CACHE).then(cache => {
        return cache.addAll([
          '/',
          '/static/css/main.css',
          '/static/js/main.js',
          '/icons/icon-192x192.png'
        ]);
      })
    ])
  );
});

self.addEventListener('fetch', (event) => {
  const { request } = event;
  const url = new URL(request.url);
  
  // API请求 - Network First策略
  if (url.pathname.startsWith('/api/')) {
    event.respondWith(
      networkFirstStrategy(request, API_CACHE)
    );
    return;
  }
  
  // 静态资源 - Cache First策略  
  if (cacheStrategies.static.some(pattern => url.pathname.startsWith(pattern))) {
    event.respondWith(
      cacheFirstStrategy(request, STATIC_CACHE)
    );
    return;
  }
  
  // HTML页面 - Stale While Revalidate策略
  if (request.destination === 'document') {
    event.respondWith(
      staleWhileRevalidateStrategy(request, CACHE_NAME)
    );
    return;
  }
});

// Network First策略实现
async function networkFirstStrategy(request, cacheName) {
  try {
    const response = await fetch(request);
    
    if (response.ok) {
      const cache = await caches.open(cacheName);
      cache.put(request, response.clone());
    }
    
    return response;
  } catch (error) {
    const cache = await caches.open(cacheName);
    const cachedResponse = await cache.match(request);
    
    if (cachedResponse) {
      return cachedResponse;
    }
    
    // 返回离线页面或错误响应
    return new Response(
      JSON.stringify({ error: 'Offline', message: 'Network unavailable' }),
      {
        status: 503,
        statusText: 'Service Unavailable',
        headers: { 'Content-Type': 'application/json' }
      }
    );
  }
}
```

**离线操作队列**：
```typescript
interface OfflineOperation {
  id: string;
  type: 'upload' | 'update' | 'delete';
  endpoint: string;
  payload: any;
  timestamp: number;
  retryCount: number;
}

export class OfflineQueueManager {
  private queue: OfflineOperation[] = [];
  private isProcessing = false;
  
  constructor() {
    this.loadFromStorage();
    this.setupOnlineListener();
  }
  
  addOperation(operation: Omit<OfflineOperation, 'id' | 'timestamp' | 'retryCount'>) {
    const queueItem: OfflineOperation = {
      ...operation,
      id: generateId(),
      timestamp: Date.now(),
      retryCount: 0
    };
    
    this.queue.push(queueItem);
    this.saveToStorage();
    
    // 如果在线，立即尝试处理
    if (navigator.onLine && !this.isProcessing) {
      this.processQueue();
    }
  }
  
  private async processQueue() {
    if (this.isProcessing || this.queue.length === 0 || !navigator.onLine) {
      return;
    }
    
    this.isProcessing = true;
    
    while (this.queue.length > 0 && navigator.onLine) {
      const operation = this.queue[0];
      
      try {
        await this.executeOperation(operation);
        this.queue.shift(); // 成功后移除
        this.saveToStorage();
        
        // 通知用户操作已同步
        this.notifySync(operation);
      } catch (error) {
        operation.retryCount++;
        
        if (operation.retryCount >= 3) {
          // 重试次数过多，移除操作
          this.queue.shift();
          this.notifyError(operation, error);
        } else {
          // 延后重试
          setTimeout(() => this.processQueue(), 5000 * operation.retryCount);
          break;
        }
        
        this.saveToStorage();
      }
    }
    
    this.isProcessing = false;
  }
  
  private async executeOperation(operation: OfflineOperation) {
    const response = await fetch(operation.endpoint, {
      method: this.getHttpMethod(operation.type),
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${getAuthToken()}`
      },
      body: operation.payload ? JSON.stringify(operation.payload) : undefined
    });
    
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}: ${response.statusText}`);
    }
    
    return response.json();
  }
  
  private setupOnlineListener() {
    window.addEventListener('online', () => {
      if (this.queue.length > 0) {
        this.processQueue();
      }
    });
  }
}
```

**验收标准**：
- ✅ 离线状态下可浏览已缓存内容
- ✅ 离线操作自动排队和同步
- ✅ PWA可安装到设备桌面
- ✅ 离线指示器准确显示状态

---

## 📅 阶段五：集成扩展（第15-18周）

### 🔌 **总体目标**
扩展系统集成能力，支持主流企业应用和云服务

### 📊 **关键指标**
- Office 365集成成功率：>99%
- S3存储响应时间：<500ms
- 邮件导入准确率：>95%
- 集成API可用性：>99.9%

---

### **5.1 Microsoft Office 365集成**
**时间**：第15-16.5周 | **负责人**：后端开发工程师×2

#### 详细任务分解

**Sprint 1 (第15-15.5周)**
- **Day 1-2**: Microsoft Graph API集成
  - OAuth 2.0认证流程
  - Graph API客户端配置
  - 权限和安全策略

- **Day 3-5**: OneDrive集成开发
  ```java
  com/ecm/core/integration/office365/
  ├── GraphService.java               // Graph API服务
  ├── OneDriveService.java            // OneDrive集成服务
  ├── TeamsService.java               // Microsoft Teams集成
  ├── OutlookService.java             // Outlook邮件集成
  ├── SharePointService.java          // SharePoint集成
  └── Office365AuthService.java       // Office 365认证服务
  
  com/ecm/core/entity/
  ├── Office365Account.java           // Office 365账户
  ├── OneDriveFile.java              // OneDrive文件映射
  └── Office365SyncLog.java          // 同步日志
  ```

**Sprint 2 (第15.5-16周)**
- **Day 1-3**: 文档同步和协作
  ```java
  com/ecm/core/sync/
  ├── Office365SyncService.java       // 同步服务
  ├── ConflictResolutionService.java  // 冲突解决服务
  ├── FileComparisionService.java     // 文件比较服务
  └── SyncScheduler.java              // 同步调度器
  ```

**Sprint 3 (第16-16.5周)**
- **Day 1-3**: 管理界面和配置
  ```typescript
  src/components/integrations/office365/
  ├── Office365Settings.tsx          // Office 365设置
  ├── OneDriveSync.tsx               // OneDrive同步管理
  ├── TeamsIntegration.tsx           // Teams集成配置
  └── Office365Dashboard.tsx         // Office 365仪表板
  ```

#### 技术实现细节

**Microsoft Graph API集成**:
```java
@Service
public class MicrosoftGraphService {
    
    @Value("${office365.client-id}")
    private String clientId;
    
    @Value("${office365.client-secret}")
    private String clientSecret;
    
    private final RestTemplate restTemplate;
    
    public GraphServiceClient createGraphClient(String accessToken) {
        return GraphServiceClient.builder()
            .authenticationProvider(new AccessTokenProvider(accessToken))
            .buildClient();
    }
    
    public List<DriveItem> listOneDriveFiles(String userId, String folderId) {
        GraphServiceClient graphClient = createGraphClient(getAccessToken(userId));
        
        DriveItemCollectionResponse response = graphClient
            .users(userId)
            .drive()
            .items(folderId)
            .children()
            .get();
            
        return response.getValue();
    }
    
    public byte[] downloadFile(String userId, String fileId) {
        GraphServiceClient graphClient = createGraphClient(getAccessToken(userId));
        
        InputStream fileStream = graphClient
            .users(userId)
            .drive()
            .items(fileId)
            .content()
            .get();
            
        return IOUtils.toByteArray(fileStream);
    }
    
    public DriveItem uploadFile(String userId, String parentId, 
                               String fileName, byte[] content) {
        GraphServiceClient graphClient = createGraphClient(getAccessToken(userId));
        
        return graphClient
            .users(userId)
            .drive()
            .items(parentId)
            .children(fileName)
            .content()
            .put(content);
    }
}
```

**双向同步机制**:
```java
@Service
public class Office365SyncService {
    
    @Scheduled(fixedRate = 300000) // 每5分钟同步
    public void syncFiles() {
        List<Office365Account> accounts = office365AccountRepository.findActive();
        
        for (Office365Account account : accounts) {
            try {
                syncUserFiles(account);
            } catch (Exception e) {
                log.error("Sync failed for account: " + account.getId(), e);
            }
        }
    }
    
    private void syncUserFiles(Office365Account account) {
        // 获取OneDrive文件列表
        List<DriveItem> oneDriveFiles = graphService.listOneDriveFiles(
            account.getUserId(), account.getSyncFolderId());
            
        // 获取ECM中的映射文件
        List<OneDriveFile> ecmFiles = oneDriveFileRepository
            .findByAccountId(account.getId());
            
        // 检测新文件和更新
        for (DriveItem driveItem : oneDriveFiles) {
            OneDriveFile ecmFile = ecmFiles.stream()
                .filter(f -> f.getOneDriveId().equals(driveItem.getId()))
                .findFirst()
                .orElse(null);
                
            if (ecmFile == null) {
                // 新文件 - 从OneDrive导入到ECM
                importFromOneDrive(account, driveItem);
            } else if (isFileUpdated(driveItem, ecmFile)) {
                // 文件已更新 - 处理冲突
                handleFileConflict(account, driveItem, ecmFile);
            }
        }
        
        // 检测ECM中的新文件需要上传到OneDrive
        syncToOneDrive(account, ecmFiles);
    }
    
    private void handleFileConflict(Office365Account account, 
                                   DriveItem driveItem, 
                                   OneDriveFile ecmFile) {
        ConflictResolution resolution = account.getConflictResolution();
        
        switch (resolution) {
            case ONEDRIVE_WINS:
                updateFromOneDrive(account, driveItem, ecmFile);
                break;
            case ECM_WINS:
                uploadToOneDrive(account, ecmFile);
                break;
            case CREATE_VERSION:
                createConflictVersion(account, driveItem, ecmFile);
                break;
            case MANUAL_REVIEW:
                createConflictTask(account, driveItem, ecmFile);
                break;
        }
    }
}
```

**验收标准**：
- ✅ OAuth 2.0认证流程完整
- ✅ OneDrive文件双向同步
- ✅ Teams消息和文件集成
- ✅ 冲突解决机制可靠

---

### **5.2 AWS S3存储支持**
**时间**：第16.5-17.5周 | **负责人**：后端开发工程师×1

#### 详细任务分解

**Sprint 1 (第16.5-17周)**
- **Day 1**: AWS S3集成设计
  - 存储适配器架构
  - 多区域部署策略
  - 生命周期管理配置

- **Day 2-3**: S3存储服务开发
  ```java
  com/ecm/core/storage/
  ├── StorageProvider.java (接口)     // 存储提供者接口
  ├── S3StorageService.java          // S3存储实现
  ├── S3ConfigurationService.java    // S3配置管理
  ├── S3LifecycleService.java        // 生命周期管理
  └── StorageMetricsService.java     // 存储指标服务
  
  com/ecm/core/storage/s3/
  ├── S3ClientFactory.java           // S3客户端工厂
  ├── S3BucketManager.java           // 存储桶管理
  ├── S3ObjectManager.java           // 对象管理
  └── S3SecurityManager.java         // S3安全管理
  ```

**Sprint 2 (第17-17.5周)**
- **Day 1-3**: 存储策略和优化
  ```java
  com/ecm/core/storage/strategy/
  ├── TieredStorageStrategy.java      // 分层存储策略
  ├── CostOptimizationStrategy.java  // 成本优化策略
  ├── BackupStrategy.java             // 备份策略
  └── ArchivalStrategy.java           // 归档策略
  ```

#### 技术实现细节

**S3存储适配器**:
```java
@Service
@ConditionalOnProperty(value = "storage.provider", havingValue = "s3")
public class S3StorageService implements StorageProvider {
    
    private final AmazonS3 s3Client;
    private final S3ConfigurationProperties s3Config;
    
    @Override
    public String storeFile(String key, InputStream inputStream, 
                           String contentType, long contentLength) {
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType(contentType);
        metadata.setContentLength(contentLength);
        metadata.addUserMetadata("stored-by", "athena-ecm");
        metadata.addUserMetadata("stored-at", Instant.now().toString());
        
        // 选择合适的存储类型
        StorageClass storageClass = determineStorageClass(contentLength);
        metadata.setStorageClass(storageClass);
        
        try {
            PutObjectRequest request = new PutObjectRequest(
                s3Config.getBucket(), key, inputStream, metadata);
                
            // 启用服务端加密
            request.withSSESpecification(new SSESpecification()
                .withSSEAlgorithm(SSEAlgorithm.AES256));
                
            PutObjectResult result = s3Client.putObject(request);
            
            return generateFileUrl(key);
        } catch (Exception e) {
            throw new StorageException("Failed to store file in S3", e);
        }
    }
    
    @Override
    public InputStream retrieveFile(String key) {
        try {
            GetObjectRequest request = new GetObjectRequest(
                s3Config.getBucket(), key);
                
            S3Object s3Object = s3Client.getObject(request);
            return s3Object.getObjectContent();
        } catch (Exception e) {
            throw new StorageException("Failed to retrieve file from S3", e);
        }
    }
    
    @Override
    public void deleteFile(String key) {
        try {
            s3Client.deleteObject(s3Config.getBucket(), key);
        } catch (Exception e) {
            throw new StorageException("Failed to delete file from S3", e);
        }
    }
    
    private StorageClass determineStorageClass(long fileSize) {
        if (fileSize < 128 * 1024) { // < 128KB
            return StorageClass.Standard;
        } else if (fileSize < 1024 * 1024) { // < 1MB
            return StorageClass.StandardInfrequentAccess;
        } else {
            return StorageClass.Glacier;
        }
    }
}
```

**分层存储策略**:
```java
@Component
public class S3LifecycleService {
    
    @Scheduled(cron = "0 0 2 * * ?") // 每天凌晨2点执行
    public void applyLifecycleRules() {
        List<LifecycleRule> rules = createLifecycleRules();
        
        BucketLifecycleConfiguration configuration = 
            new BucketLifecycleConfiguration(rules);
            
        s3Client.setBucketLifecycleConfiguration(
            s3Config.getBucket(), configuration);
    }
    
    private List<LifecycleRule> createLifecycleRules() {
        return Arrays.asList(
            // 30天后转为IA存储
            new LifecycleRule()
                .withId("transition-to-ia")
                .withFilter(new LifecycleFilter())
                .withStatus(BucketLifecycleConfiguration.ENABLED)
                .withTransitions(new LifecycleRule.Transition()
                    .withDays(30)
                    .withStorageClass(StorageClass.StandardInfrequentAccess)),
                    
            // 90天后转为Glacier
            new LifecycleRule()
                .withId("transition-to-glacier")
                .withFilter(new LifecycleFilter())
                .withStatus(BucketLifecycleConfiguration.ENABLED)
                .withTransitions(new LifecycleRule.Transition()
                    .withDays(90)
                    .withStorageClass(StorageClass.Glacier)),
                    
            // 365天后转为Deep Archive
            new LifecycleRule()
                .withId("transition-to-deep-archive")
                .withFilter(new LifecycleFilter())
                .withStatus(BucketLifecycleConfiguration.ENABLED)
                .withTransitions(new LifecycleRule.Transition()
                    .withDays(365)
                    .withStorageClass(StorageClass.DeepArchive))
        );
    }
}
```

**验收标准**：
- ✅ S3存储完全替换本地存储
- ✅ 自动分层存储优化成本
- ✅ 跨区域备份和灾难恢复
- ✅ 存储成本监控和报告

---

### **5.3 邮件系统集成**
**时间**：第17.5-18周 | **负责人**：后端开发工程师×1

#### 详细任务分解

**Sprint 1 (第17.5-18周)**
- **Day 1**: 邮件集成架构设计
  - IMAP/SMTP协议支持
  - 邮件解析和附件提取
  - 邮件归档策略

- **Day 2-3**: 邮件服务开发
  ```java
  com/ecm/core/integration/email/
  ├── EmailIngestionService.java      // 邮件摄入服务
  ├── ImapService.java               // IMAP服务
  ├── SmtpService.java               // SMTP服务  
  ├── EmailParserService.java        // 邮件解析服务
  ├── AttachmentExtractorService.java // 附件提取服务
  └── EmailArchivalService.java      // 邮件归档服务
  
  com/ecm/core/entity/
  ├── EmailAccount.java              // 邮件账户
  ├── EmailMessage.java              // 邮件消息
  ├── EmailAttachment.java           // 邮件附件
  └── EmailSyncLog.java              // 邮件同步日志
  ```

#### 技术实现细节

**IMAP邮件摄入**:
```java
@Service
public class EmailIngestionService {
    
    @Scheduled(fixedRate = 600000) // 每10分钟检查
    public void ingestEmails() {
        List<EmailAccount> accounts = emailAccountRepository.findActive();
        
        for (EmailAccount account : accounts) {
            try {
                processEmailAccount(account);
            } catch (Exception e) {
                log.error("Email ingestion failed for account: " + 
                         account.getEmailAddress(), e);
            }
        }
    }
    
    private void processEmailAccount(EmailAccount account) {
        Properties props = new Properties();
        props.setProperty("mail.store.protocol", "imaps");
        props.setProperty("mail.imaps.host", account.getImapHost());
        props.setProperty("mail.imaps.port", String.valueOf(account.getImapPort()));
        
        try {
            Session session = Session.getInstance(props);
            Store store = session.getStore("imaps");
            store.connect(account.getEmailAddress(), account.getPassword());
            
            Folder inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_WRITE);
            
            // 获取未处理的邮件
            Message[] messages = inbox.search(new FlagTerm(
                new Flags(Flags.Flag.SEEN), false));
                
            for (Message message : messages) {
                processEmailMessage(account, message);
                message.setFlag(Flags.Flag.SEEN, true);
            }
            
            inbox.close(false);
            store.close();
        } catch (Exception e) {
            throw new EmailProcessingException(
                "Failed to process email account", e);
        }
    }
    
    private void processEmailMessage(EmailAccount account, Message message) 
            throws Exception {
        // 解析邮件内容
        EmailMessage emailMsg = parseEmailMessage(message);
        emailMsg.setAccountId(account.getId());
        
        // 保存邮件记录
        emailMsg = emailMessageRepository.save(emailMsg);
        
        // 提取和保存附件
        extractAndSaveAttachments(message, emailMsg.getId());
        
        // 根据规则归档邮件
        archiveEmailIfNeeded(emailMsg);
        
        // 发送通知
        notifyEmailReceived(emailMsg);
    }
    
    private void extractAndSaveAttachments(Message message, UUID emailId) 
            throws Exception {
        if (message.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) message.getContent();
            
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart bodyPart = multipart.getBodyPart(i);
                
                if (Part.ATTACHMENT.equalsIgnoreCase(
                        bodyPart.getDisposition())) {
                    saveEmailAttachment(bodyPart, emailId);
                }
            }
        }
    }
    
    private void saveEmailAttachment(BodyPart bodyPart, UUID emailId) 
            throws Exception {
        String filename = bodyPart.getFileName();
        InputStream inputStream = bodyPart.getInputStream();
        
        // 创建文档记录
        Document document = new Document();
        document.setName(filename);
        document.setMimeType(bodyPart.getContentType());
        document.setSource("EMAIL_ATTACHMENT");
        
        // 保存文档内容
        String contentKey = contentService.store(inputStream, 
                                               bodyPart.getContentType());
        document.setContentKey(contentKey);
        
        document = documentRepository.save(document);
        
        // 创建邮件附件记录
        EmailAttachment attachment = new EmailAttachment();
        attachment.setEmailId(emailId);
        attachment.setDocumentId(document.getId());
        attachment.setFilename(filename);
        
        emailAttachmentRepository.save(attachment);
    }
}
```

**验收标准**：
- ✅ 支持主流邮件服务器(IMAP/SMTP)
- ✅ 自动提取和归档邮件附件
- ✅ 邮件内容全文检索
- ✅ 邮件归档规则配置

---

## 📅 阶段六：分析报表（第19-21周）

### 📊 **总体目标**
构建完整的数据分析和报表系统

### 📊 **关键指标**
- 报表生成时间：<30秒
- 仪表板响应时间：<2秒
- 数据准确率：>99.9%
- 用户报表使用率：>70%

---

### **6.1 使用分析仪表板**
**时间**：第19-19.5周 | **负责人**：后端开发工程师×1，前端开发工程师×1

#### 详细任务分解

**Sprint 1 (第19-19.5周)**
- **Day 1**: 分析架构设计
  - 数据仓库设计
  - 实时数据流处理
  - 分析指标定义

- **Day 2-3**: 分析服务开发
  ```java
  com/ecm/core/analytics/
  ├── AnalyticsService.java          // 分析服务
  ├── UserActivityTracker.java       // 用户活动追踪
  ├── DocumentUsageAnalyzer.java     // 文档使用分析
  ├── PerformanceMetricsService.java // 性能指标服务
  ├── UsageReportGenerator.java      // 使用报告生成器
  └── AnalyticsDataCollector.java    // 分析数据收集器
  ```

#### 技术实现细节

**实时数据收集**:
```java
@Component
@EventListener
public class AnalyticsEventListener {
    
    private final AnalyticsDataCollector dataCollector;
    
    @EventListener
    @Async
    public void handleDocumentEvent(DocumentEvent event) {
        AnalyticsEvent analyticsEvent = AnalyticsEvent.builder()
            .eventType(event.getType().name())
            .userId(event.getUserId())
            .documentId(event.getDocumentId())
            .timestamp(event.getTimestamp())
            .metadata(event.getMetadata())
            .build();
            
        dataCollector.collect(analyticsEvent);
    }
    
    @EventListener
    @Async
    public void handleUserEvent(UserEvent event) {
        // 处理用户事件
        AnalyticsEvent analyticsEvent = AnalyticsEvent.builder()
            .eventType("USER_" + event.getType().name())
            .userId(event.getUserId())
            .timestamp(event.getTimestamp())
            .sessionId(event.getSessionId())
            .ipAddress(event.getIpAddress())
            .userAgent(event.getUserAgent())
            .build();
            
        dataCollector.collect(analyticsEvent);
    }
}
```

**仪表板数据API**:
```java
@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {
    
    @GetMapping("/dashboard")
    public DashboardData getDashboardData(
            @RequestParam(defaultValue = "30") int days) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days);
        
        return DashboardData.builder()
            .totalUsers(userAnalyzer.getTotalActiveUsers(startDate, endDate))
            .totalDocuments(documentAnalyzer.getTotalDocuments())
            .storageUsage(storageAnalyzer.getCurrentUsage())
            .dailyActivity(activityAnalyzer.getDailyActivity(startDate, endDate))
            .topDocuments(documentAnalyzer.getTopAccessedDocuments(10))
            .userGrowth(userAnalyzer.getUserGrowthTrend(startDate, endDate))
            .systemHealth(healthAnalyzer.getSystemHealthMetrics())
            .build();
    }
    
    @GetMapping("/users/{userId}/activity")
    public UserActivityReport getUserActivity(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "30") int days) {
        return userAnalyzer.generateUserActivityReport(userId, days);
    }
    
    @GetMapping("/documents/usage")
    public DocumentUsageReport getDocumentUsage(
            @RequestParam(defaultValue = "30") int days) {
        return documentAnalyzer.generateUsageReport(days);
    }
}
```

**前端仪表板组件**:
```typescript
export const AnalyticsDashboard: React.FC = () => {
  const [timeRange, setTimeRange] = useState(30);
  const [dashboardData, setDashboardData] = useState<DashboardData | null>(null);
  const [loading, setLoading] = useState(true);
  
  useEffect(() => {
    const fetchDashboardData = async () => {
      setLoading(true);
      try {
        const data = await analyticsService.getDashboardData(timeRange);
        setDashboardData(data);
      } catch (error) {
        console.error('Failed to fetch dashboard data:', error);
      } finally {
        setLoading(false);
      }
    };
    
    fetchDashboardData();
    
    // 设置自动刷新
    const interval = setInterval(fetchDashboardData, 60000); // 每分钟刷新
    
    return () => clearInterval(interval);
  }, [timeRange]);
  
  if (loading || !dashboardData) {
    return <LoadingSpinner />;
  }
  
  return (
    <div className="analytics-dashboard">
      <DashboardHeader 
        timeRange={timeRange} 
        onTimeRangeChange={setTimeRange} 
      />
      
      <div className="metrics-grid">
        <MetricCard 
          title="活跃用户" 
          value={dashboardData.totalUsers}
          trend={dashboardData.userGrowth}
          icon={<UsersIcon />}
        />
        <MetricCard 
          title="文档总数" 
          value={dashboardData.totalDocuments}
          icon={<DocumentIcon />}
        />
        <MetricCard 
          title="存储使用" 
          value={formatBytes(dashboardData.storageUsage)}
          icon={<StorageIcon />}
        />
        <MetricCard 
          title="系统健康度" 
          value={`${dashboardData.systemHealth.score}%`}
          status={dashboardData.systemHealth.status}
          icon={<HealthIcon />}
        />
      </div>
      
      <div className="charts-grid">
        <ChartCard title="每日活动">
          <LineChart data={dashboardData.dailyActivity} />
        </ChartCard>
        
        <ChartCard title="热门文档">
          <BarChart data={dashboardData.topDocuments} />
        </ChartCard>
        
        <ChartCard title="用户增长">
          <AreaChart data={dashboardData.userGrowth} />
        </ChartCard>
        
        <ChartCard title="存储趋势">
          <LineChart data={dashboardData.storageTrend} />
        </ChartCard>
      </div>
    </div>
  );
};
```

**验收标准**：
- ✅ 实时数据展示和自动刷新
- ✅ 可交互的图表和筛选器
- ✅ 移动端友好的响应式设计
- ✅ 数据导出和分享功能

---

### **6.2 合规性报告生成器**
**时间**：第19.5-20.5周 | **负责人**：后端开发工程师×1

#### 详细任务分解

**Sprint 1 (第19.5-20周)**
- **Day 1-2**: 合规框架设计
  - GDPR合规检查器
  - HIPAA合规验证
  - SOX审计支持

- **Day 3-5**: 报告生成服务
  ```java
  com/ecm/core/compliance/
  ├── ComplianceReportService.java    // 合规报告服务
  ├── GdprComplianceChecker.java     // GDPR合规检查器
  ├── HipaaComplianceChecker.java    // HIPAA合规检查器
  ├── SoxComplianceChecker.java      // SOX合规检查器
  ├── ComplianceRuleEngine.java      // 合规规则引擎
  └── ComplianceAuditService.java    // 合规审计服务
  ```

**Sprint 2 (第20-20.5周)**
- **Day 1-3**: 报告模板和生成
  ```java
  com/ecm/core/reports/
  ├── ReportTemplateService.java      // 报告模板服务
  ├── PdfReportGenerator.java         // PDF报告生成器
  ├── ExcelReportGenerator.java       // Excel报告生成器
  ├── ReportScheduler.java            // 报告调度器
  └── ReportDeliveryService.java      // 报告投递服务
  ```

#### 技术实现细节

**GDPR合规检查器**:
```java
@Service
public class GdprComplianceChecker implements ComplianceChecker {
    
    @Override
    public ComplianceReport generateComplianceReport(DateRange dateRange) {
        GdprComplianceReport report = new GdprComplianceReport();
        
        // 检查个人数据处理记录
        report.setPersonalDataProcessingRecords(
            checkPersonalDataProcessing(dateRange));
            
        // 检查数据主体权利请求处理
        report.setDataSubjectRightsRequests(
            checkDataSubjectRights(dateRange));
            
        // 检查数据泄露事件
        report.setDataBreachIncidents(
            checkDataBreaches(dateRange));
            
        // 检查数据保护影响评估
        report.setDataProtectionImpactAssessments(
            checkDPIAs(dateRange));
            
        // 检查跨境数据传输
        report.setCrossBorderDataTransfers(
            checkCrossBorderTransfers(dateRange));
            
        // 计算合规分数
        report.setComplianceScore(calculateComplianceScore(report));
        
        return report;
    }
    
    private PersonalDataProcessingRecord checkPersonalDataProcessing(
            DateRange dateRange) {
        // 检查是否所有个人数据处理都有合法基础
        List<Document> personalDataDocuments = documentRepository
            .findPersonalDataDocuments(dateRange.getStart(), dateRange.getEnd());
            
        long documentsWithLegalBasis = personalDataDocuments.stream()
            .filter(doc -> hasLegalBasisForProcessing(doc))
            .count();
            
        return PersonalDataProcessingRecord.builder()
            .totalDocuments(personalDataDocuments.size())
            .documentsWithLegalBasis((int) documentsWithLegalBasis)
            .compliancePercentage(
                (double) documentsWithLegalBasis / personalDataDocuments.size() * 100)
            .recommendations(generateProcessingRecommendations(personalDataDocuments))
            .build();
    }
    
    private DataSubjectRightsRecord checkDataSubjectRights(DateRange dateRange) {
        // 检查数据主体权利请求的处理时效
        List<DataSubjectRequest> requests = dataSubjectRequestRepository
            .findByDateRange(dateRange.getStart(), dateRange.getEnd());
            
        long onTimeRequests = requests.stream()
            .filter(this::isProcessedOnTime)
            .count();
            
        return DataSubjectRightsRecord.builder()
            .totalRequests(requests.size())
            .onTimeRequests((int) onTimeRequests)
            .averageResponseTime(calculateAverageResponseTime(requests))
            .compliancePercentage(
                (double) onTimeRequests / requests.size() * 100)
            .build();
    }
}
```

**报告生成和调度**:
```java
@Service
public class ComplianceReportService {
    
    @Scheduled(cron = "0 0 9 1 * ?") // 每月1日上午9点
    public void generateMonthlyComplianceReports() {
        LocalDate endDate = LocalDate.now().minusDays(1);
        LocalDate startDate = endDate.minusMonths(1).withDayOfMonth(1);
        
        DateRange dateRange = new DateRange(startDate, endDate);
        
        // 生成所有启用的合规报告
        List<ComplianceStandard> enabledStandards = 
            complianceConfigRepository.findEnabled();
            
        for (ComplianceStandard standard : enabledStandards) {
            try {
                generateAndDeliverReport(standard, dateRange);
            } catch (Exception e) {
                log.error("Failed to generate compliance report for: " + 
                         standard.getName(), e);
            }
        }
    }
    
    public void generateAndDeliverReport(ComplianceStandard standard, 
                                       DateRange dateRange) {
        // 选择合适的合规检查器
        ComplianceChecker checker = getComplianceChecker(standard);
        
        // 生成合规报告
        ComplianceReport report = checker.generateComplianceReport(dateRange);
        
        // 生成PDF文档
        byte[] pdfContent = pdfReportGenerator.generateComplianceReport(
            report, standard);
            
        // 保存报告记录
        ComplianceReportRecord recordEntity = new ComplianceReportRecord();
        recordEntity.setStandard(standard);
        recordEntity.setDateRange(dateRange);
        recordEntity.setComplianceScore(report.getComplianceScore());
        recordEntity.setReportPath(saveReportFile(pdfContent, standard, dateRange));
        recordEntity.setGeneratedAt(LocalDateTime.now());
        
        complianceReportRepository.save(recordEntity);
        
        // 发送报告给相关人员
        deliverReport(recordEntity, pdfContent);
    }
    
    private void deliverReport(ComplianceReportRecord report, byte[] content) {
        List<String> recipients = getReportRecipients(report.getStandard());
        
        EmailMessage email = EmailMessage.builder()
            .to(recipients)
            .subject(String.format("合规报告 - %s (%s)", 
                    report.getStandard().getName(), 
                    report.getDateRange().toString()))
            .body(generateReportEmailBody(report))
            .attachment("compliance-report.pdf", content)
            .build();
            
        emailService.send(email);
    }
}
```

**验收标准**：
- ✅ 支持GDPR/HIPAA/SOX等主要合规标准
- ✅ 自动化合规检查和评分
- ✅ 定期生成和分发合规报告
- ✅ 合规问题预警和建议

---

### **6.3 存储成本分析工具**
**时间**：第20.5-21周 | **负责人**：后端开发工程师×1

#### 详细任务分解

**Sprint 1 (第20.5-21周)**
- **Day 1**: 成本分析架构
  - 存储使用量统计
  - 成本计算模型
  - 优化建议引擎

- **Day 2-3**: 成本分析服务
  ```java
  com/ecm/core/analytics/
  ├── StorageCostAnalyzer.java        // 存储成本分析器
  ├── UsagePredictor.java             // 使用量预测器
  ├── CostOptimizer.java              // 成本优化器
  ├── StorageMetricsCollector.java    // 存储指标收集器
  └── CostReportGenerator.java        // 成本报告生成器
  ```

#### 技术实现细节

**存储成本分析**:
```java
@Service
public class StorageCostAnalyzer {
    
    public StorageCostReport generateCostReport(DateRange dateRange) {
        // 收集存储使用数据
        StorageUsageData usageData = collectStorageUsage(dateRange);
        
        // 计算各种存储成本
        StorageCosts costs = calculateStorageCosts(usageData);
        
        // 生成优化建议
        List<CostOptimizationRecommendation> recommendations = 
            generateOptimizationRecommendations(usageData, costs);
            
        // 预测未来成本
        CostPrediction prediction = predictFutureCosts(usageData);
        
        return StorageCostReport.builder()
            .dateRange(dateRange)
            .usageData(usageData)
            .costs(costs)
            .recommendations(recommendations)
            .prediction(prediction)
            .build();
    }
    
    private StorageUsageData collectStorageUsage(DateRange dateRange) {
        // 按存储类型统计使用量
        Map<StorageType, Long> usageByType = documentRepository
            .calculateStorageUsageByType(dateRange.getStart(), dateRange.getEnd());
            
        // 按部门统计使用量  
        Map<String, Long> usageByDepartment = documentRepository
            .calculateStorageUsageByDepartment(
                dateRange.getStart(), dateRange.getEnd());
                
        // 按文件类型统计使用量
        Map<String, Long> usageByMimeType = documentRepository
            .calculateStorageUsageByMimeType(
                dateRange.getStart(), dateRange.getEnd());
                
        return StorageUsageData.builder()
            .totalUsage(usageByType.values().stream().mapToLong(Long::longValue).sum())
            .usageByType(usageByType)
            .usageByDepartment(usageByDepartment)
            .usageByMimeType(usageByMimeType)
            .duplicateFiles(findDuplicateFiles())
            .unusedFiles(findUnusedFiles(dateRange))
            .build();
    }
    
    private List<CostOptimizationRecommendation> generateOptimizationRecommendations(
            StorageUsageData usage, StorageCosts costs) {
        List<CostOptimizationRecommendation> recommendations = new ArrayList<>();
        
        // 推荐删除重复文件
        if (!usage.getDuplicateFiles().isEmpty()) {
            long duplicateSize = usage.getDuplicateFiles().stream()
                .mapToLong(DuplicateFile::getTotalSize)
                .sum();
            double savingsAmount = calculateStorageCost(duplicateSize) * 12; // 年度节省
            
            recommendations.add(CostOptimizationRecommendation.builder()
                .type(OptimizationType.REMOVE_DUPLICATES)
                .title("删除重复文件")
                .description(String.format("发现 %d 个重复文件，总大小 %s", 
                           usage.getDuplicateFiles().size(),
                           formatBytes(duplicateSize)))
                .potentialSavings(savingsAmount)
                .impact(RecommendationImpact.MEDIUM)
                .effort(RecommendationEffort.LOW)
                .build());
        }
        
        // 推荐归档旧文件
        List<Document> oldFiles = findOldAccessFiles(usage);
        if (!oldFiles.isEmpty()) {
            long archiveSize = oldFiles.stream()
                .mapToLong(Document::getSize)
                .sum();
            double savingsAmount = calculateArchiveSavings(archiveSize);
            
            recommendations.add(CostOptimizationRecommendation.builder()
                .type(OptimizationType.ARCHIVE_OLD_FILES)
                .title("归档旧文件")
                .description(String.format("建议将 %d 个超过90天未访问的文件归档", 
                           oldFiles.size()))
                .potentialSavings(savingsAmount)
                .impact(RecommendationImpact.HIGH)
                .effort(RecommendationEffort.LOW)
                .build());
        }
        
        // 推荐存储层级优化
        if (isStorageTierOptimizationNeeded(usage)) {
            recommendations.add(CostOptimizationRecommendation.builder()
                .type(OptimizationType.OPTIMIZE_STORAGE_TIERS)
                .title("优化存储层级")
                .description("启用自动存储层级转换可节省存储成本")
                .potentialSavings(calculateTierOptimizationSavings(usage))
                .impact(RecommendationImpact.HIGH)
                .effort(RecommendationEffort.MEDIUM)
                .build());
        }
        
        return recommendations;
    }
}
```

**验收标准**：
- ✅ 详细的存储成本分解和趋势分析
- ✅ 基于使用模式的成本优化建议
- ✅ 未来存储成本预测和预算规划
- ✅ 部门级别的存储成本分摊

---

## 📅 阶段七：AI增强（第22-25周）

### 🤖 **总体目标**
引入AI技术提升内容管理的智能化水平

### 📊 **关键指标**
- 语义搜索准确率：>85%
- 自动分类准确率：>80%
- 推荐系统点击率：>15%
- AI功能用户采用率：>60%

---

### **7.1 语义搜索功能**
**时间**：第22-23周 | **负责人**：后端开发工程师×2

#### 详细任务分解

**Sprint 1 (第22-22.5周)**
- **Day 1**: 语义搜索架构设计
  - 向量数据库选型(Qdrant/Weaviate)
  - 文档嵌入生成管道
  - 语义相似度算法

- **Day 2-3**: 向量化和索引服务
  ```java
  com/ecm/core/ai/
  ├── SemanticSearchService.java      // 语义搜索服务
  ├── VectorEmbeddingService.java     // 向量嵌入服务
  ├── DocumentVectorizer.java         // 文档向量化器
  ├── SimilarityCalculator.java       // 相似度计算器
  ├── QueryExpansionService.java      // 查询扩展服务
  └── SemanticIndexManager.java       // 语义索引管理器
  ```

**Sprint 2 (第22.5-23周)**
- **Day 1-3**: 搜索优化和集成
  ```java
  com/ecm/core/search/
  ├── HybridSearchService.java        // 混合搜索服务
  ├── SearchResultRanker.java         // 搜索结果排序器
  ├── QueryUnderstandingService.java  // 查询理解服务
  └── SearchAnalyticsService.java     // 搜索分析服务
  ```

#### 技术实现细节

**文档向量化服务**:
```java
@Service
public class DocumentVectorizer {
    
    private final OpenAiEmbeddingClient embeddingClient;
    private final VectorDatabase vectorDatabase;
    
    public void vectorizeDocument(Document document) {
        try {
            // 提取文档文本内容
            String textContent = extractTextContent(document);
            
            // 文本预处理
            List<String> chunks = chunkText(textContent, 1000); // 1000字符一块
            
            // 为每个文本块生成向量嵌入
            List<DocumentVector> vectors = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                String chunk = chunks.get(i);
                
                // 调用嵌入API生成向量
                float[] embedding = embeddingClient.createEmbedding(chunk);
                
                DocumentVector vector = DocumentVector.builder()
                    .documentId(document.getId())
                    .chunkIndex(i)
                    .content(chunk)
                    .embedding(embedding)
                    .metadata(createMetadata(document, i))
                    .build();
                    
                vectors.add(vector);
            }
            
            // 存储到向量数据库
            vectorDatabase.upsert(vectors);
            
            // 更新文档索引状态
            document.setVectorIndexed(true);
            documentRepository.save(document);
            
        } catch (Exception e) {
            log.error("Failed to vectorize document: " + document.getId(), e);
        }
    }
    
    private List<String> chunkText(String text, int chunkSize) {
        List<String> chunks = new ArrayList<>();
        
        // 按段落分割，避免破坏语义
        String[] paragraphs = text.split("\n\n");
        StringBuilder currentChunk = new StringBuilder();
        
        for (String paragraph : paragraphs) {
            if (currentChunk.length() + paragraph.length() > chunkSize) {
                if (currentChunk.length() > 0) {
                    chunks.add(currentChunk.toString().trim());
                    currentChunk = new StringBuilder();
                }
            }
            
            currentChunk.append(paragraph).append("\n\n");
        }
        
        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString().trim());
        }
        
        return chunks;
    }
    
    private Map<String, Object> createMetadata(Document document, int chunkIndex) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("documentId", document.getId().toString());
        metadata.put("documentName", document.getName());
        metadata.put("mimeType", document.getMimeType());
        metadata.put("createdBy", document.getCreatedBy().toString());
        metadata.put("createdAt", document.getCreatedAt().toString());
        metadata.put("chunkIndex", chunkIndex);
        
        // 添加标签信息
        if (document.getTags() != null && !document.getTags().isEmpty()) {
            metadata.put("tags", document.getTags().stream()
                .map(Tag::getName)
                .collect(Collectors.toList()));
        }
        
        return metadata;
    }
}
```

**语义搜索服务**:
```java
@Service
public class SemanticSearchService {
    
    private final VectorEmbeddingService embeddingService;
    private final VectorDatabase vectorDatabase;
    private final ElasticsearchClient elasticsearchClient;
    
    public SemanticSearchResult search(SemanticSearchRequest request) {
        // 生成查询向量
        float[] queryVector = embeddingService.generateQueryEmbedding(request.getQuery());
        
        // 向量相似度搜索
        List<VectorMatch> vectorMatches = vectorDatabase.similaritySearch(
            queryVector, 
            request.getLimit() * 2, // 获取更多候选结果
            request.getMinSimilarity());
            
        // 如果启用混合搜索，结合传统文本搜索
        if (request.isHybridSearchEnabled()) {
            return performHybridSearch(request, vectorMatches);
        }
        
        // 纯语义搜索结果处理
        return processSemanticResults(vectorMatches, request);
    }
    
    private SemanticSearchResult performHybridSearch(
            SemanticSearchRequest request, 
            List<VectorMatch> vectorMatches) {
        // 传统关键词搜索
        SearchResponse keywordResults = elasticsearchClient.search(
            buildKeywordQuery(request));
            
        // 结果融合和重排序
        List<SearchResult> fusedResults = fuseSearchResults(
            vectorMatches, keywordResults, request);
            
        // 应用业务规则重排序
        List<SearchResult> rankedResults = applyBusinessRanking(
            fusedResults, request);
            
        return SemanticSearchResult.builder()
            .results(rankedResults)
            .totalMatches(rankedResults.size())
            .searchType(SearchType.HYBRID)
            .queryEmbeddingTime(measureEmbeddingTime())
            .searchTime(measureSearchTime())
            .build();
    }
    
    private List<SearchResult> fuseSearchResults(
            List<VectorMatch> vectorMatches,
            SearchResponse keywordResults,
            SemanticSearchRequest request) {
        
        Map<String, SearchResult> resultMap = new HashMap<>();
        
        // 处理语义搜索结果
        for (VectorMatch match : vectorMatches) {
            String documentId = match.getMetadata().get("documentId").toString();
            
            SearchResult result = resultMap.computeIfAbsent(documentId, 
                id -> createSearchResult(match));
            result.addSemanticScore(match.getScore());
        }
        
        // 处理关键词搜索结果
        for (SearchHit hit : keywordResults.getHits().getHits()) {
            String documentId = hit.getId();
            
            SearchResult result = resultMap.computeIfAbsent(documentId,
                id -> createSearchResult(hit));
            result.addKeywordScore(hit.getScore());
        }
        
        // 计算融合分数
        return resultMap.values().stream()
            .map(result -> {
                // 加权融合分数 = α * 语义分数 + β * 关键词分数
                double fusedScore = request.getSemanticWeight() * result.getSemanticScore() +
                                   request.getKeywordWeight() * result.getKeywordScore();
                result.setFusedScore(fusedScore);
                return result;
            })
            .sorted((r1, r2) -> Double.compare(r2.getFusedScore(), r1.getFusedScore()))
            .limit(request.getLimit())
            .collect(Collectors.toList());
    }
}
```

**验收标准**：
- ✅ 支持自然语言查询和语义理解
- ✅ 混合搜索结果准确性高于单一搜索方式
- ✅ 查询响应时间在1秒以内
- ✅ 支持多语言语义搜索

---

### **7.2 文档自动分类**
**时间**：第23-24周 | **负责人**：后端开发工程师×1

### **7.3 智能内容推荐**
**时间**：第24-25周 | **负责人**：后端开发工程师×1

---

现在我将创建风险管控详细方案：

<function_calls>
<invoke name="TodoWrite">
<parameter name="todos">[{"content": "\u521b\u5efa\u8be6\u7ec6\u5b9e\u65bd\u8ba1\u5212 - \u9636\u6bb5\u4e00\u5230\u9636\u6bb5\u4e03\u7684\u8be6\u7ec6\u89c4\u5212", "status": "completed", "id": "28"}, {"content": "\u521b\u5efa\u98ce\u9669\u7ba1\u63a7\u65b9\u6848 - \u8be6\u7ec6\u7684\u98ce\u9669\u8bc6\u522b\u548c\u5e94\u5bf9\u63aa\u65bd", "status": "in_progress", "id": "29"}, {"content": "\u521b\u5efa\u4e1a\u52a1\u8ba1\u5212\u6587\u6863 - \u5e02\u573a\u5206\u6790\u548c\u7ade\u4e89\u5bf9\u624b\u7814\u7a76", "status": "completed", "id": "23"}, {"content": "\u521b\u5efa\u4e1a\u52a1\u8ba1\u5212\u6587\u6863 - \u5546\u4e1a\u6a21\u5f0f\u548c\u6536\u5165\u7b56\u7565", "status": "completed", "id": "24"}, {"content": "\u521b\u5efa\u4e1a\u52a1\u8ba1\u5212\u6587\u6863 - \u6210\u672c\u6548\u76ca\u5206\u6790", "status": "completed", "id": "25"}, {"content": "\u521b\u5efa\u4e1a\u52a1\u8ba1\u5212\u6587\u6863 - \u5e02\u573a\u63a8\u5e7f\u548c\u9500\u552e\u7b56\u7565", "status": "completed", "id": "26"}, {"content": "\u521b\u5efa\u4e1a\u52a1\u8ba1\u5212\u6587\u6863 - \u98ce\u9669\u8bc4\u4f30\u548c\u5e94\u5bf9\u65b9\u6848", "status": "completed", "id": "27"}, {"content": "\u9636\u6bb5\u4e00\uff1a\u5b89\u5168\u589e\u5f3a - \u5b9e\u73b0\u591a\u56e0\u7d20\u8ba4\u8bc1(MFA)\u7cfb\u7edf", "status": "pending", "id": "1"}, {"content": "\u9636\u6bb5\u4e00\uff1a\u5b89\u5168\u589e\u5f3a - \u6dfb\u52a0\u6587\u6863\u52a0\u5bc6\u5b58\u50a8\u529f\u80fd", "status": "pending", "id": "2"}, {"content": "\u9636\u6bb5\u4e00\uff1a\u5b89\u5168\u589e\u5f3a - \u96c6\u6210\u75c5\u6bd2\u626b\u63cf\u670d\u52a1(ClamAV)", "status": "pending", "id": "3"}, {"content": "\u9636\u6bb5\u4e00\uff1a\u5b89\u5168\u589e\u5f3a - \u5b9e\u73b0\u5ba1\u8ba1\u65e5\u5fd7\u589e\u5f3a\u548c\u4fdd\u7559\u7b56\u7565", "status": "pending", "id": "4"}, {"content": "\u9636\u6bb5\u4e8c\uff1aAPI\u589e\u5f3a - \u5b9e\u73b0API\u901f\u7387\u9650\u5236", "status": "pending", "id": "5"}, {"content": "\u9636\u6bb5\u4e8c\uff1aAPI\u589e\u5f3a - \u6dfb\u52a0Webhook\u4e8b\u4ef6\u901a\u77e5\u7cfb\u7edf", "status": "pending", "id": "6"}, {"content": "\u9636\u6bb5\u4e8c\uff1aAPI\u589e\u5f3a - \u5b9e\u73b0GraphQL API\u5c42", "status": "pending", "id": "7"}, {"content": "\u9636\u6bb5\u4e09\uff1a\u4f01\u4e1a\u529f\u80fd - \u5b9e\u73b0\u6587\u6863\u4fdd\u7559\u548c\u5904\u7f6e\u7b56\u7565", "status": "pending", "id": "8"}, {"content": "\u9636\u6bb5\u4e09\uff1a\u4f01\u4e1a\u529f\u80fd - \u6dfb\u52a0\u9ad8\u7ea7\u5de5\u4f5c\u6d41(\u5e76\u884c/\u6761\u4ef6\u5ba1\u6279)", "status": "pending", "id": "9"}, {"content": "\u9636\u6bb5\u4e09\uff1a\u4f01\u4e1a\u529f\u80fd - \u5b9e\u73b0\u6570\u5b57\u7b7e\u540d\u529f\u80fd", "status": "pending", "id": "10"}, {"content": "\u9636\u6bb5\u56db\uff1a\u7528\u6237\u4f53\u9a8c - \u5b9e\u73b0\u79fb\u52a8\u7aef\u54cd\u5e94\u5f0f\u4f18\u5316", "status": "pending", "id": "11"}, {"content": "\u9636\u6bb5\u56db\uff1a\u7528\u6237\u4f53\u9a8c - \u6dfb\u52a0\u6df1\u8272\u6a21\u5f0f\u652f\u6301", "status": "pending", "id": "12"}, {"content": "\u9636\u6bb5\u56db\uff1a\u7528\u6237\u4f53\u9a8c - \u5b9e\u73b0PWA\u79bb\u7ebf\u529f\u80fd", "status": "pending", "id": "13"}, {"content": "\u9636\u6bb5\u4e94\uff1a\u96c6\u6210\u6269\u5c55 - \u96c6\u6210Microsoft Office 365", "status": "pending", "id": "14"}, {"content": "\u9636\u6bb5\u4e94\uff1a\u96c6\u6210\u6269\u5c55 - \u6dfb\u52a0AWS S3\u5b58\u50a8\u652f\u6301", "status": "pending", "id": "15"}, {"content": "\u9636\u6bb5\u4e94\uff1a\u96c6\u6210\u6269\u5c55 - \u5b9e\u73b0\u90ae\u4ef6\u7cfb\u7edf\u96c6\u6210", "status": "pending", "id": "16"}, {"content": "\u9636\u6bb5\u516d\uff1a\u5206\u6790\u62a5\u8868 - \u521b\u5efa\u4f7f\u7528\u5206\u6790\u4eea\u8868\u677f", "status": "pending", "id": "17"}, {"content": "\u9636\u6bb5\u516d\uff1a\u5206\u6790\u62a5\u8868 - \u5b9e\u73b0\u5408\u89c4\u6027\u62a5\u544a\u751f\u6210\u5668", "status": "pending", "id": "18"}, {"content": "\u9636\u6bb5\u516d\uff1a\u5206\u6790\u62a5\u8868 - \u6dfb\u52a0\u5b58\u50a8\u6210\u672c\u5206\u6790\u5de5\u5177", "status": "pending", "id": "19"}, {"content": "\u9636\u6bb5\u4e03\uff1aAI\u589e\u5f3a - \u5b9e\u73b0\u8bed\u4e49\u641c\u7d22\u529f\u80fd", "status": "pending", "id": "20"}, {"content": "\u9636\u6bb5\u4e03\uff1aAI\u589e\u5f3a - \u6dfb\u52a0\u6587\u6863\u81ea\u52a8\u5206\u7c7b", "status": "pending", "id": "21"}, {"content": "\u9636\u6bb5\u4e03\uff1aAI\u589e\u5f3a - \u5b9e\u73b0\u667a\u80fd\u5185\u5bb9\u63a8\u8350", "status": "pending", "id": "22"}]