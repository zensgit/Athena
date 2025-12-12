# Sprint 1: 智能文档处理管道 (Document Processing Pipeline)

## 概述

Sprint 1 实现了完整的文档处理管道架构，支持从上传到索引的全流程自动化处理。采用可插拔处理器模式，便于扩展和维护。

## 架构设计

### 核心理念

```
上传 → 存储 → 提取 → 持久化 → 索引 → 事件
  │       │       │        │        │       │
  └───────┴───────┴────────┴────────┴───────┘
              DocumentProcessingPipeline
```

### 三层存储策略

```
┌─────────────────────────────────────────────────────────────────┐
│                    三层存储架构                                   │
├─────────────────┬─────────────────┬─────────────────────────────┤
│   Layer 1       │   Layer 2       │   Layer 3                   │
│   MinIO/FS      │   PostgreSQL    │   Elasticsearch             │
│   (原始内容)    │   (数据源头)    │   (加速层)                   │
├─────────────────┼─────────────────┼─────────────────────────────┤
│ • 二进制文件    │ • 元数据        │ • 全文索引                   │
│ • 内容寻址存储  │ • 关系数据      │ • 搜索加速                   │
│ • 去重优化      │ • 事务保证      │ • 可重建                     │
└─────────────────┴─────────────────┴─────────────────────────────┘
                          ↓
              PostgreSQL 是唯一数据源头
              ES 数据丢失可从 PG 重建
```

## 核心组件

### 1. 处理器接口 (DocumentProcessor)

```java
public interface DocumentProcessor {
    ProcessingResult process(DocumentContext context);
    int getOrder();                    // 执行顺序
    boolean supports(DocumentContext context);  // 是否支持
    String getName();
}
```

**执行顺序规范：**
| Order | 阶段 | 说明 |
|-------|------|------|
| 100 | 内容存储 | 保存原始文件 |
| 200 | 文本提取 | Tika 提取文本/元数据 |
| 300 | 内容丰富 | 分类、标签（预留） |
| 400 | 元数据持久化 | 保存到 PostgreSQL |
| 500 | 搜索索引 | 索引到 Elasticsearch |
| 600 | 事件发布 | 发布领域事件 |

### 2. 文档上下文 (DocumentContext)

```java
@Data
@Builder
public class DocumentContext {
    // 输入
    private String originalFilename;
    private InputStream inputStream;
    private String userId;
    private UUID parentFolderId;

    // 处理器填充
    private String contentId;      // ContentStorageProcessor
    private String mimeType;       // ContentStorageProcessor
    private Long fileSize;         // ContentStorageProcessor
    private String extractedText;  // TikaTextExtractor
    private Map<String, String> extractedMetadata;  // TikaTextExtractor

    // 输出
    private UUID documentId;       // MetadataPersistenceProcessor
}
```

### 3. 处理结果 (ProcessingResult)

```java
public class ProcessingResult {
    public enum Status {
        SUCCESS,   // 成功继续
        SKIPPED,   // 跳过（不支持）
        FAILED,    // 失败但继续
        FATAL      // 致命错误，停止管道
    }
}
```

## 实现的处理器

### ContentStorageProcessor (Order: 100)

**职责：** 将原始文件存储到内容存储系统

```java
@Override
public ProcessingResult process(DocumentContext context) {
    // 1. 检测 MIME 类型
    // 2. 存储到 MinIO/FileSystem
    // 3. 设置 contentId, mimeType, fileSize
    return ProcessingResult.success("Content stored");
}
```

### TikaTextExtractor (Order: 200)

**职责：** 使用 Apache Tika 提取文本和元数据

```java
// 支持的文件类型
- PDF, Word (doc/docx), Excel (xls/xlsx), PowerPoint (ppt/pptx)
- 纯文本, HTML, XML, Markdown
- 电子邮件 (eml, msg)

// 跳过的类型
- 图片 (image/*)
- 视频 (video/*)
- 音频 (audio/*)
- 压缩包 (application/zip, application/x-rar)

// 提取的元数据
- title, author, pageCount, wordCount
- createdDate, modifiedDate
```

### MetadataPersistenceProcessor (Order: 400)

**职责：** 将文档元数据持久化到 PostgreSQL

```java
@Override
public ProcessingResult process(DocumentContext context) {
    // 1. 创建 Document 实体
    // 2. 填充所有元数据
    // 3. 保存到 PostgreSQL (事务)
    // 4. 发布 NodeCreatedEvent
    return ProcessingResult.success();
}
```

### SearchIndexProcessor (Order: 500)

**职责：** 将文档索引到 Elasticsearch

```java
@Override
public ProcessingResult process(DocumentContext context) {
    // 非致命 - ES 失败不影响主流程
    try {
        searchIndexService.indexDocument(document);
        return ProcessingResult.success();
    } catch (Exception e) {
        return ProcessingResult.failed("ES indexing failed");
    }
}
```

## API 端点

### 文档上传

```http
POST /api/v1/documents/upload
Content-Type: multipart/form-data

file: <binary>
parentFolderId: <uuid> (optional)
```

**响应：**
```json
{
  "documentId": "uuid",
  "name": "document.pdf",
  "mimeType": "application/pdf",
  "fileSize": 1024000,
  "pipelineStatus": "SUCCESS",
  "processorResults": [
    {"processor": "ContentStorage", "status": "SUCCESS"},
    {"processor": "TikaTextExtractor", "status": "SUCCESS"},
    {"processor": "MetadataPersistence", "status": "SUCCESS"},
    {"processor": "SearchIndex", "status": "SUCCESS"}
  ]
}
```

### 批量上传

```http
POST /api/v1/documents/upload/batch
Content-Type: multipart/form-data

files: <binary[]>
parentFolderId: <uuid> (optional)
```

### 管道状态

```http
GET /api/v1/documents/pipeline/status
```

## 文件结构

```
src/main/java/com/ecm/core/
├── pipeline/
│   ├── DocumentProcessor.java          # 处理器接口
│   ├── DocumentContext.java            # 上下文对象
│   ├── ProcessingResult.java           # 处理结果
│   ├── PipelineResult.java             # 管道结果
│   ├── ProcessorExecution.java         # 执行记录
│   ├── DocumentProcessingPipeline.java # 管道编排器
│   └── processor/
│       ├── ContentStorageProcessor.java
│       ├── TikaTextExtractor.java
│       ├── MetadataPersistenceProcessor.java
│       └── SearchIndexProcessor.java
├── service/
│   └── DocumentUploadService.java      # 上传服务
└── controller/
    └── UploadController.java           # REST API
```

## 配置

```yaml
ecm:
  tika:
    max-text-length: 10485760  # 10MB 文本限制

  search:
    enabled: true
```

## 扩展点

### 添加新处理器

```java
@Component
public class CustomProcessor implements DocumentProcessor {

    @Override
    public ProcessingResult process(DocumentContext context) {
        // 自定义处理逻辑
        return ProcessingResult.success();
    }

    @Override
    public int getOrder() {
        return 350;  // 在持久化之前
    }

    @Override
    public boolean supports(DocumentContext context) {
        return "application/pdf".equals(context.getMimeType());
    }
}
```

### 预留扩展

- **300: ContentEnrichmentProcessor** - AI 分类、自动标签
- **350: ThumbnailGenerator** - 缩略图生成
- **450: VersioningProcessor** - 版本控制
- **600: EventPublishingProcessor** - 外部事件发布

## 单元测试

```
src/test/java/com/ecm/core/pipeline/
├── DocumentProcessingPipelineTest.java  # 管道测试
└── processor/
    └── TikaTextExtractorTest.java       # Tika 测试
```

**测试覆盖：**
- 处理器顺序执行
- 致命错误停止管道
- 非致命错误继续执行
- 不支持的处理器跳过
- 执行时间记录

## 性能考量

1. **流式处理** - 大文件不全部加载到内存
2. **异步索引** - ES 索引失败不阻塞主流程
3. **批量支持** - 支持并发批量上传
4. **超时控制** - Tika 提取有超时保护
