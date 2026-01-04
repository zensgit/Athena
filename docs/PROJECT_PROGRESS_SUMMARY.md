# ECM Core - Project Progress Summary

## Overview

ECM Core 是一个企业内容管理系统的核心服务模块，采用 Spring Boot 3.x 构建，提供文档管理、版本控制、权限管理、全文搜索、ML 智能分类等功能。

## Recent Verification (2026-01-04)

- Frontend E2E: `ECM_UI_URL=http://localhost:5500 ECM_API_URL=http://localhost:7700 npx playwright test` (15 passed)
- Backend tests: `docker run --rm -v "$(pwd)":/workspace -v "$HOME/.m2":/root/.m2 -w /workspace/ecm-core maven:3-eclipse-temurin-17 mvn test` (17 tests, 0 failures)
- Backend verify: `docker run --rm -v "$(pwd)":/workspace -v "$HOME/.m2":/root/.m2 -w /workspace/ecm-core maven:3-eclipse-temurin-17 mvn verify` (17 tests, 0 failures)

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           ECM Core (Spring Boot)                         │
├─────────────────────────────────────────────────────────────────────────┤
│  Controllers                                                             │
│  ├── NodeController      (文档/文件夹 CRUD)                              │
│  ├── FolderController    (文件夹树操作)                                  │
│  ├── VersionController   (版本管理)                                      │
│  ├── SearchController    (全文搜索)                                      │
│  ├── RuleController      (自动化规则)                                    │
│  └── MLController        (ML 服务集成)                                   │
├─────────────────────────────────────────────────────────────────────────┤
│  Services                                                                │
│  ├── NodeService         (节点业务逻辑)                                  │
│  ├── FolderService       (文件夹操作)                                    │
│  ├── VersionService      (版本控制)                                      │
│  ├── SecurityService     (权限检查)                                      │
│  ├── SearchService       (Elasticsearch 集成)                           │
│  ├── FacetedSearchService(分面搜索)                                      │
│  ├── RuleEngineService   (规则引擎)                                      │
│  ├── TagService          (标签管理)                                      │
│  ├── CategoryService     (分类管理)                                      │
│  └── CommentService      (评论管理)                                      │
├─────────────────────────────────────────────────────────────────────────┤
│  External Integrations                                                   │
│  ├── MLServiceClient     (FastAPI ML 微服务)                             │
│  ├── AlfrescoPermissionService (Alfresco 权限同步)                       │
│  ├── ConversionService   (文档转换)                                      │
│  └── PreviewService      (预览生成)                                      │
└─────────────────────────────────────────────────────────────────────────┘
           │                    │                    │
           ▼                    ▼                    ▼
   ┌──────────────┐    ┌──────────────┐    ┌──────────────┐
   │  PostgreSQL  │    │ Elasticsearch │    │    MinIO     │
   │  (元数据)    │    │  (全文索引)   │    │  (文件存储)  │
   └──────────────┘    └──────────────┘    └──────────────┘
```

## Sprint Progress

### Sprint 2: Workflow ✅ COMPLETED

**目标**: 集成 Flowable 工作流引擎，实现文档审批流程

**已完成功能**:

| 组件 | 文件 | 功能描述 |
|------|------|----------|
| BPMN Process | `workflows/document-approval.bpmn20.xml` | 标准审批流程定义 |
| WorkflowService | `service/WorkflowService.java` | 流程启动、任务管理、状态更新 |
| WorkflowController | `controller/WorkflowController.java` | 工作流 REST API |
| NodeStatus | `entity/Node.java` | 新增 PENDING_APPROVAL/APPROVED/REJECTED 状态 |

**API Endpoints**:
- `GET /api/v1/workflows/definitions` - 查看流程定义
- `POST /api/v1/workflows/document/{id}/approval` - 发起审批
- `GET /api/v1/workflows/tasks/my` - 查看待办任务
- `POST /api/v1/workflows/tasks/{id}/complete` - 完成任务
- `GET /api/v1/workflows/document/{id}/history` - 查看审批历史

---

### Sprint 5: Analytics & Monitoring ✅ COMPLETED

**目标**: 提供系统使用情况统计、存储分析和审计日志监控

**已完成功能**:

| 组件 | 文件 | 功能描述 |
|------|------|----------|
| AuditLog | `entity/AuditLog.java` | 审计日志实体 (JPA) |
| AuditLogRepository | `repository/AuditLogRepository.java` | 日志查询与统计接口 |
| AnalyticsService | `service/AnalyticsService.java` | 数据聚合与统计服务 |
| AnalyticsController | `controller/AnalyticsController.java` | 监控仪表盘 REST API |
| AuditService | `service/AuditService.java` | 重构为使用 Repository 模式 |

**API Endpoints**:
- `GET /api/v1/analytics/dashboard` - 仪表盘聚合数据
- `GET /api/v1/analytics/summary` - 系统概览 (总数/容量)
- `GET /api/v1/analytics/storage/mimetype` - 存储分布统计
- `GET /api/v1/analytics/activity/daily` - 每日活动趋势
- `GET /api/v1/analytics/audit/recent` - 实时审计日志

---

### Sprint 4: UX Enhancement ✅ COMPLETED

**目标**: 增强用户体验，提供文件夹树导航和分面搜索

**已完成功能**:

| 组件 | 文件 | 功能描述 |
|------|------|----------|
| FolderService | `service/FolderService.java` | 文件夹树构建、面包屑导航、移动/复制操作 |
| FolderController | `controller/FolderController.java` | REST API - 树视图、子节点、面包屑 |
| FacetedSearchService | `service/FacetedSearchService.java` | 多维度分面搜索、聚合统计 |

**API Endpoints**:
- `GET /api/v1/folders/tree` - 获取文件夹树
- `GET /api/v1/folders/{id}/children` - 获取子节点
- `GET /api/v1/folders/{id}/breadcrumb` - 获取面包屑路径
- `POST /api/v1/folders/{id}/move` - 移动文件夹
- `POST /api/v1/folders/{id}/copy` - 复制文件夹
- `GET /api/v1/search/faceted` - 分面搜索

---

### Sprint 3: ML Service & Rule Engine ✅ COMPLETED

**目标**: 实现智能自动化，包括 ML 分类和规则引擎

**已完成功能**:

| 组件 | 文件 | 功能描述 |
|------|------|----------|
| AutomationRule | `entity/AutomationRule.java` | 自动化规则实体 (JSONB 条件/动作) |
| RuleCondition | `entity/RuleCondition.java` | 嵌套条件模型 (AND/OR/NOT/SIMPLE) |
| RuleAction | `entity/RuleAction.java` | 动作模型 (15+ 动作类型) |
| RuleExecutionResult | `entity/RuleExecutionResult.java` | 执行结果跟踪 |
| AutomationRuleRepository | `repository/AutomationRuleRepository.java` | 规则数据访问 |
| RuleEngineService | `service/RuleEngineService.java` | 规则评估与执行引擎 |
| RuleController | `controller/RuleController.java` | 规则管理 REST API |
| MLServiceClient | `ml/MLServiceClient.java` | ML 微服务 HTTP 客户端 |
| MLController | `controller/MLController.java` | ML 功能 REST API |

**触发类型**:
- `DOCUMENT_CREATED` - 新文档上传
- `DOCUMENT_UPDATED` - 文档更新
- `DOCUMENT_MOVED` - 文档移动
- `DOCUMENT_TAGGED` - 添加标签
- `VERSION_CREATED` - 新版本创建
- `COMMENT_ADDED` - 添加评论

**动作类型**:
- `ADD_TAG` / `REMOVE_TAG` - 标签操作
- `SET_CATEGORY` / `REMOVE_CATEGORY` - 分类操作
- `MOVE_TO_FOLDER` / `COPY_TO_FOLDER` - 文件夹操作
- `SET_METADATA` / `REMOVE_METADATA` - 元数据操作
- `RENAME` - 重命名 (支持正则)
- `SEND_NOTIFICATION` - 发送通知
- `WEBHOOK` - 调用外部 API
- `SET_STATUS` / `LOCK_DOCUMENT` - 状态操作

**API Endpoints**:
- `POST /api/v1/rules` - 创建规则
- `GET /api/v1/rules` - 列出规则
- `PUT /api/v1/rules/{id}` - 更新规则
- `DELETE /api/v1/rules/{id}` - 删除规则
- `POST /api/v1/rules/{id}/test` - 测试规则
- `POST /api/v1/rules/validate` - 验证条件语法
- `GET /api/v1/rules/stats` - 统计信息
- `GET /api/v1/rules/templates` - 预定义模板
- `POST /api/v1/ml/classify` - 文本分类
- `POST /api/v1/ml/suggest-tags` - 标签建议
- `POST /api/v1/ml/train` - 模型训练

---

## Existing Core Features

### Document Management

| 组件 | 功能 |
|------|------|
| Node | 基础节点实体 (Document/Folder 父类) |
| Document | 文档实体 (扩展 Node) |
| Folder | 文件夹实体 (扩展 Node) |
| NodeService | 文档 CRUD、上传、下载、锁定 |
| NodeController | REST API - 文档操作 |

### Version Control

| 组件 | 功能 |
|------|------|
| Version | 版本实体 |
| VersionService | 版本创建、查询、回滚、提升 |
| VersionController | REST API - 版本操作 |

### Security & Permissions

| 组件 | 功能 |
|------|------|
| Permission | 权限实体 |
| Role | 角色实体 |
| Group | 用户组实体 |
| SecurityService | 权限检查、ACL 管理 |
| AlfrescoPermissionService | Alfresco 权限同步 |

### Taxonomy

| 组件 | 功能 |
|------|------|
| Tag | 标签实体 |
| Category | 分类实体 |
| TagService | 标签 CRUD、批量操作 |
| CategoryService | 分类 CRUD、层级管理 |
| CommentService | 评论管理 |

### Search & Indexing

| 组件 | 功能 |
|------|------|
| NodeDocument | Elasticsearch 文档模型 |
| SearchService | 全文搜索、高亮 |
| FacetedSearchService | 分面聚合搜索 |

### Content Processing

| 组件 | 功能 |
|------|------|
| ConversionService | 文档格式转换 |
| PreviewService | 缩略图/预览生成 |

---

## File Structure

```
ecm-core/src/main/java/com/ecm/core/
├── alfresco/
│   └── AlfrescoPermissionService.java
├── config/
│   └── (配置类)
├── controller/
│   ├── FolderController.java      ⭐ Sprint 4
│   ├── MLController.java          ⭐ Sprint 3
│   ├── NodeController.java
│   ├── RuleController.java        ⭐ Sprint 3
│   ├── SearchController.java
│   └── VersionController.java
├── conversion/
│   └── ConversionService.java
├── entity/
│   ├── AutomationRule.java        ⭐ Sprint 3
│   ├── BaseEntity.java
│   ├── Document.java
│   ├── Folder.java
│   ├── Group.java
│   ├── Node.java
│   ├── Permission.java
│   ├── Role.java
│   ├── RuleAction.java            ⭐ Sprint 3
│   ├── RuleCondition.java         ⭐ Sprint 3
│   ├── RuleExecutionResult.java   ⭐ Sprint 3
│   └── Version.java
├── event/
│   ├── EcmEventListener.java
│   ├── NodeCreatedEvent.java
│   ├── NodeUpdatedEvent.java
│   └── ... (其他事件)
├── exception/
│   └── (异常类)
├── ml/
│   └── MLServiceClient.java       ⭐ Sprint 3
├── model/
│   ├── Category.java
│   ├── Comment.java
│   └── Tag.java
├── preview/
│   └── PreviewService.java
├── repository/
│   ├── AutomationRuleRepository.java  ⭐ Sprint 3
│   ├── CategoryRepository.java
│   ├── CommentRepository.java
│   ├── FolderRepository.java
│   ├── GroupRepository.java
│   ├── NodeRepository.java
│   ├── PermissionRepository.java
│   ├── RoleRepository.java
│   ├── TagRepository.java
│   └── VersionRepository.java
├── search/
│   └── NodeDocument.java
└── service/
    ├── CategoryService.java
    ├── CommentService.java
    ├── FacetedSearchService.java  ⭐ Sprint 4
    ├── FolderService.java         ⭐ Sprint 4
    ├── NodeService.java
    ├── RuleEngineService.java     ⭐ Sprint 3
    ├── SecurityService.java
    ├── TagService.java
    └── VersionService.java
```

---

## Configuration

### application.yml

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/ecm
    username: ecm_user
    password: ${DB_PASSWORD}

  elasticsearch:
    uris: http://localhost:9200

  jpa:
    hibernate:
      ddl-auto: update
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect

minio:
  endpoint: http://localhost:9000
  access-key: ${MINIO_ACCESS_KEY}
  secret-key: ${MINIO_SECRET_KEY}
  bucket: ecm-documents

ecm:
  ml:
    service:
      url: http://ml-service:8080
    enabled: true
    timeout: 30000

  rules:
    max-rules-per-user: 100
    max-actions-per-rule: 10
```

---

## API Summary

### Document Operations
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/nodes/documents` | 上传文档 |
| GET | `/api/v1/nodes/{id}` | 获取节点 |
| PUT | `/api/v1/nodes/{id}` | 更新节点 |
| DELETE | `/api/v1/nodes/{id}` | 删除节点 |
| GET | `/api/v1/nodes/{id}/download` | 下载文件 |
| POST | `/api/v1/nodes/{id}/lock` | 锁定文档 |
| POST | `/api/v1/nodes/{id}/unlock` | 解锁文档 |

### Folder Operations
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/folders/tree` | 获取文件夹树 |
| GET | `/api/v1/folders/{id}/children` | 获取子节点 |
| GET | `/api/v1/folders/{id}/breadcrumb` | 获取面包屑 |
| POST | `/api/v1/folders` | 创建文件夹 |
| POST | `/api/v1/folders/{id}/move` | 移动文件夹 |
| POST | `/api/v1/folders/{id}/copy` | 复制文件夹 |

### Version Operations
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/versions/{documentId}` | 获取版本历史 |
| POST | `/api/v1/versions/{documentId}` | 创建新版本 |
| POST | `/api/v1/versions/{id}/promote` | 提升为主版本 |
| POST | `/api/v1/versions/{id}/revert` | 回滚到此版本 |
| DELETE | `/api/v1/versions/{id}` | 删除版本 |

### Search Operations
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/search?q=` | 全文搜索 |
| GET | `/api/v1/search/faceted` | 分面搜索 |

### Rule Operations
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/rules` | 创建规则 |
| GET | `/api/v1/rules` | 列出规则 |
| PUT | `/api/v1/rules/{id}` | 更新规则 |
| DELETE | `/api/v1/rules/{id}` | 删除规则 |
| POST | `/api/v1/rules/{id}/test` | 测试规则 |
| GET | `/api/v1/rules/templates` | 获取模板 |

### ML Operations
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/ml/health` | 健康检查 |
| POST | `/api/v1/ml/classify` | 文本分类 |
| POST | `/api/v1/ml/suggest-tags` | 标签建议 |
| POST | `/api/v1/ml/train` | 模型训练 |

---

## Technology Stack

| Layer | Technology |
|-------|------------|
| Framework | Spring Boot 3.x |
| Language | Java 17+ |
| Database | PostgreSQL 15+ (with JSONB) |
| Search | Elasticsearch 8.x |
| Storage | MinIO (S3-compatible) |
| Cache | Redis |
| Auth | Keycloak / Spring Security |
| API Docs | OpenAPI 3.0 / Swagger |
| Build | Maven |
| Container | Docker |

---

## Next Steps (Roadmap)

### Sprint 1: Core Enhancement ✅ COMPLETED
- [x] 增强文档元数据管理
- [x] 添加批量操作 API
- [x] 实现回收站功能

### Sprint 2: Workflow ✅ COMPLETED
- [x] 工作流引擎集成
- [x] 审批流程
- [x] 任务分配

---

### Sprint 5: Analytics ✅ COMPLETED
- [x] 使用统计
- [x] 存储报告
- [x] 活动日志

### Sprint 6: Integration ✅ COMPLETED
- [x] Office 365 集成 (基础链接)
- [x] Email 归档
- [x] 第三方 API 连接器 (Odoo)

---

## Documentation

| Document | Path | Description |
|----------|------|-------------|
| Sprint 3 详细文档 | `docs/SPRINT_3_RULE_ENGINE.md` | ML 服务与规则引擎详细设计 |
| Sprint 4 详细文档 | `docs/SPRINT_4_UX_ENHANCEMENT.md` | UX 增强功能详细设计 |
| 功能路线图 | `docs/ECM_FEATURE_ROADMAP.md` | 完整功能规划 |
| 贡献指南 | `AGENTS.md` | 开发者指南 |

---

## Quick Start

```bash
# 1. 启动依赖服务
docker-compose up -d postgres elasticsearch minio redis keycloak

# 2. 构建项目
cd ecm-core
mvn clean package -DskipTests

# 3. 运行服务
java -jar target/ecm-core.jar

# 4. 访问 API 文档
open http://localhost:8080/swagger-ui.html
```

---

*Last Updated: 2026-01-04*
