# ECM Core 功能提升路线图 (修订版)

> 基于 Alfresco 和 Paperless-ngx 的最佳实践
>
> 采用**纵向切片**开发模式：每个 Sprint 交付端到端可用功能
>
> 版本: 2.0
>
> 更新日期: 2025-12-09

---

## 目录

1. [开发策略：纵向切片 vs 横向分层](#开发策略纵向切片-vs-横向分层)
2. [Sprint 1：智能管道 MVP](#sprint-1智能管道-mvp)
3. [Sprint 2：基础权限增强](#sprint-2基础权限增强)
4. [Sprint 3：ML 服务与规则引擎](#sprint-3ml-服务与规则引擎)
5. [Sprint 4：用户体验增强](#sprint-4用户体验增强)
6. [架构设计：核心模式](#架构设计核心模式)
7. [风险缓解策略](#风险缓解策略)
8. [实现检查清单](#实现检查清单)

---

## 开发策略：纵向切片 vs 横向分层

### 为什么选择纵向切片？

```
❌ 横向分层 (原方案):
├─ Phase 1: 全部权限相关功能  ← 完成后仍无法独立使用
├─ Phase 2: 全部文档处理功能  ← 完成后仍无法独立使用
├─ Phase 3: 全部搜索功能      ← 全部完成才能用
└─ 问题: 投入大量工作后，系统仍不可交付

✅ 纵向切片 (修订方案):
├─ Sprint 1: 上传 → 提取 → 索引 → 搜索  ← 完整价值流可用
├─ Sprint 2: 在 Sprint 1 基础上加权限   ← 可安全使用
├─ Sprint 3: 在 Sprint 2 基础上加智能   ← 持续增强
└─ 优势: 每个 Sprint 都有可交付价值
```

### 价值流导向

```
核心价值流: 用户上传文档 → 系统自动处理 → 用户可搜索到 → 权限控制访问

Sprint 1: 打通这条路
Sprint 2: 加上门禁
Sprint 3: 让路更智能
```

---

## Sprint 1：智能管道 MVP

**目标**: 上传 PDF → 自动提取文本 → 自动进 ES → 搜索可见

**验收标准**: 用户上传一个 PDF，几秒后可以通过全文搜索找到它

### 1.1 文档处理管道架构

```java
/**
 * 文档处理管道 - 借鉴 Paperless-ngx 的 Pipeline 架构
 * 将上传过程拆分为可插拔的处理器链
 */
public interface DocumentProcessor {

    /**
     * 处理文档
     * @param context 包含文档和中间结果的上下文
     * @return 处理结果
     */
    ProcessingResult process(DocumentContext context);

    /**
     * 处理器顺序 (数值越小越先执行)
     */
    int getOrder();

    /**
     * 是否应该处理此文档
     */
    default boolean shouldProcess(DocumentContext context) {
        return true;
    }

    /**
     * 处理器名称 (用于日志和监控)
     */
    String getName();
}

/**
 * 文档处理上下文
 */
@Data
public class DocumentContext {
    private final Document document;
    private final InputStream contentStream;
    private final Map<String, Object> attributes = new HashMap<>();

    // 中间结果
    private String extractedText;
    private Map<String, String> extractedMetadata;
    private List<String> suggestedTags;
    private String suggestedCategory;

    // 处理状态
    private ProcessingStatus status = ProcessingStatus.PENDING;
    private List<ProcessingError> errors = new ArrayList<>();
}

public enum ProcessingStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
    PARTIALLY_COMPLETED
}
```

### 1.2 管道编排服务

```java
@Service
@Slf4j
public class DocumentPipelineService {

    @Autowired
    private List<DocumentProcessor> processors;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @PostConstruct
    public void init() {
        // 按 order 排序处理器
        processors.sort(Comparator.comparingInt(DocumentProcessor::getOrder));
        log.info("Document pipeline initialized with {} processors: {}",
            processors.size(),
            processors.stream().map(DocumentProcessor::getName).toList());
    }

    /**
     * 同步处理文档 (用于小文件)
     */
    @Transactional
    public ProcessingResult processSync(Document document, InputStream content) {
        DocumentContext context = new DocumentContext(document, content);
        return executePipeline(context);
    }

    /**
     * 异步处理文档 (用于大文件)
     */
    @Async("documentProcessingExecutor")
    @Transactional
    public CompletableFuture<ProcessingResult> processAsync(UUID documentId) {
        Document document = documentRepository.findById(documentId)
            .orElseThrow(() -> new NotFoundException("Document not found"));

        InputStream content = contentService.getContent(document.getContentId());
        DocumentContext context = new DocumentContext(document, content);

        ProcessingResult result = executePipeline(context);

        // 发布处理完成事件
        eventPublisher.publishEvent(new DocumentProcessedEvent(document, result));

        return CompletableFuture.completedFuture(result);
    }

    private ProcessingResult executePipeline(DocumentContext context) {
        context.setStatus(ProcessingStatus.PROCESSING);
        List<ProcessorResult> results = new ArrayList<>();

        for (DocumentProcessor processor : processors) {
            if (!processor.shouldProcess(context)) {
                log.debug("Skipping processor {} for document {}",
                    processor.getName(), context.getDocument().getId());
                continue;
            }

            try {
                log.debug("Executing processor {} for document {}",
                    processor.getName(), context.getDocument().getId());

                long startTime = System.currentTimeMillis();
                ProcessingResult result = processor.process(context);
                long duration = System.currentTimeMillis() - startTime;

                results.add(new ProcessorResult(processor.getName(), result, duration));

                if (result.isFailed()) {
                    log.warn("Processor {} failed for document {}: {}",
                        processor.getName(), context.getDocument().getId(), result.getError());
                    // 继续执行其他处理器，不中断管道
                }
            } catch (Exception e) {
                log.error("Processor {} threw exception for document {}",
                    processor.getName(), context.getDocument().getId(), e);
                context.getErrors().add(new ProcessingError(processor.getName(), e.getMessage()));
            }
        }

        // 确定最终状态
        boolean hasErrors = !context.getErrors().isEmpty();
        boolean hasSuccess = results.stream().anyMatch(r -> !r.getResult().isFailed());

        if (!hasErrors) {
            context.setStatus(ProcessingStatus.COMPLETED);
        } else if (hasSuccess) {
            context.setStatus(ProcessingStatus.PARTIALLY_COMPLETED);
        } else {
            context.setStatus(ProcessingStatus.FAILED);
        }

        return ProcessingResult.builder()
            .status(context.getStatus())
            .processorResults(results)
            .errors(context.getErrors())
            .extractedText(context.getExtractedText())
            .suggestedTags(context.getSuggestedTags())
            .suggestedCategory(context.getSuggestedCategory())
            .build();
    }
}
```

### 1.3 文本提取处理器 (Tika)

```java
/**
 * 使用 Apache Tika 提取文本
 * Order: 100 (第一个执行)
 */
@Component
@Slf4j
public class TikaTextExtractor implements DocumentProcessor {

    private final Tika tika = new Tika();
    private final TikaConfig tikaConfig;

    // 支持的 MIME 类型
    private static final Set<String> SUPPORTED_TYPES = Set.of(
        "application/pdf",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.ms-powerpoint",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "text/plain",
        "text/html",
        "text/rtf",
        "application/rtf"
    );

    @Override
    public ProcessingResult process(DocumentContext context) {
        try {
            // 使用 Tika 提取文本
            Metadata metadata = new Metadata();
            String text = tika.parseToString(context.getContentStream(), metadata);

            // 存储提取结果到上下文
            context.setExtractedText(text);

            // 提取元数据
            Map<String, String> extractedMetadata = new HashMap<>();
            for (String name : metadata.names()) {
                extractedMetadata.put(name, metadata.get(name));
            }
            context.setExtractedMetadata(extractedMetadata);

            log.info("Extracted {} characters from document {}",
                text.length(), context.getDocument().getId());

            return ProcessingResult.success()
                .withData("textLength", text.length())
                .withData("metadataCount", extractedMetadata.size());

        } catch (Exception e) {
            log.error("Text extraction failed for document {}",
                context.getDocument().getId(), e);
            return ProcessingResult.failed(e.getMessage());
        }
    }

    @Override
    public int getOrder() {
        return 100;
    }

    @Override
    public String getName() {
        return "TikaTextExtractor";
    }

    @Override
    public boolean shouldProcess(DocumentContext context) {
        String mimeType = context.getDocument().getMimeType();
        return SUPPORTED_TYPES.contains(mimeType);
    }
}
```

### 1.4 三层存储策略 (关键！)

```java
/**
 * 内容持久化处理器
 * Order: 200 (文本提取后执行)
 *
 * 三层存储策略:
 * Layer 1: 原始文件 (MinIO/S3) - 永久保留
 * Layer 2: 提取文本 (PostgreSQL) - 事务保证，Source of Truth
 * Layer 3: 搜索索引 (Elasticsearch) - 可重建的加速层
 */
@Component
@Slf4j
public class ContentPersistenceProcessor implements DocumentProcessor {

    @Autowired
    private NodeRepository nodeRepository;

    @Autowired
    private ContentStorageService contentStorage; // MinIO/S3

    @Override
    @Transactional
    public ProcessingResult process(DocumentContext context) {
        Document document = context.getDocument();

        try {
            // Layer 2: 提取文本存入 PostgreSQL (JSONB 字段)
            // 这是 Source of Truth，即使 ES 崩溃也不会丢失
            ExtractedContent extractedContent = ExtractedContent.builder()
                .text(context.getExtractedText())
                .metadata(context.getExtractedMetadata())
                .extractedAt(LocalDateTime.now())
                .processorVersion("1.0")
                .build();

            document.setExtractedContent(extractedContent);
            nodeRepository.save(document);

            // 可选: 将提取文本作为 .txt 文件存入对象存储作为冷备份
            if (context.getExtractedText() != null && context.getExtractedText().length() > 0) {
                String textContentId = contentStorage.storeText(
                    context.getExtractedText(),
                    document.getId() + "_extracted.txt"
                );
                document.setExtractedTextContentId(textContentId);
                nodeRepository.save(document);
            }

            log.info("Content persisted to database for document {}", document.getId());

            return ProcessingResult.success()
                .withData("textStored", true)
                .withData("textBackupId", document.getExtractedTextContentId());

        } catch (Exception e) {
            log.error("Content persistence failed for document {}", document.getId(), e);
            return ProcessingResult.failed(e.getMessage());
        }
    }

    @Override
    public int getOrder() {
        return 200;
    }

    @Override
    public String getName() {
        return "ContentPersistenceProcessor";
    }

    @Override
    public boolean shouldProcess(DocumentContext context) {
        // 只有提取到文本才需要持久化
        return context.getExtractedText() != null && !context.getExtractedText().isEmpty();
    }
}

/**
 * 提取内容实体 (嵌入 Document)
 */
@Embeddable
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExtractedContent {

    @Column(name = "extracted_text", columnDefinition = "text")
    private String text;

    @Column(name = "extracted_metadata", columnDefinition = "jsonb")
    @Type(JsonBinaryType.class)
    private Map<String, String> metadata;

    @Column(name = "extracted_at")
    private LocalDateTime extractedAt;

    @Column(name = "processor_version")
    private String processorVersion;
}
```

### 1.5 异步索引处理器 (ES)

```java
/**
 * Elasticsearch 异步索引处理器
 * Order: 300 (持久化后执行)
 *
 * ES 只是加速层，崩溃后可以从数据库重建
 */
@Component
@Slf4j
public class ElasticsearchIndexProcessor implements DocumentProcessor {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private ElasticsearchOperations esOperations;

    @Value("${ecm.search.indexing.async:true}")
    private boolean asyncIndexing;

    @Override
    public ProcessingResult process(DocumentContext context) {
        Document document = context.getDocument();

        try {
            if (asyncIndexing) {
                // 异步: 发送消息到队列
                IndexMessage message = IndexMessage.builder()
                    .documentId(document.getId())
                    .operation(IndexOperation.INDEX)
                    .timestamp(LocalDateTime.now())
                    .build();

                rabbitTemplate.convertAndSend(
                    "ecm.search.indexing",
                    "document.index",
                    message
                );

                log.debug("Sent index message for document {}", document.getId());
                return ProcessingResult.success().withData("async", true);

            } else {
                // 同步: 直接索引
                indexDocument(document, context);
                return ProcessingResult.success().withData("async", false);
            }

        } catch (Exception e) {
            log.error("Index request failed for document {}", document.getId(), e);
            // 索引失败不应阻塞整个管道
            return ProcessingResult.failed(e.getMessage());
        }
    }

    /**
     * 索引文档到 ES
     */
    public void indexDocument(Document document, DocumentContext context) {
        NodeDocument nodeDoc = NodeDocument.builder()
            .id(document.getId().toString())
            .name(document.getName())
            .mimeType(document.getMimeType())
            .content(context.getExtractedText())
            .createdBy(document.getCreatedBy())
            .createdDate(document.getCreatedDate())
            .lastModifiedDate(document.getLastModifiedDate())
            .parentId(document.getParent() != null ? document.getParent().getId().toString() : null)
            .tags(document.getTags().stream().map(Tag::getName).toList())
            .categories(document.getCategories().stream().map(Category::getName).toList())
            .metadata(context.getExtractedMetadata())
            .build();

        esOperations.save(nodeDoc);
        log.info("Document {} indexed to Elasticsearch", document.getId());
    }

    @Override
    public int getOrder() {
        return 300;
    }

    @Override
    public String getName() {
        return "ElasticsearchIndexProcessor";
    }
}

/**
 * ES 索引消费者 (异步模式)
 */
@Component
@Slf4j
public class IndexingConsumer {

    @Autowired
    private NodeRepository nodeRepository;

    @Autowired
    private ElasticsearchIndexProcessor indexProcessor;

    @RabbitListener(queues = "ecm.search.indexing.queue")
    public void handleIndexMessage(IndexMessage message) {
        log.debug("Received index message for document {}", message.getDocumentId());

        try {
            Document document = (Document) nodeRepository.findById(message.getDocumentId())
                .orElseThrow(() -> new NotFoundException("Document not found"));

            // 从数据库读取已提取的内容
            DocumentContext context = new DocumentContext(document, null);
            if (document.getExtractedContent() != null) {
                context.setExtractedText(document.getExtractedContent().getText());
                context.setExtractedMetadata(document.getExtractedContent().getMetadata());
            }

            indexProcessor.indexDocument(document, context);

        } catch (Exception e) {
            log.error("Failed to process index message for document {}",
                message.getDocumentId(), e);
            // 可以实现重试逻辑或死信队列
        }
    }
}
```

### 1.6 索引重建服务

```java
/**
 * ES 索引重建服务
 * 当 ES 数据丢失时，从数据库重建索引
 */
@Service
@Slf4j
public class IndexRebuildService {

    @Autowired
    private NodeRepository nodeRepository;

    @Autowired
    private ElasticsearchOperations esOperations;

    /**
     * 重建整个索引
     */
    @Async
    public CompletableFuture<RebuildResult> rebuildIndex() {
        log.info("Starting full index rebuild...");

        long startTime = System.currentTimeMillis();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        // 删除旧索引
        esOperations.indexOps(NodeDocument.class).delete();
        esOperations.indexOps(NodeDocument.class).create();

        // 流式处理所有文档
        try (Stream<Node> nodes = nodeRepository.streamAllDocuments()) {
            nodes.forEach(node -> {
                try {
                    Document doc = (Document) node;
                    NodeDocument nodeDoc = buildNodeDocument(doc);
                    esOperations.save(nodeDoc);
                    successCount.incrementAndGet();

                    if (successCount.get() % 1000 == 0) {
                        log.info("Rebuilt {} documents...", successCount.get());
                    }
                } catch (Exception e) {
                    log.error("Failed to index document {}", node.getId(), e);
                    errorCount.incrementAndGet();
                }
            });
        }

        long duration = System.currentTimeMillis() - startTime;

        RebuildResult result = RebuildResult.builder()
            .successCount(successCount.get())
            .errorCount(errorCount.get())
            .durationMs(duration)
            .build();

        log.info("Index rebuild completed: {} success, {} errors, {} ms",
            successCount.get(), errorCount.get(), duration);

        return CompletableFuture.completedFuture(result);
    }

    /**
     * 增量重建 (指定时间范围)
     */
    public CompletableFuture<RebuildResult> rebuildSince(LocalDateTime since) {
        log.info("Starting incremental index rebuild since {}", since);
        // 类似实现，但只处理指定时间后修改的文档
        return null; // 实现省略
    }

    private NodeDocument buildNodeDocument(Document doc) {
        return NodeDocument.builder()
            .id(doc.getId().toString())
            .name(doc.getName())
            .mimeType(doc.getMimeType())
            .content(doc.getExtractedContent() != null ? doc.getExtractedContent().getText() : null)
            .createdBy(doc.getCreatedBy())
            .createdDate(doc.getCreatedDate())
            .lastModifiedDate(doc.getLastModifiedDate())
            .parentId(doc.getParent() != null ? doc.getParent().getId().toString() : null)
            .tags(doc.getTags().stream().map(Tag::getName).toList())
            .categories(doc.getCategories().stream().map(Category::getName).toList())
            .build();
    }
}
```

### 1.7 重构上传 API

```java
@RestController
@RequestMapping("/api/v1/documents")
@Slf4j
public class DocumentController {

    @Autowired
    private NodeService nodeService;

    @Autowired
    private DocumentPipelineService pipelineService;

    /**
     * 上传文档 - 集成处理管道
     */
    @PostMapping("/upload")
    public ResponseEntity<DocumentUploadResponse> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folderId", required = false) UUID folderId,
            @RequestParam(value = "async", defaultValue = "true") boolean async) {

        // 1. 创建文档记录
        Document document = nodeService.createDocument(
            file.getOriginalFilename(),
            file.getContentType(),
            file.getSize(),
            folderId
        );

        // 2. 存储原始内容 (Layer 1)
        String contentId = contentService.store(file.getInputStream());
        document.setContentId(contentId);
        nodeRepository.save(document);

        // 3. 执行处理管道
        if (async && file.getSize() > 10 * 1024 * 1024) { // > 10MB 异步处理
            pipelineService.processAsync(document.getId());

            return ResponseEntity.accepted()
                .body(DocumentUploadResponse.builder()
                    .id(document.getId())
                    .name(document.getName())
                    .status("PROCESSING")
                    .message("Document uploaded, processing in background")
                    .build());
        } else {
            ProcessingResult result = pipelineService.processSync(document, file.getInputStream());

            return ResponseEntity.ok(DocumentUploadResponse.builder()
                .id(document.getId())
                .name(document.getName())
                .status(result.getStatus().name())
                .processingResult(result)
                .build());
        }
    }

    /**
     * 获取文档处理状态
     */
    @GetMapping("/{documentId}/processing-status")
    public ResponseEntity<ProcessingStatusResponse> getProcessingStatus(
            @PathVariable UUID documentId) {

        Document document = (Document) nodeService.getNode(documentId);

        return ResponseEntity.ok(ProcessingStatusResponse.builder()
            .documentId(documentId)
            .hasExtractedText(document.getExtractedContent() != null)
            .isIndexed(searchService.isIndexed(documentId))
            .build());
    }
}
```

---

## Sprint 2：基础权限增强

**目标**: 确保上传的文件只有创建者和被授权者能看到

**验收标准**: 用户 A 上传的文件，用户 B 默认看不到，除非 A 分享给 B

### 2.1 动态权限系统

```java
/**
 * 动态权限接口 - 借鉴 Alfresco DynamicAuthority
 * 运行时根据上下文判断权限，而不是静态 ACL 记录
 */
public interface DynamicAuthority {

    /**
     * 检查用户是否拥有该动态权限
     */
    boolean hasAuthority(PermissionContext context);

    /**
     * 获取权限标识符
     */
    String getAuthorityName();

    /**
     * 此动态权限适用于哪些权限类型
     * null 表示应用于所有权限检查
     */
    Set<PermissionType> getApplicablePermissions();

    /**
     * 优先级 (数值越小优先级越高)
     */
    default int getPriority() {
        return 100;
    }
}

/**
 * 权限检查上下文
 */
@Data
@Builder
public class PermissionContext {
    private UUID nodeId;
    private Node node;
    private String username;
    private PermissionType requestedPermission;
    private Map<String, Object> attributes;
}

/**
 * 文档所有者动态权限
 */
@Component
public class OwnerDynamicAuthority implements DynamicAuthority {

    @Autowired
    private NodeRepository nodeRepository;

    @Override
    public boolean hasAuthority(PermissionContext context) {
        Node node = context.getNode();
        if (node == null) {
            node = nodeRepository.findById(context.getNodeId()).orElse(null);
        }
        return node != null && context.getUsername().equals(node.getCreatedBy());
    }

    @Override
    public String getAuthorityName() {
        return "ROLE_OWNER";
    }

    @Override
    public Set<PermissionType> getApplicablePermissions() {
        return null; // 所有者拥有所有权限
    }

    @Override
    public int getPriority() {
        return 10; // 高优先级
    }
}

/**
 * 锁定所有者动态权限
 */
@Component
public class LockOwnerDynamicAuthority implements DynamicAuthority {

    @Override
    public boolean hasAuthority(PermissionContext context) {
        Node node = context.getNode();
        if (node == null) return false;
        return node.isLocked() && context.getUsername().equals(node.getLockedBy());
    }

    @Override
    public String getAuthorityName() {
        return "ROLE_LOCK_OWNER";
    }

    @Override
    public Set<PermissionType> getApplicablePermissions() {
        return Set.of(
            PermissionType.UNLOCK,
            PermissionType.CHECKIN,
            PermissionType.CANCEL_CHECKOUT
        );
    }
}

/**
 * 同部门动态权限 (可选扩展)
 */
@Component
public class SameDepartmentDynamicAuthority implements DynamicAuthority {

    @Autowired
    private UserService userService;

    @Override
    public boolean hasAuthority(PermissionContext context) {
        String ownerDept = userService.getDepartment(context.getNode().getCreatedBy());
        String userDept = userService.getDepartment(context.getUsername());
        return ownerDept != null && ownerDept.equals(userDept);
    }

    @Override
    public String getAuthorityName() {
        return "ROLE_SAME_DEPARTMENT";
    }

    @Override
    public Set<PermissionType> getApplicablePermissions() {
        return Set.of(PermissionType.READ); // 同部门只能读
    }
}
```

### 2.2 增强的 SecurityService

```java
@Service
@Slf4j
public class SecurityService {

    @Autowired
    private List<DynamicAuthority> dynamicAuthorities;

    @Autowired
    private PermissionRepository permissionRepository;

    @PostConstruct
    public void init() {
        // 按优先级排序
        dynamicAuthorities.sort(Comparator.comparingInt(DynamicAuthority::getPriority));
        log.info("SecurityService initialized with {} dynamic authorities",
            dynamicAuthorities.size());
    }

    /**
     * 检查权限 (核心方法)
     */
    public boolean hasPermission(Node node, PermissionType permission, String username) {
        PermissionContext context = PermissionContext.builder()
            .nodeId(node.getId())
            .node(node)
            .username(username)
            .requestedPermission(permission)
            .build();

        // 1. 检查动态权限 (优先级最高)
        for (DynamicAuthority da : dynamicAuthorities) {
            Set<PermissionType> applicable = da.getApplicablePermissions();
            if (applicable == null || applicable.contains(permission)) {
                if (da.hasAuthority(context)) {
                    log.debug("Permission {} granted to {} on {} via {}",
                        permission, username, node.getId(), da.getAuthorityName());
                    return true;
                }
            }
        }

        // 2. 检查节点级 ACL
        if (checkNodeAcl(node, permission, username)) {
            return true;
        }

        // 3. 检查继承的权限 (向上遍历父节点)
        if (node.getParent() != null && node.isInheritPermissions()) {
            return hasPermission(node.getParent(), permission, username);
        }

        return false;
    }

    /**
     * 检查节点级 ACL
     */
    private boolean checkNodeAcl(Node node, PermissionType permission, String username) {
        // 检查用户直接权限
        Optional<Permission> userPerm = permissionRepository.findByNodeAndAuthority(
            node.getId(), username, AuthorityType.USER);

        if (userPerm.isPresent() && userPerm.get().getPermissions().contains(permission)) {
            return true;
        }

        // 检查用户所属组的权限
        List<String> userGroups = getUserGroups(username);
        for (String group : userGroups) {
            Optional<Permission> groupPerm = permissionRepository.findByNodeAndAuthority(
                node.getId(), group, AuthorityType.GROUP);

            if (groupPerm.isPresent() && groupPerm.get().getPermissions().contains(permission)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 断言权限 (抛异常版本)
     */
    public void checkPermission(Node node, PermissionType permission) {
        String username = getCurrentUser();
        if (!hasPermission(node, permission, username)) {
            throw new AccessDeniedException(
                String.format("User %s does not have %s permission on node %s",
                    username, permission, node.getId()));
        }
    }
}
```

### 2.3 分享链接功能

```java
/**
 * 分享链接实体
 */
@Entity
@Table(name = "share_links")
@Data
public class ShareLink extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Column(nullable = false, unique = true, length = 32)
    private String slug;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "access_count")
    private Integer accessCount = 0;

    @Column(name = "max_access_count")
    private Integer maxAccessCount;

    @Column(name = "enabled")
    private Boolean enabled = true;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    public boolean isValid() {
        if (!enabled) return false;
        if (expiresAt != null && LocalDateTime.now().isAfter(expiresAt)) return false;
        if (maxAccessCount != null && accessCount >= maxAccessCount) return false;
        return true;
    }
}

/**
 * 分享链接服务
 */
@Service
@Transactional
public class ShareLinkService {

    @Autowired
    private ShareLinkRepository shareLinkRepository;

    @Autowired
    private SecurityService securityService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public ShareLink createShareLink(CreateShareLinkRequest request) {
        Document document = documentRepository.findById(request.getDocumentId())
            .orElseThrow(() -> new NotFoundException("Document not found"));

        // 检查权限
        securityService.checkPermission(document, PermissionType.READ);

        ShareLink shareLink = new ShareLink();
        shareLink.setDocument(document);
        shareLink.setSlug(generateSlug());
        shareLink.setExpiresAt(request.getExpiresAt());
        shareLink.setMaxAccessCount(request.getMaxAccessCount());
        shareLink.setCreatedBy(securityService.getCurrentUser());

        if (request.getPassword() != null) {
            shareLink.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        }

        return shareLinkRepository.save(shareLink);
    }

    public Document accessShareLink(String slug, String password) {
        ShareLink shareLink = shareLinkRepository.findBySlug(slug)
            .orElseThrow(() -> new NotFoundException("Share link not found"));

        if (!shareLink.isValid()) {
            throw new ShareLinkExpiredException("Share link has expired");
        }

        if (shareLink.getPasswordHash() != null) {
            if (password == null || !passwordEncoder.matches(password, shareLink.getPasswordHash())) {
                throw new UnauthorizedException("Invalid password");
            }
        }

        // 增加访问计数
        shareLink.setAccessCount(shareLink.getAccessCount() + 1);
        shareLinkRepository.save(shareLink);

        return shareLink.getDocument();
    }

    private String generateSlug() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
```

### 2.4 回收站 API

```java
@Service
@Transactional
public class TrashService {

    /**
     * 获取回收站内容
     */
    public Page<Node> getTrashItems(Pageable pageable) {
        String username = securityService.getCurrentUser();
        return nodeRepository.findByDeletedTrueAndCreatedBy(username, pageable);
    }

    /**
     * 恢复节点
     */
    public Node restoreNode(UUID nodeId) {
        Node node = nodeRepository.findByIdIncludeDeleted(nodeId)
            .orElseThrow(() -> new NotFoundException("Node not found"));

        if (!node.isDeleted()) {
            throw new IllegalStateException("Node is not in trash");
        }

        // 检查原父节点
        if (node.getParent() != null && node.getParent().isDeleted()) {
            throw new IllegalStateException("Parent folder is also deleted");
        }

        node.setDeleted(false);

        // 重新索引
        if (node instanceof Document) {
            indexService.indexDocument((Document) node);
        }

        return nodeRepository.save(node);
    }

    /**
     * 永久删除
     */
    public void permanentDelete(UUID nodeId) {
        Node node = nodeRepository.findByIdIncludeDeleted(nodeId)
            .orElseThrow(() -> new NotFoundException("Node not found"));

        if (!node.isDeleted()) {
            throw new IllegalStateException("Must be in trash first");
        }

        // 删除内容
        if (node instanceof Document doc) {
            contentService.deleteContent(doc.getContentId());
            if (doc.getExtractedTextContentId() != null) {
                contentService.deleteContent(doc.getExtractedTextContentId());
            }
        }

        // 删除索引
        indexService.deleteDocument(nodeId);

        // 删除数据库记录
        nodeRepository.delete(node);
    }
}
```

---

## Sprint 3：ML 服务与规则引擎

**目标**: 让系统能自动分类文档，并支持用户定义的自动化规则

**验收标准**: 规则 "名称包含发票 → 自动分类到财务" 可以正常工作

### 3.1 ML 微服务架构 (关键修正！)

```
┌─────────────────────────────────────────────────────────────────┐
│                       ECM 系统架构                               │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────┐     HTTP/gRPC      ┌──────────────────┐       │
│  │  ECM Core   │ ←───────────────→  │   ML Service     │       │
│  │  (Java)     │                    │   (FastAPI)      │       │
│  └─────────────┘                    └──────────────────┘       │
│        │                                    │                   │
│        │                                    │                   │
│        ▼                                    ▼                   │
│  ┌─────────────┐  ┌─────────────┐  ┌──────────────────┐       │
│  │ PostgreSQL  │  │    MinIO    │  │  scikit-learn    │       │
│  │ (Source of  │  │  (原始文件)  │  │  transformers    │       │
│  │   Truth)    │  │             │  │                  │       │
│  └─────────────┘  └─────────────┘  └──────────────────┘       │
│        │                                                        │
│        ▼                                                        │
│  ┌─────────────┐                                               │
│  │Elasticsearch│ ← 可重建的加速层                               │
│  └─────────────┘                                               │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘

⚠️ 重要: 不要用 ProcessBuilder 调用 Python！
   原因: Python 环境依赖问题、进程开销、并发瓶颈
   方案: 独立的 FastAPI 微服务，通过 HTTP 调用
```

### 3.2 ML 微服务 (FastAPI)

```python
# ml-service/app/main.py
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from typing import List, Optional
import pickle
import numpy as np
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.neural_network import MLPClassifier

app = FastAPI(title="ECM ML Service", version="1.0.0")

# 全局模型变量
model_data = None

class ClassifyRequest(BaseModel):
    text: str
    candidates: Optional[List[str]] = None

class ClassifyResponse(BaseModel):
    prediction: str
    confidence: float
    alternatives: List[dict]

class TrainRequest(BaseModel):
    documents: List[dict]  # [{text, tags, category}]

class HealthResponse(BaseModel):
    status: str
    model_loaded: bool
    model_version: Optional[str]

@app.on_event("startup")
async def load_model():
    """启动时加载模型"""
    global model_data
    try:
        with open('/var/ml-service/model.pkl', 'rb') as f:
            model_data = pickle.load(f)
        print("Model loaded successfully")
    except FileNotFoundError:
        print("No model found, will need training")
        model_data = None

@app.get("/health", response_model=HealthResponse)
async def health_check():
    """健康检查"""
    return HealthResponse(
        status="healthy",
        model_loaded=model_data is not None,
        model_version=model_data.get('version') if model_data else None
    )

@app.post("/api/ml/classify", response_model=ClassifyResponse)
async def classify_document(request: ClassifyRequest):
    """分类文档"""
    global model_data

    if model_data is None:
        raise HTTPException(status_code=503, detail="Model not loaded")

    vectorizer = model_data['vectorizer']
    classifier = model_data['classifier']
    label_encoder = model_data['label_encoder']

    # 向量化
    X = vectorizer.transform([request.text])

    # 预测
    proba = classifier.predict_proba(X)[0]
    pred_idx = np.argmax(proba)
    confidence = float(proba[pred_idx])
    prediction = label_encoder.inverse_transform([pred_idx])[0]

    # 获取 Top-3 备选
    top_indices = np.argsort(proba)[-3:][::-1]
    alternatives = [
        {
            "category": label_encoder.inverse_transform([idx])[0],
            "confidence": float(proba[idx])
        }
        for idx in top_indices
    ]

    return ClassifyResponse(
        prediction=prediction,
        confidence=confidence,
        alternatives=alternatives
    )

@app.post("/api/ml/train")
async def train_model(request: TrainRequest):
    """训练模型"""
    global model_data

    if len(request.documents) < 10:
        raise HTTPException(status_code=400, detail="Need at least 10 documents")

    # 准备训练数据
    texts = [doc['text'] for doc in request.documents]
    categories = [doc['category'] for doc in request.documents]

    # 向量化
    vectorizer = TfidfVectorizer(max_features=5000, stop_words='english')
    X = vectorizer.fit_transform(texts)

    # 标签编码
    from sklearn.preprocessing import LabelEncoder
    label_encoder = LabelEncoder()
    y = label_encoder.fit_transform(categories)

    # 训练
    classifier = MLPClassifier(hidden_layer_sizes=(100, 50), max_iter=500)
    classifier.fit(X, y)

    # 保存模型
    model_data = {
        'vectorizer': vectorizer,
        'classifier': classifier,
        'label_encoder': label_encoder,
        'version': '1.0',
        'trained_samples': len(texts)
    }

    with open('/var/ml-service/model.pkl', 'wb') as f:
        pickle.dump(model_data, f)

    return {"status": "trained", "samples": len(texts)}
```

```dockerfile
# ml-service/Dockerfile
FROM python:3.11-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app/ ./app/

# 模型存储目录
RUN mkdir -p /var/ml-service

EXPOSE 8080

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8080"]
```

```yaml
# docker-compose.yml 新增服务
services:
  ml-service:
    build: ./ml-service
    ports:
      - "8081:8080"
    volumes:
      - ml-models:/var/ml-service
    environment:
      - MODEL_PATH=/var/ml-service/model.pkl
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/health"]
      interval: 30s
      timeout: 10s
      retries: 3

volumes:
  ml-models:
```

### 3.3 Java 客户端调用 ML 服务

```java
/**
 * ML 服务客户端
 * 通过 HTTP 调用独立的 ML 微服务
 */
@Service
@Slf4j
public class MLServiceClient {

    @Value("${ecm.ml.service.url:http://ml-service:8080}")
    private String mlServiceUrl;

    @Autowired
    private RestTemplate restTemplate;

    /**
     * 分类文档
     */
    public ClassificationResult classify(String text) {
        try {
            ClassifyRequest request = new ClassifyRequest(text, null);

            ClassifyResponse response = restTemplate.postForObject(
                mlServiceUrl + "/api/ml/classify",
                request,
                ClassifyResponse.class
            );

            return ClassificationResult.builder()
                .suggestedCategory(response.getPrediction())
                .confidence(response.getConfidence())
                .alternatives(response.getAlternatives())
                .build();

        } catch (RestClientException e) {
            log.warn("ML service unavailable, classification skipped: {}", e.getMessage());
            return ClassificationResult.empty();
        }
    }

    /**
     * 检查服务是否可用
     */
    public boolean isAvailable() {
        try {
            HealthResponse response = restTemplate.getForObject(
                mlServiceUrl + "/health",
                HealthResponse.class
            );
            return response != null && "healthy".equals(response.getStatus());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 触发模型训练
     */
    public void trainModel(List<TrainingDocument> documents) {
        try {
            TrainRequest request = new TrainRequest(documents);
            restTemplate.postForObject(
                mlServiceUrl + "/api/ml/train",
                request,
                Map.class
            );
            log.info("ML model training triggered with {} documents", documents.size());
        } catch (RestClientException e) {
            log.error("Failed to trigger ML training", e);
            throw new ServiceUnavailableException("ML service unavailable");
        }
    }
}
```

### 3.4 ML 分类处理器

```java
/**
 * ML 分类处理器
 * Order: 400 (索引后执行)
 *
 * 重要: 这是可选处理器，ML 服务不可用时优雅降级
 */
@Component
@Slf4j
public class MLClassificationProcessor implements DocumentProcessor {

    @Autowired
    private MLServiceClient mlServiceClient;

    @Autowired
    private CategoryRepository categoryRepository;

    @Value("${ecm.ml.auto-classify.enabled:true}")
    private boolean autoClassifyEnabled;

    @Value("${ecm.ml.auto-classify.confidence-threshold:0.7}")
    private double confidenceThreshold;

    @Override
    public ProcessingResult process(DocumentContext context) {
        if (!autoClassifyEnabled) {
            return ProcessingResult.skipped("Auto-classify disabled");
        }

        if (!mlServiceClient.isAvailable()) {
            log.debug("ML service unavailable, skipping classification");
            return ProcessingResult.skipped("ML service unavailable");
        }

        String text = context.getExtractedText();
        if (text == null || text.length() < 100) {
            return ProcessingResult.skipped("Insufficient text for classification");
        }

        try {
            ClassificationResult result = mlServiceClient.classify(text);

            if (result.getConfidence() >= confidenceThreshold) {
                context.setSuggestedCategory(result.getSuggestedCategory());

                // 如果是高置信度，自动应用分类
                if (result.getConfidence() >= 0.85) {
                    applyCategory(context.getDocument(), result.getSuggestedCategory());
                }

                return ProcessingResult.success()
                    .withData("category", result.getSuggestedCategory())
                    .withData("confidence", result.getConfidence())
                    .withData("autoApplied", result.getConfidence() >= 0.85);
            } else {
                return ProcessingResult.success()
                    .withData("category", result.getSuggestedCategory())
                    .withData("confidence", result.getConfidence())
                    .withData("belowThreshold", true);
            }

        } catch (Exception e) {
            log.warn("ML classification failed", e);
            return ProcessingResult.failed(e.getMessage());
        }
    }

    private void applyCategory(Document document, String categoryName) {
        Category category = categoryRepository.findByName(categoryName)
            .orElseGet(() -> {
                Category newCat = new Category();
                newCat.setName(categoryName);
                return categoryRepository.save(newCat);
            });

        document.getCategories().add(category);
    }

    @Override
    public int getOrder() {
        return 400;
    }

    @Override
    public String getName() {
        return "MLClassificationProcessor";
    }
}
```

### 3.5 规则引擎

```java
/**
 * 自动化规则实体
 */
@Entity
@Table(name = "automation_rules")
@Data
public class AutomationRule extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false)
    private TriggerType triggerType;

    /**
     * 条件 (JSON 格式)
     */
    @Column(name = "conditions", columnDefinition = "jsonb")
    @Type(JsonBinaryType.class)
    private RuleCondition condition;

    /**
     * 动作列表 (JSON 格式)
     */
    @Column(name = "actions", columnDefinition = "jsonb")
    @Type(JsonBinaryType.class)
    private List<RuleAction> actions;

    @Column(name = "priority")
    private Integer priority = 100;

    @Column(name = "enabled")
    private Boolean enabled = true;

    @Column(name = "owner")
    private String owner;
}

public enum TriggerType {
    DOCUMENT_CREATED,
    DOCUMENT_UPDATED,
    DOCUMENT_TAGGED,
    DOCUMENT_MOVED,
    SCHEDULED
}

@Data
public class RuleCondition {
    private ConditionType type;
    private String field;
    private String operator;
    private Object value;
    private List<RuleCondition> children; // 用于 AND/OR 组合
}

public enum ConditionType {
    SIMPLE,     // 简单条件: field operator value
    AND,        // 所有子条件都满足
    OR,         // 任一子条件满足
    NOT         // 取反
}

@Data
public class RuleAction {
    private ActionType type;
    private Map<String, Object> params;
}

public enum ActionType {
    ADD_TAG,
    REMOVE_TAG,
    SET_CATEGORY,
    MOVE_TO_FOLDER,
    SET_METADATA,
    START_WORKFLOW,
    SEND_NOTIFICATION
}
```

### 3.6 规则引擎服务

```java
@Service
@Slf4j
public class RuleEngineService {

    @Autowired
    private AutomationRuleRepository ruleRepository;

    /**
     * 评估并执行规则
     */
    @Transactional
    public List<RuleExecutionResult> evaluateAndExecute(Document document, TriggerType trigger) {
        List<AutomationRule> rules = ruleRepository.findByTriggerTypeAndEnabledTrue(trigger);
        rules.sort(Comparator.comparingInt(AutomationRule::getPriority));

        List<RuleExecutionResult> results = new ArrayList<>();

        for (AutomationRule rule : rules) {
            try {
                boolean matches = evaluateCondition(rule.getCondition(), document);

                if (matches) {
                    log.info("Rule '{}' matched for document {}", rule.getName(), document.getId());

                    for (RuleAction action : rule.getActions()) {
                        executeAction(action, document);
                    }

                    results.add(RuleExecutionResult.success(rule, document));
                }
            } catch (Exception e) {
                log.error("Rule '{}' execution failed", rule.getName(), e);
                results.add(RuleExecutionResult.failed(rule, document, e.getMessage()));
            }
        }

        return results;
    }

    /**
     * 评估条件
     */
    private boolean evaluateCondition(RuleCondition condition, Document document) {
        if (condition == null) return true;

        return switch (condition.getType()) {
            case SIMPLE -> evaluateSimpleCondition(condition, document);
            case AND -> condition.getChildren().stream()
                .allMatch(c -> evaluateCondition(c, document));
            case OR -> condition.getChildren().stream()
                .anyMatch(c -> evaluateCondition(c, document));
            case NOT -> !evaluateCondition(condition.getChildren().get(0), document);
        };
    }

    private boolean evaluateSimpleCondition(RuleCondition condition, Document document) {
        Object fieldValue = getFieldValue(document, condition.getField());
        Object targetValue = condition.getValue();

        return switch (condition.getOperator()) {
            case "equals" -> Objects.equals(fieldValue, targetValue);
            case "contains" -> fieldValue != null &&
                fieldValue.toString().toLowerCase().contains(targetValue.toString().toLowerCase());
            case "startsWith" -> fieldValue != null &&
                fieldValue.toString().toLowerCase().startsWith(targetValue.toString().toLowerCase());
            case "regex" -> fieldValue != null &&
                Pattern.matches(targetValue.toString(), fieldValue.toString());
            case "gt" -> compareNumbers(fieldValue, targetValue) > 0;
            case "lt" -> compareNumbers(fieldValue, targetValue) < 0;
            default -> false;
        };
    }

    private Object getFieldValue(Document document, String field) {
        return switch (field) {
            case "name" -> document.getName();
            case "mimeType" -> document.getMimeType();
            case "size" -> document.getSize();
            case "content" -> document.getExtractedContent() != null ?
                document.getExtractedContent().getText() : null;
            case "createdBy" -> document.getCreatedBy();
            default -> document.getMetadata().get(field);
        };
    }

    /**
     * 执行动作
     */
    private void executeAction(RuleAction action, Document document) {
        switch (action.getType()) {
            case ADD_TAG -> {
                String tagName = (String) action.getParams().get("tagName");
                tagService.addTagToNode(document.getId(), tagName);
            }
            case SET_CATEGORY -> {
                String categoryName = (String) action.getParams().get("categoryName");
                categoryService.setCategoryForNode(document.getId(), categoryName);
            }
            case MOVE_TO_FOLDER -> {
                UUID folderId = UUID.fromString((String) action.getParams().get("folderId"));
                nodeService.moveNode(document.getId(), folderId);
            }
            case SEND_NOTIFICATION -> {
                String recipient = (String) action.getParams().get("recipient");
                String message = (String) action.getParams().get("message");
                notificationService.send(recipient, message, document);
            }
            case START_WORKFLOW -> {
                String workflowKey = (String) action.getParams().get("workflowKey");
                workflowService.startWorkflow(workflowKey, document);
            }
        }
    }
}
```

### 3.7 规则引擎处理器

```java
/**
 * 规则引擎处理器
 * Order: 500 (最后执行)
 */
@Component
@Slf4j
public class RuleEngineProcessor implements DocumentProcessor {

    @Autowired
    private RuleEngineService ruleEngineService;

    @Override
    public ProcessingResult process(DocumentContext context) {
        Document document = context.getDocument();

        List<RuleExecutionResult> results = ruleEngineService.evaluateAndExecute(
            document,
            TriggerType.DOCUMENT_CREATED
        );

        int matched = (int) results.stream().filter(RuleExecutionResult::isSuccess).count();
        int failed = (int) results.stream().filter(r -> !r.isSuccess()).count();

        log.info("Rule engine executed: {} matched, {} failed for document {}",
            matched, failed, document.getId());

        return ProcessingResult.success()
            .withData("rulesMatched", matched)
            .withData("rulesFailed", failed)
            .withData("results", results);
    }

    @Override
    public int getOrder() {
        return 500;
    }

    @Override
    public String getName() {
        return "RuleEngineProcessor";
    }
}
```

---

## Sprint 4：用户体验增强

**目标**: 提升搜索和使用体验

### 4.1 Faceted Search

```java
@Data
public class FacetedSearchResult {
    private Page<NodeDTO> results;
    private Map<String, List<FacetValue>> facets;
}

@Data
public class FacetValue {
    private String value;
    private long count;
}

@Service
public class FacetedSearchService {

    public FacetedSearchResult search(SearchRequest request) {
        // 构建 ES 查询
        NativeQuery query = NativeQuery.builder()
            .withQuery(buildQuery(request))
            .withAggregation("mimeTypes",
                Aggregation.of(a -> a.terms(t -> t.field("mimeType").size(10))))
            .withAggregation("tags",
                Aggregation.of(a -> a.terms(t -> t.field("tags").size(20))))
            .withAggregation("categories",
                Aggregation.of(a -> a.terms(t -> t.field("categories").size(10))))
            .withAggregation("createdBy",
                Aggregation.of(a -> a.terms(t -> t.field("createdBy").size(10))))
            .withAggregation("dateRange",
                Aggregation.of(a -> a.dateRange(dr -> dr.field("createdDate")
                    .ranges(
                        r -> r.key("last_day").from("now-1d"),
                        r -> r.key("last_week").from("now-7d"),
                        r -> r.key("last_month").from("now-30d"),
                        r -> r.key("last_year").from("now-365d")
                    ))))
            .build();

        SearchHits<NodeDocument> hits = esOperations.search(query, NodeDocument.class);

        // 提取 facets
        Map<String, List<FacetValue>> facets = extractFacets(hits.getAggregations());

        return FacetedSearchResult.builder()
            .results(convertToPage(hits, request.getPageable()))
            .facets(facets)
            .build();
    }
}
```

### 4.2 前端交付分阶段计划

```markdown
## 前端交付策略

### MVP (Sprint 1-2):
- 基础文件管理界面
- 简单搜索框
- JSON 编辑器用于规则配置 (Monaco Editor)

### V1 (Sprint 3):
- 表单式规则配置界面
- 分类建议展示
- 搜索结果高亮

### V2 (Future):
- 可视化规则拖拽编辑器
- 高级 Faceted Search UI
- Dashboard 和报表
```

---

## 风险缓解策略

### 风险 1: Java ↔ Python ML 交互

| 方案 | 问题 | 我们的选择 |
|------|------|-----------|
| ProcessBuilder | 环境依赖、进程开销、并发瓶颈 | ❌ 不采用 |
| Jython | 库兼容性差 | ❌ 不采用 |
| GraalPython | 生态不成熟 | ❌ 不采用 |
| **HTTP 微服务** | 需要额外部署 | ✅ **采用** |

### 风险 2: Elasticsearch 数据一致性

```
三层存储策略:
┌───────────────────────────────────────────────────────────┐
│ Layer 1: MinIO/S3 (原始文件)                              │
│ - 永久保留，不可丢失                                       │
├───────────────────────────────────────────────────────────┤
│ Layer 2: PostgreSQL (提取文本、元数据)                     │
│ - Source of Truth，事务保证                               │
│ - ES 崩溃后可从此重建                                      │
├───────────────────────────────────────────────────────────┤
│ Layer 3: Elasticsearch (搜索索引)                         │
│ - 加速层，允许失败                                         │
│ - 提供 rebuildIndex() 方法重建                            │
└───────────────────────────────────────────────────────────┘
```

### 风险 3: 前端复杂度

分阶段交付策略，避免一次性开发过多复杂 UI。

---

## 系统集成支持 (符合产品组合战略)

> 基于 `PRODUCT_COMBINATION_STRATEGY.md` 和 `SYSTEM_STABILITY_GUIDE.md` 的要求

### 事件总线集成 (支持组合 A: Athena + DedupCAD)

```java
/**
 * 事件发布处理器
 * Order: 600 (管道最后执行)
 *
 * 发布标准事件到 RabbitMQ，供 DedupCAD 和 PLM 消费
 *
 * 事件契约:
 * - athena.document.created: 文档创建完成
 * - athena.document.updated: 文档更新
 * - athena.content.stored: 内容存储完成 (供 PLM 使用)
 */
@Component
@Slf4j
public class EventPublishingProcessor implements DocumentProcessor {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Value("${ecm.events.exchange:athena.events}")
    private String eventsExchange;

    @Override
    public ProcessingResult process(DocumentContext context) {
        Document document = context.getDocument();

        try {
            // 发布文档创建事件 (DedupCAD 消费)
            DocumentCreatedEvent event = DocumentCreatedEvent.builder()
                .documentId(document.getId())
                .name(document.getName())
                .mimeType(document.getMimeType())
                .contentId(document.getContentId())
                .size(document.getSize())
                .createdBy(document.getCreatedBy())
                .createdAt(document.getCreatedDate())
                .parentId(document.getParent() != null ?
                    document.getParent().getId() : null)
                .metadata(context.getExtractedMetadata())
                .build();

            rabbitTemplate.convertAndSend(
                eventsExchange,
                "document.created",
                event
            );

            log.info("Published document.created event for {}", document.getId());

            return ProcessingResult.success()
                .withData("eventPublished", true)
                .withData("eventType", "document.created");

        } catch (Exception e) {
            log.warn("Failed to publish event, but continuing: {}", e.getMessage());
            // 事件发布失败不应阻塞管道
            return ProcessingResult.success()
                .withData("eventPublished", false)
                .withData("error", e.getMessage());
        }
    }

    @Override
    public int getOrder() {
        return 600; // 最后执行
    }

    @Override
    public String getName() {
        return "EventPublishingProcessor";
    }
}

/**
 * 文档创建事件 (契约定义)
 *
 * 消费者: DedupCAD (查重分析), PLM (触发工作流)
 */
@Data
@Builder
public class DocumentCreatedEvent {
    private UUID documentId;
    private String name;
    private String mimeType;
    private String contentId;
    private Long size;
    private String createdBy;
    private LocalDateTime createdAt;
    private UUID parentId;
    private Map<String, String> metadata;

    // 事件版本 (用于契约兼容性)
    private String eventVersion = "1.0";
}
```

### OCR 处理器 (支持 AI 内容认知)

```java
/**
 * OCR 文本提取处理器
 * Order: 150 (Tika 之后，持久化之前)
 *
 * 支持图片类型的 OCR 文本提取
 * 满足 FUNCTIONAL_ENHANCEMENT_ROADMAP 的 "AI 内容认知" 要求
 */
@Component
@Slf4j
public class OcrTextExtractor implements DocumentProcessor {

    @Value("${ecm.ocr.enabled:false}")
    private boolean ocrEnabled;

    @Value("${ecm.ocr.service.url:http://ocr-service:8080}")
    private String ocrServiceUrl;

    @Autowired
    private RestTemplate restTemplate;

    // 支持 OCR 的图片类型
    private static final Set<String> IMAGE_TYPES = Set.of(
        "image/png",
        "image/jpeg",
        "image/tiff",
        "image/bmp"
    );

    @Override
    public ProcessingResult process(DocumentContext context) {
        if (!ocrEnabled) {
            return ProcessingResult.skipped("OCR disabled");
        }

        try {
            // 调用 OCR 服务 (Tesseract 或云服务)
            OcrRequest request = new OcrRequest(context.getContentStream());
            OcrResponse response = restTemplate.postForObject(
                ocrServiceUrl + "/api/ocr/extract",
                request,
                OcrResponse.class
            );

            if (response != null && response.getText() != null) {
                // 追加到提取文本
                String existingText = context.getExtractedText();
                String ocrText = response.getText();

                if (existingText != null) {
                    context.setExtractedText(existingText + "\n[OCR]\n" + ocrText);
                } else {
                    context.setExtractedText(ocrText);
                }

                // 提取标题栏信息 (图纸特有)
                if (response.getTitleBlockInfo() != null) {
                    Map<String, String> metadata = context.getExtractedMetadata();
                    if (metadata == null) {
                        metadata = new HashMap<>();
                    }
                    metadata.putAll(response.getTitleBlockInfo());
                    context.setExtractedMetadata(metadata);
                }

                log.info("OCR extracted {} characters from document {}",
                    ocrText.length(), context.getDocument().getId());

                return ProcessingResult.success()
                    .withData("ocrCharacters", ocrText.length())
                    .withData("hasTitleBlock", response.getTitleBlockInfo() != null);
            }

            return ProcessingResult.skipped("OCR returned empty result");

        } catch (Exception e) {
            log.warn("OCR extraction failed: {}", e.getMessage());
            // OCR 失败不阻塞管道
            return ProcessingResult.failed(e.getMessage());
        }
    }

    @Override
    public int getOrder() {
        return 150; // Tika (100) 之后，持久化 (200) 之前
    }

    @Override
    public String getName() {
        return "OcrTextExtractor";
    }

    @Override
    public boolean shouldProcess(DocumentContext context) {
        String mimeType = context.getDocument().getMimeType();
        return IMAGE_TYPES.contains(mimeType);
    }
}

@Data
public class OcrResponse {
    private String text;
    private double confidence;
    private Map<String, String> titleBlockInfo; // 图纸标题栏: 图号、材质、比例等
}
```

### PLM 存储集成 API (支持组合 C: PLM + Athena)

```java
/**
 * 外部存储提供者 API
 *
 * 支持 "组合 C: 企业级研发底座 (PLM + Athena)"
 * PLM 可以切换 StorageProvider 为 AthenaProvider，将附件存入 Athena
 */
@RestController
@RequestMapping("/api/v1/storage/external")
@Slf4j
public class ExternalStorageController {

    @Autowired
    private ExternalStorageService externalStorageService;

    /**
     * PLM 存储内容到 Athena
     *
     * PLM 调用此接口将物料附件存入 Athena，利用 Athena 的:
     * - 细粒度 ACL 权限控制
     * - 版本管理
     * - 全文检索
     */
    @PostMapping("/store")
    public ResponseEntity<ExternalStorageResult> storeContent(
            @RequestParam("file") MultipartFile file,
            @RequestParam("sourceSystem") String sourceSystem,
            @RequestParam("externalId") String externalId,
            @RequestParam(value = "folderId", required = false) UUID folderId,
            @RequestParam(value = "metadata", required = false) String metadataJson) {

        log.info("External storage request from {} for {}", sourceSystem, externalId);

        ExternalStorageRequest request = ExternalStorageRequest.builder()
            .file(file)
            .sourceSystem(sourceSystem)
            .externalId(externalId)
            .targetFolderId(folderId)
            .metadata(parseMetadata(metadataJson))
            .build();

        ExternalStorageResult result = externalStorageService.store(request);

        return ResponseEntity.ok(result);
    }

    /**
     * PLM 获取内容
     */
    @GetMapping("/{sourceSystem}/{externalId}/content")
    public ResponseEntity<Resource> getContent(
            @PathVariable String sourceSystem,
            @PathVariable String externalId) {

        ExternalContent content = externalStorageService.getContent(sourceSystem, externalId);

        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(content.getMimeType()))
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + content.getFileName() + "\"")
            .body(new InputStreamResource(content.getInputStream()));
    }

    /**
     * PLM 查询关联文档
     */
    @GetMapping("/{sourceSystem}/{externalId}")
    public ResponseEntity<ExternalDocumentInfo> getDocumentInfo(
            @PathVariable String sourceSystem,
            @PathVariable String externalId) {

        ExternalDocumentInfo info = externalStorageService.getDocumentInfo(sourceSystem, externalId);
        return ResponseEntity.ok(info);
    }

    /**
     * PLM 删除关联
     */
    @DeleteMapping("/{sourceSystem}/{externalId}")
    public ResponseEntity<Void> deleteContent(
            @PathVariable String sourceSystem,
            @PathVariable String externalId) {

        externalStorageService.delete(sourceSystem, externalId);
        return ResponseEntity.noContent().build();
    }
}

@Data
@Builder
public class ExternalStorageResult {
    private UUID athenaDocumentId;
    private String contentId;
    private String sourceSystem;
    private String externalId;
    private Long size;
    private String mimeType;
    private LocalDateTime storedAt;
}

/**
 * 外部系统文档关联实体
 */
@Entity
@Table(name = "external_document_mappings",
       uniqueConstraints = @UniqueConstraint(columnNames = {"source_system", "external_id"}))
@Data
public class ExternalDocumentMapping extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "source_system", nullable = false)
    private String sourceSystem;  // e.g., "PLM", "ERP"

    @Column(name = "external_id", nullable = false)
    private String externalId;    // 外部系统的 ID

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Column(name = "sync_status")
    @Enumerated(EnumType.STRING)
    private SyncStatus syncStatus = SyncStatus.SYNCED;

    public enum SyncStatus {
        SYNCED,
        PENDING,
        ERROR
    }
}
```

### API 契约测试支持

```java
/**
 * 契约测试相关配置
 *
 * 符合 SYSTEM_STABILITY_GUIDE 的契约测试要求
 */
@Configuration
public class ContractTestConfig {

    /**
     * API 废弃响应头拦截器
     * 当 API 计划废弃时，添加 Warning 响应头通知消费者
     */
    @Bean
    public FilterRegistrationBean<DeprecationFilter> deprecationFilter() {
        FilterRegistrationBean<DeprecationFilter> registration =
            new FilterRegistrationBean<>();
        registration.setFilter(new DeprecationFilter());
        registration.addUrlPatterns("/api/v1/*");
        return registration;
    }
}

/**
 * API 废弃过滤器
 */
public class DeprecationFilter implements Filter {

    // 即将废弃的 API 端点
    private static final Map<String, String> DEPRECATED_APIS = Map.of(
        // "/api/v1/old-endpoint", "Migrate to /api/v2/new-endpoint by 2025-06"
    );

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        String path = req.getRequestURI();
        if (DEPRECATED_APIS.containsKey(path)) {
            res.addHeader("Warning", "299 - " + DEPRECATED_APIS.get(path));
        }

        chain.doFilter(request, response);
    }
}
```

---

## 实现检查清单

### Sprint 1: 智能管道 MVP

- [ ] DocumentProcessor 接口
- [ ] DocumentPipelineService
- [ ] TikaTextExtractor
- [ ] ContentPersistenceProcessor
- [ ] ElasticsearchIndexProcessor
- [ ] IndexingConsumer (RabbitMQ)
- [ ] IndexRebuildService
- [ ] ExtractedContent 实体
- [ ] 重构 DocumentController.upload()
- [ ] RabbitMQ 配置
- [ ] **EventPublishingProcessor** (事件总线集成)
- [ ] 集成测试

### Sprint 2: 基础权限增强

- [ ] DynamicAuthority 接口
- [ ] OwnerDynamicAuthority
- [ ] LockOwnerDynamicAuthority
- [ ] 增强 SecurityService
- [ ] ShareLink 实体
- [ ] ShareLinkService
- [ ] ShareLinkController
- [ ] TrashService
- [ ] TrashController
- [ ] 权限单元测试

### Sprint 3: ML 服务与规则引擎

- [ ] FastAPI ML 微服务
- [ ] MLServiceClient
- [ ] MLClassificationProcessor
- [ ] AutomationRule 实体
- [ ] RuleCondition / RuleAction
- [ ] RuleEngineService
- [ ] RuleEngineProcessor
- [ ] 规则管理 API
- [ ] Docker Compose 集成

### Sprint 4: 用户体验增强

- [ ] FacetedSearchService
- [ ] 搜索高亮
- [ ] 保存的视图功能
- [ ] 前端 MVP (基于 Monaco Editor)

---

> 文档版本: 2.0
>
> 更新日期: 2025-12-09
>
> 修订说明: 采用纵向切片开发模式，增加三层存储策略，修正 ML 服务为独立微服务
