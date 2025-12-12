# Sprint 4: 用户体验增强

## 目标
提升文件管理和搜索体验，实现文件夹管理、文件复制/移动操作以及高级分面搜索功能。

## 实现的功能

### 1. 文件夹管理服务 (FolderService)

提供完整的文件夹管理功能。

#### 核心功能
```
com.ecm.core.service.FolderService
```

**功能特性：**
- 创建/更新/删除文件夹
- 文件夹重命名
- 获取文件夹内容（分页、排序、过滤）
- 文件夹树形结构导航
- 面包屑路径
- 文件夹统计信息
- 容量检查（最大项数、允许的文件类型）

#### 文件夹类型
```java
enum FolderType {
    GENERAL,    // 通用文件夹
    WORKSPACE,  // 工作空间
    PROJECT,    // 项目文件夹
    ARCHIVE,    // 归档文件夹
    SYSTEM,     // 系统文件夹
    TEMP        // 临时文件夹
}
```

#### FolderService 方法

| 方法 | 描述 |
|------|------|
| `createFolder(request)` | 创建新文件夹 |
| `getFolder(folderId)` | 获取文件夹 |
| `getFolderByPath(path)` | 通过路径获取文件夹 |
| `getRootFolders()` | 获取根文件夹列表 |
| `getFolderContents(folderId, pageable)` | 获取文件夹内容（分页） |
| `getFolderContentsFiltered(folderId, filter)` | 获取过滤后的内容 |
| `updateFolder(folderId, request)` | 更新文件夹 |
| `renameFolder(folderId, newName)` | 重命名文件夹 |
| `deleteFolder(folderId, permanent, recursive)` | 删除文件夹 |
| `getFolderTree(rootId, maxDepth)` | 获取文件夹树 |
| `getFolderBreadcrumb(folderId)` | 获取面包屑路径 |
| `getFoldersByType(type)` | 按类型获取文件夹 |
| `getFolderStats(folderId)` | 获取文件夹统计 |
| `canAcceptItems(folderId, itemCount)` | 检查容量 |
| `canAcceptFileType(folderId, mimeType)` | 检查文件类型 |

### 2. 文件复制/移动操作

NodeService 已包含完整的复制移动功能。

#### 移动操作
```java
Node moveNode(UUID nodeId, UUID targetParentId)
```
- 权限检查：源节点 DELETE 权限 + 目标文件夹 CREATE_CHILDREN 权限
- 循环引用检查
- 名称冲突检查
- 发布 NodeMovedEvent 事件

#### 复制操作
```java
Node copyNode(UUID nodeId, UUID targetParentId, String newName, boolean deep)
```
- 权限检查：源节点 READ 权限 + 目标文件夹 CREATE_CHILDREN 权限
- 支持深度复制（包含子节点）
- 复制元数据和权限
- 发布 NodeCopiedEvent 事件

### 3. FolderController REST API

```
/api/folders
```

#### 基本操作
```
POST   /api/folders                        创建文件夹
GET    /api/folders/{folderId}             获取文件夹
GET    /api/folders/path?path=xxx          通过路径获取
GET    /api/folders/roots                  获取根文件夹
PUT    /api/folders/{folderId}             更新文件夹
PATCH  /api/folders/{folderId}/rename      重命名文件夹
DELETE /api/folders/{folderId}             删除文件夹
```

#### 内容管理
```
GET    /api/folders/{folderId}/contents            获取内容（分页）
GET    /api/folders/{folderId}/contents/filtered   过滤获取内容
GET    /api/folders/{folderId}/stats               获取统计信息
GET    /api/folders/{folderId}/can-accept          检查是否可添加
```

#### 导航
```
GET    /api/folders/tree                   获取文件夹树
GET    /api/folders/{folderId}/breadcrumb  获取面包屑
GET    /api/folders/type/{type}            按类型获取
```

#### 复制/移动
```
POST   /api/folders/{folderId}/move        移动节点到文件夹
POST   /api/folders/{folderId}/copy        复制节点到文件夹
POST   /api/folders/{folderId}/batch-move  批量移动
POST   /api/folders/{folderId}/batch-copy  批量复制
```

### 4. 分面搜索 (Faceted Search)

提供高级搜索功能，支持分面导航和聚合统计。

#### FacetedSearchService 功能
```
com.ecm.core.search.FacetedSearchService
```

**搜索能力：**
- 分面搜索（按类别过滤）
- 聚合统计（各分面的文档数量）
- 多字段搜索（支持权重提升）
- 智能搜索（自动增强查询）
- 文件夹内搜索
- 相似文档推荐
- 搜索建议（自动补全）

#### 分面类型

| 分面 | 描述 |
|------|------|
| mimeType | 文件类型 |
| createdBy | 创建者 |
| tags | 标签 |
| categories | 分类 |

#### 搜索 API

```
POST   /api/v1/search/faceted           分面搜索
GET    /api/v1/search/smart?q=xxx       智能搜索
GET    /api/v1/search/folder/{path}     文件夹内搜索
GET    /api/v1/search/facets            获取分面
GET    /api/v1/search/suggestions       搜索建议
GET    /api/v1/search/similar/{id}      相似文档
GET    /api/v1/search/filters/suggested 推荐过滤器
```

## 新增文件清单

### 服务层
```
src/main/java/com/ecm/core/service/
└── FolderService.java              # 文件夹管理服务
```

### 搜索模块
```
src/main/java/com/ecm/core/search/
└── FacetedSearchService.java       # 分面搜索服务
```

### 控制器层
```
src/main/java/com/ecm/core/controller/
└── FolderController.java           # 文件夹 REST 控制器
```

## 修改的文件

| 文件 | 修改内容 |
|------|----------|
| `SearchController.java` | 添加分面搜索端点 |

## API 请求/响应示例

### 创建文件夹
```json
POST /api/folders
{
    "name": "Project Documents",
    "description": "Project documentation folder",
    "parentId": "uuid-of-parent",
    "folderType": "PROJECT",
    "maxItems": 100,
    "allowedTypes": "application/pdf,image/*",
    "inheritPermissions": true
}
```

### 获取文件夹树
```json
GET /api/folders/tree?rootId=uuid&maxDepth=3

Response:
[
    {
        "id": "uuid",
        "name": "Root",
        "path": "/Root",
        "folderType": "GENERAL",
        "childCount": 5,
        "hasChildren": true,
        "children": [
            {
                "id": "uuid-child",
                "name": "Documents",
                "path": "/Root/Documents",
                "folderType": "GENERAL",
                "childCount": 10,
                "hasChildren": true,
                "children": []
            }
        ]
    }
]
```

### 分面搜索
```json
POST /api/v1/search/faceted
{
    "query": "annual report",
    "filters": {
        "mimeTypes": ["application/pdf"],
        "createdBy": "john.doe",
        "dateFrom": "2024-01-01T00:00:00",
        "dateTo": "2024-12-31T23:59:59"
    },
    "facetFields": ["mimeType", "createdBy", "tags"],
    "highlightEnabled": true
}

Response:
{
    "results": {
        "content": [...],
        "totalElements": 42,
        "totalPages": 3
    },
    "facets": {
        "mimeType": [
            {"value": "application/pdf", "count": 30},
            {"value": "application/msword", "count": 12}
        ],
        "createdBy": [
            {"value": "john.doe", "count": 25},
            {"value": "jane.smith", "count": 17}
        ],
        "tags": [
            {"value": "finance", "count": 20},
            {"value": "annual", "count": 15}
        ]
    },
    "totalHits": 42,
    "queryTime": 45
}
```

### 批量移动
```json
POST /api/folders/{targetFolderId}/batch-move
{
    "nodeIds": ["uuid1", "uuid2", "uuid3"]
}

Response:
{
    "success": 3,
    "failed": 0,
    "total": 3
}
```

## 架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                    Folder Management Flow                        │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│   [FolderController] ─→ [FolderService] ─→ [FolderRepository]   │
│           │                    │                                 │
│           │                    ├─→ [NodeRepository]              │
│           │                    │                                 │
│           │                    └─→ [SecurityService]             │
│           │                              │                       │
│           └───────────────────→ [Events: Created/Updated/Deleted]│
│                                                                  │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                    Copy/Move Operations                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│   moveNode:                                                      │
│   ┌──────────┐     ┌──────────┐     ┌──────────┐               │
│   │ Source   │ ──→ │ Permission│ ──→ │ Target   │              │
│   │ Folder   │     │ Check    │     │ Folder   │               │
│   └──────────┘     └──────────┘     └──────────┘               │
│                          │                                       │
│                          ↓                                       │
│                 ┌──────────────────┐                            │
│                 │ Circular Check   │                            │
│                 │ Name Conflict    │                            │
│                 └──────────────────┘                            │
│                          │                                       │
│                          ↓                                       │
│                 ┌──────────────────┐                            │
│                 │ NodeMovedEvent   │                            │
│                 └──────────────────┘                            │
│                                                                  │
│   copyNode:                                                      │
│   ┌──────────┐     ┌──────────┐     ┌──────────┐               │
│   │ Source   │ ──→ │ Deep Copy│ ──→ │ New Node │               │
│   │ Node     │     │ (optional)│    │ + Children│              │
│   └──────────┘     └──────────┘     └──────────┘               │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                    Faceted Search Flow                           │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│   Query ─→ [Build Criteria] ─→ [Elasticsearch] ─→ [Results]     │
│              │                        │              │           │
│              ├─ Text Search           │              ├─ Hits     │
│              ├─ Filters               │              ├─ Facets   │
│              └─ Path Prefix           │              └─ Score    │
│                                       │                          │
│   Facets Aggregation:                 │                          │
│   ┌──────────────────────────────────┴───────────────┐          │
│   │                                                   │          │
│   │   mimeType: [PDF: 30, DOC: 20, XLS: 15]         │          │
│   │   createdBy: [john: 25, jane: 20, bob: 10]       │          │
│   │   tags: [finance: 20, report: 15, Q4: 10]        │          │
│   │                                                   │          │
│   └───────────────────────────────────────────────────┘          │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

## 配置示例

```yaml
ecm:
  folder:
    max-depth: 10              # 最大文件夹深度
    default-max-items: null    # 默认最大项数（null=无限）

  search:
    enabled: true
    facets:
      enabled: true
      default-fields:
        - mimeType
        - createdBy
        - tags
        - categories
    suggestions:
      min-prefix-length: 2
      max-results: 10
```

## 下一步计划 (Sprint 5)

根据 ECM_FEATURE_ROADMAP.md，下一阶段将实现：
- 工作流审批系统
- 版本比较功能
- 文档模板管理

## 技术亮点

1. **分面搜索**：支持多维度过滤和聚合统计
2. **智能搜索**：自动增强查询，提供搜索建议
3. **相似文档**：基于内容推荐相关文档
4. **批量操作**：支持批量复制/移动，提高效率
5. **文件夹树**：递归构建树形结构，支持深度限制
6. **权限继承**：创建文件夹时可选择继承父级权限
