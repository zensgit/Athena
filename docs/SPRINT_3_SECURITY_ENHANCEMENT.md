# Sprint 3: 基础权限增强

## 目标
确保上传的文件只有创建者和被授权者能看到，实现企业级权限控制。

## 实现的功能

### 1. 动态权限系统 (Dynamic Authority)

借鉴 Alfresco 的 DynamicAuthority 模式，实现运行时动态评估权限。

#### 核心接口
```
com.ecm.core.security.DynamicAuthority
```

**设计特点：**
- 基于上下文 (PermissionContext) 评估权限
- 优先级机制（数值越小优先级越高）
- 可插拔的权限规则
- 支持返回三态结果：`true`（授权）、`false`（拒绝）、`null`（跳过，由下一个规则处理）

#### 已实现的动态权限

| 实现类 | 优先级 | 功能 | 权限范围 |
|--------|--------|------|----------|
| `OwnerDynamicAuthority` | 10 | 文档创建者拥有所有权限 | ALL |
| `LockOwnerDynamicAuthority` | 20 | 锁定者可修改/删除锁定文档 | WRITE, DELETE |
| `SameDepartmentDynamicAuthority` | 50 | 同部门用户可读取 | READ（可配置启用） |

#### 权限上下文
```java
PermissionContext {
    nodeId: UUID           // 节点ID
    node: Node             // 节点实体
    username: String       // 请求用户
    requestedPermission: PermissionType  // 请求的权限
    attributes: Map<String, Object>      // 额外属性（IP、时间等）
}
```

### 2. SecurityService 增强

**改进点：**
- 动态权限优先检查
- 按优先级排序的权限规则链
- 详细的权限检查日志

**权限检查流程：**
```
1. Admin 检查 → 如果是管理员，直接通过
2. 动态权限检查 → 按优先级遍历所有 DynamicAuthority
   - 如果返回 true/false，立即返回结果
   - 如果返回 null，继续下一个规则
3. ACL 检查 → 回退到传统的 ACL 权限检查
```

### 3. 分享链接功能 (Share Link)

允许用户创建安全的文档分享链接，支持多种保护机制。

#### ShareLink 实体
```
com.ecm.core.entity.ShareLink
```

**功能特性：**
- 唯一 Token（Base64 URL 安全编码）
- 密码保护（BCrypt 加密）
- 过期时间限制
- 访问次数限制
- IP 白名单限制
- 三种权限级别：VIEW、COMMENT、EDIT

#### ShareLinkService 功能
- 创建分享链接
- 访问验证（密码、过期、次数、IP）
- 更新/停用/删除链接
- 定时清理过期链接

#### REST API
```
POST   /api/share/nodes/{nodeId}     创建分享链接
GET    /api/share/access/{token}      访问分享链接（公开）
GET    /api/share/{token}             获取链接信息
GET    /api/share/nodes/{nodeId}      获取节点的所有分享链接
GET    /api/share/my                   获取当前用户的分享链接
PUT    /api/share/{token}             更新分享链接
POST   /api/share/{token}/deactivate  停用链接
DELETE /api/share/{token}             删除链接
```

### 4. 回收站功能 (Trash/Recycle Bin)

实现软删除和恢复机制，防止误删。

#### TrashService 功能
- 移动到回收站（软删除）
- 从回收站恢复
- 永久删除
- 清空回收站
- 自动清理过期项目

#### 配置选项
```yaml
ecm:
  trash:
    retention-days: 30       # 保留天数
    auto-purge-enabled: true # 自动清理
```

#### REST API
```
POST   /api/trash/nodes/{nodeId}      移动到回收站
POST   /api/trash/{nodeId}/restore    从回收站恢复
DELETE /api/trash/{nodeId}            永久删除
GET    /api/trash                      获取回收站内容
GET    /api/trash/user/{username}     获取用户的回收站
DELETE /api/trash/empty               清空回收站
GET    /api/trash/stats               获取回收站统计
GET    /api/trash/nearing-purge       获取即将过期的项目
```

## 新增文件清单

### 安全模块
```
src/main/java/com/ecm/core/security/
├── DynamicAuthority.java           # 动态权限接口
├── PermissionContext.java          # 权限检查上下文
├── OwnerDynamicAuthority.java      # 所有者权限实现
├── LockOwnerDynamicAuthority.java  # 锁定者权限实现
└── SameDepartmentDynamicAuthority.java  # 同部门权限实现
```

### 分享链接模块
```
src/main/java/com/ecm/core/entity/
└── ShareLink.java                  # 分享链接实体

src/main/java/com/ecm/core/repository/
└── ShareLinkRepository.java        # 分享链接仓库

src/main/java/com/ecm/core/service/
└── ShareLinkService.java           # 分享链接服务

src/main/java/com/ecm/core/controller/
└── ShareLinkController.java        # 分享链接控制器
```

### 回收站模块
```
src/main/java/com/ecm/core/service/
└── TrashService.java               # 回收站服务

src/main/java/com/ecm/core/controller/
└── TrashController.java            # 回收站控制器
```

## 修改的文件

| 文件 | 修改内容 |
|------|----------|
| `BaseEntity.java` | 添加 deletedAt, deletedBy 字段 |
| `Node.java` | 添加 isFolder(), getSize() 方法 |
| `Document.java` | 覆盖 getSize() 方法返回 fileSize |
| `NodeRepository.java` | 添加回收站相关查询方法 |
| `SecurityService.java` | 集成动态权限检查 |

## 数据库变更

### 新增表
```sql
-- share_links 表
CREATE TABLE share_links (
    id UUID PRIMARY KEY,
    token VARCHAR(64) UNIQUE NOT NULL,
    node_id UUID NOT NULL REFERENCES nodes(id),
    created_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    expiry_date TIMESTAMP,
    password_hash VARCHAR(255),
    max_access_count INTEGER,
    access_count INTEGER DEFAULT 0,
    active BOOLEAN DEFAULT TRUE,
    name VARCHAR(255),
    permission_level VARCHAR(20) NOT NULL,
    last_accessed_at TIMESTAMP,
    allowed_ips VARCHAR(500)
);

-- 索引
CREATE INDEX idx_share_link_token ON share_links(token);
CREATE INDEX idx_share_link_node ON share_links(node_id);
CREATE INDEX idx_share_link_created_by ON share_links(created_by);
CREATE INDEX idx_share_link_expiry ON share_links(expiry_date);
```

### 表结构变更
```sql
-- 添加软删除相关字段到基础表
ALTER TABLE nodes ADD COLUMN deleted_at TIMESTAMP;
ALTER TABLE nodes ADD COLUMN deleted_by VARCHAR(255);
```

## 配置示例

```yaml
ecm:
  security:
    same-department-access:
      enabled: false  # 是否启用同部门访问权限

  trash:
    retention-days: 30
    auto-purge-enabled: true
```

## 架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                      Permission Check Flow                       │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│   Request → [Admin?] → [DynamicAuthority Chain] → [ACL Check]   │
│               ↓              ↓                        ↓          │
│             Grant    Owner(10) → LockOwner(20) → Dept(50)       │
│                              ↓                        ↓          │
│                        Grant/Deny/Skip         Grant/Deny/Skip   │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                    Share Link Access Flow                        │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│   Access → [Valid?] → [Password?] → [IP Check] → [Record] → OK  │
│              ↓           ↓             ↓                         │
│           Expired     Verify       Whitelist                     │
│           Inactive    Match        Check                         │
│           Limit                                                  │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                      Trash Lifecycle                             │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│   [Active] ─delete→ [Trash] ─restore→ [Active]                  │
│                        │                                         │
│                    permanent delete                              │
│                        │                                         │
│                        ↓                                         │
│                    [Deleted]                                     │
│                                                                  │
│   Auto-purge: Trash items older than retention-days             │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

## 下一步计划 (Sprint 4)

根据 ECM_FEATURE_ROADMAP.md，下一阶段将实现：
- 文件夹结构管理
- 批量上传支持
- 文档复制/移动操作
