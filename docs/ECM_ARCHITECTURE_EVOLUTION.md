# ECM Core 架构演进策略

> 从 Alfresco 和 Paperless-ngx 吸收的核心设计理念
>
> 文档版本: 1.0
>
> 更新日期: 2025-12-09

---

## 核心洞察：两个项目的设计哲学

### Alfresco 的核心优势：**企业级可扩展性**

```
设计理念: "一切皆可配置、一切皆可扩展"

┌─────────────────────────────────────────────────────────────┐
│                    Alfresco 架构特点                         │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  1. 策略驱动 (Policy-Driven)                                │
│     └─ 所有行为通过策略/行为注入，而非硬编码                  │
│                                                             │
│  2. 模型驱动 (Model-Driven)                                 │
│     └─ 内容模型通过 XML 定义，运行时可扩展                   │
│                                                             │
│  3. 服务总线 (Service Bus)                                  │
│     └─ 92+ 独立服务，通过接口松耦合                          │
│                                                             │
│  4. 权限即数据 (Permission as Data)                         │
│     └─ 权限模型也是配置，不是代码                            │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Paperless-ngx 的核心优势：**智能自动化**

```
设计理念: "让文档自己找到归属"

┌─────────────────────────────────────────────────────────────┐
│                  Paperless-ngx 架构特点                      │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  1. 插件管道 (Plugin Pipeline)                              │
│     └─ 文档处理通过可插拔的处理器链                          │
│                                                             │
│  2. 规则引擎 (Rule Engine)                                  │
│     └─ 用户定义规则，系统自动执行                            │
│                                                             │
│  3. 机器学习 (ML Integration)                               │
│     └─ 从用户行为中学习，持续改进分类                        │
│                                                             │
│  4. 事件驱动 (Event-Driven)                                 │
│     └─ 信号机制驱动松耦合的功能扩展                          │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## 第一部分：架构层面的改变

### 1.1 从"硬编码"到"配置驱动"

#### 当前问题
```java
// ❌ ECM Core 当前: 权限类型硬编码在枚举中
public enum PermissionType {
    READ, WRITE, DELETE, CREATE_CHILDREN...
}

// ❌ 添加新权限需要修改代码、重新编译
```

#### 应该变成
```java
// ✅ 借鉴 Alfresco: 权限通过配置定义
// permission-definitions.yml
permissions:
  - name: READ
    displayName: "读取"
    includes: [READ_PROPERTIES, READ_CONTENT]
  - name: CUSTOM_APPROVE
    displayName: "自定义审批"
    includes: [READ, WRITE]

// 代码中动态加载
@Service
public class PermissionDefinitionService {
    @PostConstruct
    public void loadPermissionDefinitions() {
        // 从 YAML/数据库 加载权限定义
    }
}
```

#### 改变要点
| 方面 | 当前 | 目标 |
|------|------|------|
| 权限类型 | 枚举硬编码 | YAML/DB 配置 |
| 权限组 | 无 | 可配置权限角色 |
| 动态权限 | 无 | 策略接口 + 配置 |

---

### 1.2 从"单一处理"到"管道架构"

#### 当前问题
```java
// ❌ ECM Core 当前: 文档上传是单一方法
@PostMapping("/upload")
public ResponseEntity<?> uploadDocument(MultipartFile file) {
    // 所有逻辑在一个方法中
    extractMetadata(file);
    storeContent(file);
    createVersion(file);
    indexDocument(file);
    // 无法插入自定义处理步骤
}
```

#### 应该变成
```java
// ✅ 借鉴 Paperless-ngx: 插件管道架构
public interface DocumentProcessor {
    ProcessingResult process(DocumentContext context);
    int getOrder(); // 执行顺序
    boolean shouldProcess(DocumentContext context); // 条件判断
}

@Component
@Order(10)
public class MetadataExtractionProcessor implements DocumentProcessor {
    @Override
    public ProcessingResult process(DocumentContext context) {
        // 提取元数据
        return ProcessingResult.continueProcessing();
    }
}

@Component
@Order(20)
public class VirusScanProcessor implements DocumentProcessor {
    // 病毒扫描 - 可选插件
}

@Component
@Order(30)
public class AutoClassificationProcessor implements DocumentProcessor {
    // ML 自动分类 - 可选插件
}

@Component
@Order(40)
public class WorkflowTriggerProcessor implements DocumentProcessor {
    // 触发工作流 - 可选插件
}

// 管道执行器
@Service
public class DocumentProcessingPipeline {
    @Autowired
    private List<DocumentProcessor> processors;

    public void process(DocumentContext context) {
        for (DocumentProcessor processor : processors) {
            if (processor.shouldProcess(context)) {
                ProcessingResult result = processor.process(context);
                if (result.shouldStop()) break;
            }
        }
    }
}
```

#### 管道架构的优势
```
文档上传管道:
┌──────────────────────────────────────────────────────────────┐
│  Upload → [Validation] → [Virus Scan] → [OCR] → [ML分类]     │
│           → [规则匹配] → [工作流触发] → [存储] → [索引]       │
└──────────────────────────────────────────────────────────────┘
                    ↑ 每个步骤都可插拔、可配置
```

---

### 1.3 从"被动查询"到"智能推荐"

#### 当前问题
```java
// ❌ ECM Core 当前: 用户必须主动搜索
@GetMapping("/search")
public Page<Document> search(@RequestParam String query) {
    return searchService.search(query);
}
// 系统不主动提供任何建议
```

#### 应该变成
```java
// ✅ 借鉴 Paperless-ngx: 智能建议系统
@GetMapping("/documents/{id}/suggestions")
public SuggestionResponse getSuggestions(@PathVariable UUID id) {
    Document doc = documentService.getDocument(id);

    return SuggestionResponse.builder()
        .suggestedTags(classifierService.predictTags(doc.getContent()))
        .suggestedCategory(classifierService.predictCategory(doc.getContent()))
        .similarDocuments(searchService.findSimilar(doc))
        .relatedWorkflows(workflowService.findApplicable(doc))
        .build();
}

// 文档上传时自动应用建议
@PostMapping("/upload")
public Document uploadWithSuggestions(MultipartFile file, boolean autoApply) {
    Document doc = createDocument(file);

    if (autoApply) {
        SuggestionResponse suggestions = getSuggestions(doc.getId());
        applyTopSuggestions(doc, suggestions);
    }

    return doc;
}
```

---

### 1.4 从"静态规则"到"自学习系统"

#### 当前问题
```
ECM Core 当前:
- 无自动分类能力
- 用户必须手动打标签
- 系统不从用户行为中学习
```

#### 应该变成
```java
// ✅ 借鉴 Paperless-ngx: 机器学习分类器

/**
 * 文档分类器 - 从用户行为中学习
 */
@Service
public class DocumentClassifierService {

    private DocumentClassifier classifier;

    /**
     * 训练分类器
     * 数据来源: 用户手动分类的文档
     */
    @Scheduled(cron = "0 0 2 * * ?") // 每天凌晨2点训练
    public void trainClassifier() {
        // 1. 收集训练数据 - 用户已分类的文档
        List<TrainingDocument> trainingData = documentRepository
            .findByTagsIsNotEmptyOrCategoriesIsNotEmpty();

        // 2. 训练模型
        classifier.train(trainingData);

        // 3. 保存模型
        classifier.save("/var/ecm/models/classifier.pkl");

        log.info("Classifier trained with {} documents", trainingData.size());
    }

    /**
     * 预测分类
     */
    public ClassificationResult predict(String content) {
        return classifier.predict(content);
    }
}

/**
 * 用户行为学习
 * 当用户修正系统建议时，记录用于改进模型
 */
@EventListener
public void onUserCorrection(UserCorrectionEvent event) {
    // 记录用户修正，用于下次训练
    trainingFeedbackRepository.save(new TrainingFeedback(
        event.getDocumentId(),
        event.getSuggestedTags(),
        event.getActualTags(),
        event.getUser()
    ));
}
```

---

## 第二部分：功能层面的改变

### 2.1 规则引擎：让用户定义自动化

```java
/**
 * 规则引擎 - 用户可视化配置自动化规则
 *
 * 借鉴: Paperless-ngx WorkflowTrigger + Alfresco RuleService
 */
@Entity
@Table(name = "automation_rules")
public class AutomationRule {

    @Id
    private UUID id;

    private String name;

    // 触发条件
    @Enumerated(EnumType.STRING)
    private TriggerType triggerType; // ON_CREATE, ON_UPDATE, ON_UPLOAD, SCHEDULED

    // 匹配条件 (JSON)
    @Type(JsonBinaryType.class)
    private RuleCondition condition;

    // 执行动作 (JSON)
    @Type(JsonBinaryType.class)
    private List<RuleAction> actions;

    private boolean enabled;
    private int priority;
}

/**
 * 规则条件 - 支持复杂表达式
 */
@Data
public class RuleCondition {
    private MatchType matchType; // ALL, ANY, NONE
    private List<Criterion> criteria;

    @Data
    public static class Criterion {
        private String field;      // name, mimeType, content, metadata.xxx
        private Operator operator; // CONTAINS, EQUALS, MATCHES, GREATER_THAN
        private Object value;
    }
}

/**
 * 规则动作
 */
@Data
public class RuleAction {
    private ActionType type; // ADD_TAG, SET_CATEGORY, MOVE_TO, NOTIFY, RUN_WORKFLOW
    private Map<String, Object> parameters;
}

/**
 * 规则引擎执行器
 */
@Service
public class RuleEngine {

    @EventListener
    public void onDocumentEvent(DocumentEvent event) {
        Document doc = event.getDocument();

        // 找到匹配的规则
        List<AutomationRule> rules = ruleRepository
            .findByTriggerTypeAndEnabledTrue(event.getTriggerType());

        for (AutomationRule rule : rules) {
            if (matches(doc, rule.getCondition())) {
                executeActions(doc, rule.getActions());
            }
        }
    }

    private boolean matches(Document doc, RuleCondition condition) {
        // 实现复杂的条件匹配逻辑
    }

    private void executeActions(Document doc, List<RuleAction> actions) {
        for (RuleAction action : actions) {
            actionExecutors.get(action.getType()).execute(doc, action.getParameters());
        }
    }
}
```

### 规则配置示例 (UI 可视化)

```yaml
# 示例规则: 发票自动分类
rule:
  name: "发票自动分类"
  trigger: ON_UPLOAD
  condition:
    matchType: ANY
    criteria:
      - field: content
        operator: CONTAINS
        value: "发票"
      - field: content
        operator: MATCHES
        value: "\\d{20}"  # 发票号码正则
      - field: mimeType
        operator: EQUALS
        value: "application/pdf"
  actions:
    - type: ADD_TAG
      parameters:
        tagName: "发票"
    - type: SET_CATEGORY
      parameters:
        categoryPath: "/财务/发票"
    - type: SET_METADATA
      parameters:
        invoiceType: "auto-detected"
    - type: NOTIFY
      parameters:
        to: "finance@company.com"
        template: "new-invoice"
```

---

### 2.2 动态权限：上下文感知的访问控制

```java
/**
 * 动态权限系统
 *
 * 借鉴: Alfresco DynamicAuthority
 *
 * 核心理念: 权限不仅取决于"你是谁"，还取决于"当前上下文"
 */

// 1. 动态权限提供者接口
public interface DynamicAuthorityProvider {
    /**
     * 根据上下文判断是否授予权限
     */
    boolean hasAuthority(PermissionContext context);

    /**
     * 提供者标识
     */
    String getAuthorityName();

    /**
     * 此权限适用于哪些操作
     */
    Set<PermissionType> getApplicablePermissions();
}

// 2. 内置动态权限: 所有者
@Component
public class OwnerDynamicAuthority implements DynamicAuthorityProvider {

    @Override
    public boolean hasAuthority(PermissionContext context) {
        return context.getCurrentUser().equals(context.getNode().getCreatedBy());
    }

    @Override
    public String getAuthorityName() {
        return "ROLE_OWNER";
    }

    @Override
    public Set<PermissionType> getApplicablePermissions() {
        return Set.of(PermissionType.values()); // 所有者拥有全部权限
    }
}

// 3. 内置动态权限: 部门同事
@Component
public class DepartmentColleagueDynamicAuthority implements DynamicAuthorityProvider {

    @Override
    public boolean hasAuthority(PermissionContext context) {
        User currentUser = userService.getUser(context.getCurrentUser());
        User owner = userService.getUser(context.getNode().getCreatedBy());
        return currentUser.getDepartment().equals(owner.getDepartment());
    }

    @Override
    public String getAuthorityName() {
        return "ROLE_DEPARTMENT_COLLEAGUE";
    }

    @Override
    public Set<PermissionType> getApplicablePermissions() {
        return Set.of(PermissionType.READ); // 同部门同事可读
    }
}

// 4. 内置动态权限: 工作流参与者
@Component
public class WorkflowParticipantDynamicAuthority implements DynamicAuthorityProvider {

    @Override
    public boolean hasAuthority(PermissionContext context) {
        // 检查用户是否是当前文档相关工作流的参与者
        return workflowService.isParticipant(
            context.getNode().getId(),
            context.getCurrentUser()
        );
    }

    @Override
    public String getAuthorityName() {
        return "ROLE_WORKFLOW_PARTICIPANT";
    }

    @Override
    public Set<PermissionType> getApplicablePermissions() {
        return Set.of(PermissionType.READ, PermissionType.WRITE);
    }
}

// 5. 权限检查时整合动态权限
@Service
public class EnhancedSecurityService {

    @Autowired
    private List<DynamicAuthorityProvider> dynamicProviders;

    public boolean hasPermission(Node node, PermissionType permission, String username) {
        PermissionContext context = new PermissionContext(node, username, permission);

        // 1. 检查动态权限
        for (DynamicAuthorityProvider provider : dynamicProviders) {
            if (provider.getApplicablePermissions().contains(permission)) {
                if (provider.hasAuthority(context)) {
                    return true;
                }
            }
        }

        // 2. 检查静态权限 (ACL)
        return checkStaticPermissions(node, permission, username);
    }
}
```

---

### 2.3 内容模型：可扩展的元数据

```java
/**
 * 可扩展内容模型
 *
 * 借鉴: Alfresco Content Model + Paperless-ngx CustomField
 *
 * 核心理念: 用户可以定义自己的文档类型和属性，无需修改代码
 */

// 1. 内容类型定义
@Entity
@Table(name = "content_type_definitions")
public class ContentTypeDefinition {

    @Id
    private UUID id;

    private String name;        // invoice, contract, report
    private String displayName; // 发票, 合同, 报告
    private String parentType;  // 继承自哪个类型

    @OneToMany(cascade = CascadeType.ALL)
    private List<PropertyDefinition> properties;

    @OneToMany(cascade = CascadeType.ALL)
    private List<AspectDefinition> mandatoryAspects;
}

// 2. 属性定义
@Entity
@Table(name = "property_definitions")
public class PropertyDefinition {

    @Id
    private UUID id;

    private String name;
    private String displayName;

    @Enumerated(EnumType.STRING)
    private PropertyType type; // STRING, INTEGER, DATE, BOOLEAN, LIST, NODE_REF

    private boolean required;
    private boolean indexed;     // 是否加入搜索索引
    private boolean searchable;  // 是否支持全文搜索

    private String defaultValue;
    private String validationRegex;

    @Type(JsonBinaryType.class)
    private List<String> allowedValues; // 枚举值
}

// 3. 方面定义 (Aspect - 可附加到任何类型的属性集)
@Entity
@Table(name = "aspect_definitions")
public class AspectDefinition {

    @Id
    private UUID id;

    private String name;        // auditable, versionable, taggable
    private String displayName;

    @OneToMany(cascade = CascadeType.ALL)
    private List<PropertyDefinition> properties;
}

// 4. 内容模型服务
@Service
public class ContentModelService {

    /**
     * 创建自定义文档类型
     */
    public ContentTypeDefinition createContentType(CreateTypeRequest request) {
        ContentTypeDefinition type = new ContentTypeDefinition();
        type.setName(request.getName());
        type.setDisplayName(request.getDisplayName());
        type.setParentType(request.getParentType());
        type.setProperties(request.getProperties());

        // 验证
        validateTypeDefinition(type);

        // 保存
        ContentTypeDefinition saved = typeRepository.save(type);

        // 更新搜索索引 schema
        searchIndexService.updateSchema(saved);

        return saved;
    }

    /**
     * 验证文档是否符合类型定义
     */
    public ValidationResult validateDocument(Document doc, String typeName) {
        ContentTypeDefinition type = getTypeDefinition(typeName);
        List<ValidationError> errors = new ArrayList<>();

        for (PropertyDefinition prop : type.getProperties()) {
            Object value = doc.getProperty(prop.getName());

            if (prop.isRequired() && value == null) {
                errors.add(new ValidationError(prop.getName(), "Required property is missing"));
            }

            if (value != null && prop.getValidationRegex() != null) {
                if (!Pattern.matches(prop.getValidationRegex(), value.toString())) {
                    errors.add(new ValidationError(prop.getName(), "Value does not match pattern"));
                }
            }
        }

        return new ValidationResult(errors.isEmpty(), errors);
    }
}
```

#### 内容类型配置示例

```yaml
# 自定义内容类型: 发票
contentType:
  name: ecm:invoice
  displayName: "发票"
  parentType: ecm:document
  properties:
    - name: invoiceNumber
      displayName: "发票号码"
      type: STRING
      required: true
      indexed: true
      validationRegex: "^\\d{20}$"
    - name: invoiceDate
      displayName: "开票日期"
      type: DATE
      required: true
      indexed: true
    - name: amount
      displayName: "金额"
      type: DECIMAL
      required: true
    - name: vendor
      displayName: "供应商"
      type: STRING
      indexed: true
    - name: invoiceType
      displayName: "发票类型"
      type: LIST
      allowedValues: ["增值税专用发票", "增值税普通发票", "电子发票"]
  mandatoryAspects:
    - ecm:auditable
    - ecm:versionable
```

---

## 第三部分：技术层面的改变

### 3.1 从同步到异步

```java
/**
 * 异步处理架构
 *
 * 借鉴: Paperless-ngx Celery + Alfresco Action Service
 */

// 1. 文档处理异步化
@Service
public class AsyncDocumentService {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    /**
     * 异步处理文档上传
     */
    public DocumentUploadTask uploadDocumentAsync(MultipartFile file, UploadOptions options) {
        // 1. 保存原始文件到临时位置
        String tempPath = storeTempFile(file);

        // 2. 创建任务
        DocumentUploadTask task = new DocumentUploadTask();
        task.setId(UUID.randomUUID());
        task.setStatus(TaskStatus.PENDING);
        task.setTempFilePath(tempPath);
        task.setOptions(options);
        taskRepository.save(task);

        // 3. 发送到消息队列
        rabbitTemplate.convertAndSend("document.upload", task.getId().toString());

        // 4. 立即返回任务 ID
        return task;
    }

    /**
     * 查询任务状态
     */
    public DocumentUploadTask getTaskStatus(UUID taskId) {
        return taskRepository.findById(taskId)
            .orElseThrow(() -> new NotFoundException("Task not found"));
    }
}

// 2. 消息消费者
@Component
@RabbitListener(queues = "document.upload")
public class DocumentUploadConsumer {

    @RabbitHandler
    public void handleUpload(String taskId) {
        DocumentUploadTask task = taskRepository.findById(UUID.fromString(taskId)).get();

        try {
            task.setStatus(TaskStatus.PROCESSING);
            taskRepository.save(task);

            // 执行处理管道
            documentPipeline.process(task);

            task.setStatus(TaskStatus.COMPLETED);
            task.setResultDocumentId(/* ... */);
        } catch (Exception e) {
            task.setStatus(TaskStatus.FAILED);
            task.setErrorMessage(e.getMessage());
        }

        taskRepository.save(task);

        // 通知客户端 (WebSocket)
        notificationService.notifyTaskComplete(task);
    }
}

// 3. 前端轮询/WebSocket 获取进度
@GetMapping("/tasks/{taskId}")
public TaskStatusResponse getTaskStatus(@PathVariable UUID taskId) {
    DocumentUploadTask task = asyncDocumentService.getTaskStatus(taskId);
    return TaskStatusResponse.builder()
        .taskId(task.getId())
        .status(task.getStatus())
        .progress(task.getProgress())
        .resultDocumentId(task.getResultDocumentId())
        .errorMessage(task.getErrorMessage())
        .build();
}
```

### 3.2 从单体到模块化

```
当前架构 (单体):
┌─────────────────────────────────────────┐
│              ECM Core                    │
│  ┌─────┬─────┬─────┬─────┬─────┐       │
│  │Node │Ver. │Perm.│Search│Work.│       │
│  │Svc  │Svc  │Svc  │Svc  │Svc  │       │
│  └─────┴─────┴─────┴─────┴─────┘       │
└─────────────────────────────────────────┘

目标架构 (模块化):
┌─────────────────────────────────────────┐
│              ECM Core                    │
│  ┌─────────────────────────────────┐   │
│  │         Core Module              │   │  ← 核心模块 (必须)
│  │  Node, Version, Permission       │   │
│  └─────────────────────────────────┘   │
│  ┌─────────────────────────────────┐   │
│  │         Search Module            │   │  ← 搜索模块 (可选)
│  │  Elasticsearch, Facet, Suggest   │   │
│  └─────────────────────────────────┘   │
│  ┌─────────────────────────────────┐   │
│  │       Workflow Module            │   │  ← 工作流模块 (可选)
│  │  Flowable, Task, Rule Engine     │   │
│  └─────────────────────────────────┘   │
│  ┌─────────────────────────────────┐   │
│  │     Intelligence Module          │   │  ← 智能模块 (可选)
│  │  ML Classifier, OCR, NLP         │   │
│  └─────────────────────────────────┘   │
│  ┌─────────────────────────────────┐   │
│  │      Integration Module          │   │  ← 集成模块 (可选)
│  │  Odoo, Email, Webhook            │   │
│  └─────────────────────────────────┘   │
└─────────────────────────────────────────┘
```

#### Maven 模块结构

```xml
<!-- ecm-parent/pom.xml -->
<modules>
    <module>ecm-core</module>           <!-- 核心模块 -->
    <module>ecm-search</module>         <!-- 搜索模块 -->
    <module>ecm-workflow</module>       <!-- 工作流模块 -->
    <module>ecm-intelligence</module>   <!-- 智能模块 -->
    <module>ecm-integration</module>    <!-- 集成模块 -->
    <module>ecm-app</module>            <!-- 启动模块 -->
</modules>
```

---

## 第四部分：改变优先级总结

### 高优先级 (立即实施)

| 改变 | 来源 | 价值 | 复杂度 |
|------|------|:----:|:------:|
| 文档处理管道架构 | Paperless | 🔥🔥🔥 | ⭐⭐ |
| 动态权限系统 | Alfresco | 🔥🔥🔥 | ⭐⭐⭐ |
| 规则引擎 | Both | 🔥🔥🔥 | ⭐⭐⭐ |
| 异步任务处理 | Paperless | 🔥🔥 | ⭐⭐ |

### 中优先级 (迭代实施)

| 改变 | 来源 | 价值 | 复杂度 |
|------|------|:----:|:------:|
| ML 自动分类 | Paperless | 🔥🔥🔥 | ⭐⭐⭐⭐ |
| 可配置权限模型 | Alfresco | 🔥🔥 | ⭐⭐⭐ |
| 自定义内容类型 | Alfresco | 🔥🔥 | ⭐⭐⭐ |
| 模块化架构 | Both | 🔥🔥 | ⭐⭐⭐⭐ |

### 低优先级 (长期目标)

| 改变 | 来源 | 价值 | 复杂度 |
|------|------|:----:|:------:|
| 完整策略框架 | Alfresco | 🔥 | ⭐⭐⭐⭐ |
| 多租户支持 | Alfresco | 🔥 | ⭐⭐⭐⭐⭐ |

---

## 第五部分：具体实施建议

### Phase 1: 管道 + 动态权限 (2周)

```
Week 1:
├── 实现 DocumentProcessor 接口
├── 实现处理管道执行器
├── 重构文档上传为管道模式
└── 添加 2-3 个处理器 (元数据提取、索引、通知)

Week 2:
├── 实现 DynamicAuthorityProvider 接口
├── 实现 OwnerDynamicAuthority
├── 实现 LockOwnerDynamicAuthority
├── 集成到 SecurityService
└── 单元测试
```

### Phase 2: 规则引擎 (2周)

```
Week 3:
├── 设计规则数据模型
├── 实现规则条件匹配器
├── 实现规则动作执行器
└── 创建规则管理 API

Week 4:
├── 集成到文档事件监听
├── 添加规则管理 UI API
├── 实现规则导入/导出
└── 集成测试
```

### Phase 3: 智能分类 (3周)

```
Week 5-6:
├── 设计 ML 服务接口
├── 实现 Python 分类器脚本
├── 实现 Java-Python 通信
├── 训练数据导出功能
└── 定时训练任务

Week 7:
├── 集成到文档上传管道
├── 实现建议 API
├── 实现用户反馈收集
└── 端到端测试
```

---

## 总结

### 核心理念转变

| 从 | 到 | 来源 |
|----|----|------|
| 硬编码 | 配置驱动 | Alfresco |
| 单一处理 | 管道架构 | Paperless |
| 被动响应 | 主动推荐 | Paperless |
| 静态规则 | 自学习 | Paperless |
| 同步处理 | 异步任务 | Both |
| 单体架构 | 模块化 | Both |

### 一句话总结

> **从"功能实现"转变为"平台思维"：不是给用户提供功能，而是给用户提供配置和扩展自己功能的能力。**

---

> 文档版本: 1.0
>
> 更新日期: 2025-12-09
