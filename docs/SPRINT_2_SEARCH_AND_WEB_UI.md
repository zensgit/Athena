# Sprint 2: 全文搜索 + 简易 Web 界面 (Full-Text Search & Web UI)

## 概述

Sprint 2 实现了完整的全文搜索功能和简易 Web 界面，包括：
- 基于 Elasticsearch 的全文搜索
- 搜索结果高亮显示
- 索引重建机制（从 PostgreSQL 恢复）
- 拖拽上传的 Web 界面

## 架构设计

### 搜索架构

```
┌─────────────┐     ┌─────────────────┐     ┌─────────────────┐
│   Web UI    │ ──→ │ SearchController │ ──→ │ FullTextSearch  │
│  (Browser)  │     │   (REST API)     │     │    Service      │
└─────────────┘     └─────────────────┘     └────────┬────────┘
                                                      │
                    ┌─────────────────────────────────┼─────────────────┐
                    │                                 ▼                 │
                    │  ┌─────────────────┐    ┌─────────────────┐      │
                    │  │   PostgreSQL    │ ←─ │  Elasticsearch  │      │
                    │  │  (数据源头)     │    │   (搜索加速)    │      │
                    │  └─────────────────┘    └─────────────────┘      │
                    │         ↑                       ↓                 │
                    │         └───── rebuildIndex() ──┘                │
                    │              (恢复机制)                           │
                    └──────────────────────────────────────────────────┘
```

### 索引重建流程

```
rebuildIndex()
    │
    ├─→ 1. 检查是否已在重建中 (AtomicBoolean)
    │
    ├─→ 2. 删除现有 ES 索引
    │
    ├─→ 3. 创建新索引 + 映射
    │
    ├─→ 4. 分页读取 PostgreSQL
    │       │
    │       └─→ 每批 100 条文档
    │               │
    │               └─→ 转换为 NodeDocument
    │                       │
    │                       └─→ 保存到 ES
    │
    └─→ 5. 返回索引数量
```

## 核心组件

### 1. FullTextSearchService

全文搜索服务，提供搜索和索引管理功能。

```java
@Service
public class FullTextSearchService {

    // 基础搜索
    public Page<SearchResult> search(String queryText, int page, int size);

    // 高级搜索（带过滤器）
    public Page<SearchResult> advancedSearch(SearchRequest request);

    // 索引重建（从 PostgreSQL）
    public int rebuildIndex();

    // 重建状态
    public Map<String, Object> getRebuildStatus();

    // 索引统计
    public Map<String, Object> getIndexStats();
}
```

**搜索字段：**
- `name` - 文件名
- `content` - 文档内容
- `textContent` - 提取的文本
- `title` - 文档标题
- `description` - 描述

### 2. SearchResult

搜索结果 DTO，包含高亮支持。

```java
@Data
@Builder
public class SearchResult {
    private String id;
    private String name;
    private String description;
    private String mimeType;
    private Long fileSize;
    private String createdBy;
    private LocalDateTime createdDate;
    private float score;                           // 相关性分数
    private Map<String, List<String>> highlights;  // 高亮片段

    public String getFileSizeFormatted();  // 人类可读大小
}
```

### 3. SearchFilters

高级搜索过滤器。

```java
@Data
public class SearchFilters {
    private List<String> nodeTypes;    // 节点类型
    private List<String> mimeTypes;    // MIME 类型
    private String createdBy;          // 创建者
    private LocalDateTime dateFrom;    // 开始日期
    private LocalDateTime dateTo;      // 结束日期
    private Long minSize;              // 最小大小
    private Long maxSize;              // 最大大小
    private List<String> tags;         // 标签
    private List<String> categories;   // 分类
    private String path;               // 路径
    private boolean includeDeleted;    // 包含已删除
}
```

## REST API

### 搜索端点

#### 全文搜索

```http
GET /api/v1/search?q=keyword&page=0&size=20

Response:
{
  "content": [
    {
      "id": "uuid",
      "name": "document.pdf",
      "description": "A sample document",
      "mimeType": "application/pdf",
      "fileSize": 1024000,
      "fileSizeFormatted": "1.0 MB",
      "createdBy": "admin",
      "createdDate": "2025-01-15T10:30:00",
      "score": 5.234,
      "highlights": {
        "content": ["...matching <em>keyword</em> in text..."]
      }
    }
  ],
  "totalElements": 42,
  "totalPages": 3,
  "number": 0,
  "size": 20
}
```

#### 高级搜索

```http
POST /api/v1/search/advanced
Content-Type: application/json

{
  "query": "contract agreement",
  "filters": {
    "mimeTypes": ["application/pdf"],
    "createdBy": "admin",
    "dateFrom": "2025-01-01T00:00:00",
    "dateTo": "2025-12-31T23:59:59",
    "includeDeleted": false
  },
  "highlightEnabled": true,
  "pageable": {
    "page": 0,
    "size": 20
  }
}
```

#### 快速搜索

```http
GET /api/v1/search/quick?q=keyword&limit=10
```

### 索引管理端点

#### 重建索引

```http
POST /api/v1/search/index/rebuild
Authorization: Bearer <admin-token>

Response (成功):
{
  "status": "completed",
  "documentsIndexed": 1500,
  "message": "Index rebuild completed successfully"
}

Response (进行中):
{
  "status": "in_progress",
  "message": "Index rebuild already in progress"
}
```

#### 重建状态

```http
GET /api/v1/search/index/rebuild/status

Response:
{
  "inProgress": true,
  "documentsIndexed": 750
}
```

#### 索引统计

```http
GET /api/v1/search/index/stats

Response:
{
  "indexName": "ecm_documents",
  "documentCount": 1500,
  "searchEnabled": true
}
```

#### 单文档索引

```http
POST /api/v1/search/index/{documentId}
DELETE /api/v1/search/index/{documentId}
```

## Web 界面

### 功能特性

简易单页应用，提供三个主要功能模块：

#### 1. 文档上传

```
┌─────────────────────────────────────────────┐
│  ┌─────────────────────────────────────┐    │
│  │                                     │    │
│  │     📁 拖拽文件到此处或点击上传      │    │
│  │                                     │    │
│  │     支持 PDF, Word, Excel...        │    │
│  └─────────────────────────────────────┘    │
│                                             │
│  📄 document.pdf    1.2 MB   ✅ Success     │
│  📄 report.docx     256 KB   ✅ Success     │
│                                             │
│  [        上传文件        ]                  │
└─────────────────────────────────────────────┘
```

**功能：**
- 拖拽上传
- 点击选择文件
- 多文件批量上传
- 上传进度显示
- 状态反馈（Pending → Uploading → Success/Error）

#### 2. 全文搜索

```
┌─────────────────────────────────────────────┐
│  [    输入关键词搜索...    ] [  搜索  ]     │
│                                             │
│  ┌─────────────────────────────────────┐    │
│  │ Score: 5.23                         │    │
│  │ 📄 合同协议.pdf                      │    │
│  │ application/pdf | 1.2 MB | 2025-01  │    │
│  │ ...matching <em>关键词</em> in...   │    │
│  └─────────────────────────────────────┘    │
│                                             │
│  ┌─────────────────────────────────────┐    │
│  │ Score: 4.15                         │    │
│  │ 📄 项目报告.docx                     │    │
│  │ ...                                 │    │
│  └─────────────────────────────────────┘    │
└─────────────────────────────────────────────┘
```

**功能：**
- 实时搜索
- 高亮显示匹配词
- 相关性分数显示
- 文件元信息展示

#### 3. 索引统计

```
┌─────────────────────────────────────────────┐
│  ┌──────────┐  ┌──────────┐  ┌──────────┐   │
│  │   1500   │  │   ecm_   │  │  Active  │   │
│  │ Documents│  │documents │  │  Status  │   │
│  │ Indexed  │  │  Index   │  │          │   │
│  └──────────┘  └──────────┘  └──────────┘   │
│                                             │
│  [    重建索引    ]                          │
│                                             │
│  ✅ Index rebuilt! 1500 documents indexed.  │
└─────────────────────────────────────────────┘
```

**功能：**
- 索引文档数量
- 索引名称
- 搜索状态
- 一键重建索引

### 技术实现

- **纯前端** - 无需额外前端框架
- **单文件** - `index.html` 包含 HTML/CSS/JS
- **响应式** - 移动端兼容
- **API 调用** - Fetch API
- **拖拽支持** - HTML5 Drag & Drop API

## 文件结构

```
src/main/
├── java/com/ecm/core/
│   ├── search/
│   │   ├── FullTextSearchService.java   # 全文搜索服务
│   │   ├── SearchResult.java            # 搜索结果 DTO
│   │   ├── SearchRequest.java           # 搜索请求
│   │   ├── SearchFilters.java           # 搜索过滤器
│   │   ├── SearchIndexService.java      # 索引服务
│   │   └── NodeDocument.java            # ES 文档模型
│   └── controller/
│       └── SearchController.java        # 搜索 API
└── resources/
    └── static/
        └── index.html                   # Web 界面
```

## 配置

```yaml
ecm:
  search:
    enabled: true
    index-name: ecm_documents
    batch-size: 100
    highlight:
      enabled: true
      pre-tag: "<em>"
      post-tag: "</em>"

spring:
  elasticsearch:
    uris: http://localhost:9200
    username: elastic
    password: elastic_password
```

## 安全考量

### 权限控制

| 端点 | 权限要求 |
|------|----------|
| `GET /api/v1/search` | 公开 |
| `POST /api/v1/search/advanced` | 公开 |
| `POST /api/v1/search/index/rebuild` | ADMIN |
| `GET /api/v1/search/index/rebuild/status` | ADMIN |
| `POST /api/v1/search/index/{id}` | ADMIN, EDITOR |
| `DELETE /api/v1/search/index/{id}` | ADMIN |

### 数据安全

- 搜索结果自动过滤已删除文档（除非明确请求）
- 用户只能搜索有权限访问的文档（通过 ACL 过滤搜索结果与聚合统计）

## 数据一致性保证

### ES 数据丢失恢复

```
PostgreSQL (Source of Truth)
       │
       │ rebuildIndex()
       ▼
Elasticsearch (Acceleration Layer)
```

**恢复步骤：**
1. 调用 `POST /api/v1/search/index/rebuild`
2. 系统自动从 PostgreSQL 读取所有文档
3. 批量重建 ES 索引
4. 搜索功能恢复

### 实时同步

文档创建/更新时：
1. 先保存到 PostgreSQL（事务）
2. 再索引到 ES（异步，允许失败）
3. ES 失败不影响主流程

## 性能优化

1. **分页搜索** - 默认每页 20 条
2. **批量重建** - 每批 100 条文档
3. **异步索引** - 上传时 ES 索引不阻塞
4. **缓存** - ES 自带查询缓存
5. **原子操作** - 重建状态使用 AtomicBoolean

## 扩展计划

### Sprint 3 预留

- [ ] 权限过滤搜索结果
- [ ] 搜索建议/自动完成
- [ ] Faceted 搜索（按类型、日期聚合）
- [ ] 搜索历史记录
- [ ] 更丰富的 Web 界面（React/Vue）
