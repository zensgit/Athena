# Sprint 3: ML Service & Rule Engine

## Overview

Sprint 3 implements an intelligent automation system for the ECM platform, consisting of:

1. **Rule Engine** - Event-driven automation rules with configurable conditions and actions
2. **ML Service Integration** - HTTP-based integration with an external FastAPI ML microservice for document classification and tag suggestions

## Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                         ECM Core (Java/Spring)                       │
├─────────────────────────────────────────────────────────────────────┤
│                                                                       │
│  ┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐  │
│  │  RuleController │    │  MLController   │    │  Event Listeners │  │
│  └────────┬────────┘    └────────┬────────┘    └────────┬────────┘  │
│           │                      │                      │            │
│           ▼                      ▼                      ▼            │
│  ┌─────────────────┐    ┌─────────────────┐                         │
│  │RuleEngineService│    │ MLServiceClient │                         │
│  └────────┬────────┘    └────────┬────────┘                         │
│           │                      │                                   │
│           ▼                      │ HTTP                              │
│  ┌─────────────────┐            │                                   │
│  │AutomationRule   │            │                                   │
│  │Repository       │            │                                   │
│  └────────┬────────┘            │                                   │
│           │                      │                                   │
└───────────┼──────────────────────┼───────────────────────────────────┘
            │                      │
            ▼                      ▼
   ┌─────────────────┐    ┌─────────────────┐
   │   PostgreSQL    │    │   ML Service    │
   │   (JSONB)       │    │   (FastAPI)     │
   └─────────────────┘    └─────────────────┘
```

## Components

### 1. Entity Models

#### AutomationRule (`entity/AutomationRule.java`)

Main entity for storing automation rules.

```java
@Entity
@Table(name = "automation_rules")
public class AutomationRule extends BaseEntity {
    private String name;
    private String description;

    @Enumerated(EnumType.STRING)
    private TriggerType triggerType;

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private RuleCondition condition;

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private List<RuleAction> actions;

    private Integer priority = 100;      // Lower = higher priority
    private Boolean enabled = true;
    private String owner;
    private UUID scopeFolderId;          // Limit rule to specific folder
    private String scopeMimeTypes;       // Limit to specific MIME types
    private Long executionCount = 0L;
    private Long failureCount = 0L;
    private Boolean stopOnMatch = false; // Stop processing other rules
}
```

**Trigger Types:**
- `DOCUMENT_CREATED` - New document uploaded
- `DOCUMENT_UPDATED` - Document metadata changed
- `DOCUMENT_TAGGED` - Tag added to document
- `DOCUMENT_MOVED` - Document moved to different folder
- `DOCUMENT_CATEGORIZED` - Category assigned
- `VERSION_CREATED` - New version uploaded
- `COMMENT_ADDED` - Comment added to document
- `SCHEDULED` - Time-based trigger

#### RuleCondition (`entity/RuleCondition.java`)

Supports nested condition trees for complex logic.

```java
public class RuleCondition implements Serializable {
    private ConditionType type;  // SIMPLE, AND, OR, NOT, ALWAYS_TRUE, ALWAYS_FALSE
    private String field;        // Field to evaluate
    private String operator;     // Comparison operator
    private Object value;        // Value to compare
    private List<RuleCondition> children;  // For compound conditions
    private Boolean ignoreCase = true;
}
```

**Supported Fields:**
- `name` - Document name
- `description` - Document description
- `mimeType` - MIME type (e.g., `application/pdf`)
- `size` - File size in bytes
- `path` - Full path
- `parentFolderId` - Parent folder UUID
- `content` - Extracted text content
- `createdBy` - Creator username
- `tags` - Tag names
- `categories` - Category names
- Custom metadata: `metadata.fieldName`

**Supported Operators:**
| Operator | Description | Example |
|----------|-------------|---------|
| `equals` | Exact match | `name equals "report.pdf"` |
| `notEquals` | Not equal | `mimeType notEquals "text/plain"` |
| `contains` | Contains substring | `name contains "invoice"` |
| `startsWith` | Starts with | `path startsWith "/Finance"` |
| `endsWith` | Ends with | `name endsWith ".pdf"` |
| `regex` | Regex match | `name regex "INV-\\d{4}"` |
| `gt`, `gte` | Greater than | `size gt 10485760` |
| `lt`, `lte` | Less than | `size lt 1024` |
| `in` | In list | `mimeType in ["application/pdf", "image/png"]` |
| `notIn` | Not in list | `tags notIn ["draft", "temp"]` |
| `isNull` | Is null | `description isNull true` |
| `isEmpty` | Is empty | `tags isEmpty true` |

#### RuleAction (`entity/RuleAction.java`)

Defines actions to execute when conditions match.

```java
public class RuleAction implements Serializable {
    private ActionType type;
    private Map<String, Object> params;
    private Boolean continueOnError = true;
    private Integer order = 0;  // Execution order
}
```

**Action Types:**

| Type | Parameters | Description |
|------|------------|-------------|
| `ADD_TAG` | `tagName` | Add a tag to document |
| `REMOVE_TAG` | `tagName` | Remove a tag |
| `SET_CATEGORY` | `categoryName` | Assign category |
| `REMOVE_CATEGORY` | `categoryName` | Remove category |
| `MOVE_TO_FOLDER` | `folderId` | Move document |
| `COPY_TO_FOLDER` | `folderId` | Copy document |
| `SET_METADATA` | `key`, `value` | Set metadata field |
| `REMOVE_METADATA` | `key` | Remove metadata field |
| `RENAME` | `pattern`, `replacement` | Rename using regex |
| `START_WORKFLOW` | `workflowId`, `params` | Trigger workflow |
| `SEND_NOTIFICATION` | `recipient`, `message` | Send notification |
| `WEBHOOK` | `url`, `method`, `payload` | Call external API |
| `SET_STATUS` | `status` | Set document status |
| `LOCK_DOCUMENT` | (none) | Lock document |
| `EXECUTE_SCRIPT` | `script` | Execute custom script |

### 2. RuleEngineService (`service/RuleEngineService.java`)

Core service handling rule CRUD operations and execution.

**Key Methods:**

```java
// CRUD Operations
AutomationRule createRule(CreateRuleRequest request);
AutomationRule updateRule(UUID ruleId, UpdateRuleRequest request);
void deleteRule(UUID ruleId);
AutomationRule setRuleEnabled(UUID ruleId, boolean enabled);

// Rule Execution
List<RuleExecutionResult> evaluateAndExecute(Document document, TriggerType triggerType);

// Condition Evaluation
boolean evaluateCondition(RuleCondition condition, Document document);

// Query Operations
Page<AutomationRule> getAllRules(Pageable pageable);
Page<AutomationRule> getRulesByOwner(String owner, Pageable pageable);
Page<AutomationRule> searchRules(String query, Pageable pageable);
```

**Execution Flow:**

```
Document Event
      │
      ▼
┌─────────────────┐
│ Find matching   │
│ enabled rules   │
│ by trigger type │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ Sort by priority│
│ (lower = first) │
└────────┬────────┘
         │
         ▼
    ┌────────┐
    │For each│
    │  rule  │
    └───┬────┘
        │
        ▼
┌─────────────────┐
│ Check scope     │
│ (folder, MIME)  │
└────────┬────────┘
         │ matches
         ▼
┌─────────────────┐
│ Evaluate        │
│ condition tree  │
└────────┬────────┘
         │ true
         ▼
┌─────────────────┐
│ Execute actions │
│ in order        │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ Update stats    │
│ (count/failures)│
└────────┬────────┘
         │
         ▼
    stopOnMatch?
    ├── true ──▶ Stop
    └── false ─▶ Next rule
```

### 3. RuleController (`controller/RuleController.java`)

REST API for rule management.

**Endpoints:**

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/rules` | Create rule |
| `GET` | `/api/v1/rules/{id}` | Get rule by ID |
| `GET` | `/api/v1/rules` | List all rules (paginated) |
| `GET` | `/api/v1/rules/my` | Get current user's rules |
| `GET` | `/api/v1/rules/search?q=` | Search rules |
| `PUT` | `/api/v1/rules/{id}` | Update rule |
| `DELETE` | `/api/v1/rules/{id}` | Delete rule |
| `PATCH` | `/api/v1/rules/{id}/enable` | Enable rule |
| `PATCH` | `/api/v1/rules/{id}/disable` | Disable rule |
| `POST` | `/api/v1/rules/{id}/test` | Test rule against sample data |
| `POST` | `/api/v1/rules/validate` | Validate condition syntax |
| `GET` | `/api/v1/rules/stats` | Get overall statistics |
| `GET` | `/api/v1/rules/{id}/stats` | Get rule execution stats |
| `GET` | `/api/v1/rules/templates` | Get predefined templates |

### 4. MLServiceClient (`ml/MLServiceClient.java`)

HTTP client for the external ML microservice.

**Configuration:**

```yaml
ecm:
  ml:
    service:
      url: http://ml-service:8080   # ML service URL
    enabled: true                    # Enable/disable ML features
    timeout: 30000                   # Request timeout (ms)
```

**Key Methods:**

```java
// Health & Status
boolean isAvailable();
boolean isModelLoaded();
ModelInfo getModelInfo();

// Classification
ClassificationResult classify(String text);
ClassificationResult classifyWithCandidates(String text, List<String> candidates);

// Tag Suggestions
List<String> suggestTags(String text, int maxTags);

// Model Training
TrainingResult trainModel(List<TrainingDocument> documents);
```

**ClassificationResult:**
```java
public class ClassificationResult {
    private String suggestedCategory;
    private Double confidence;           // 0.0 - 1.0
    private List<AlternativeCategory> alternatives;
    private boolean success;
    private String errorMessage;

    public boolean isHighConfidence() {
        return confidence != null && confidence >= 0.85;
    }
}
```

### 5. MLController (`controller/MLController.java`)

REST API for ML features.

**Endpoints:**

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/v1/ml/health` | Check ML service health |
| `POST` | `/api/v1/ml/classify` | Classify text content |
| `POST` | `/api/v1/ml/classify/{documentId}` | Classify document by ID |
| `POST` | `/api/v1/ml/classify-batch` | Batch classify documents |
| `POST` | `/api/v1/ml/suggest-tags` | Get tag suggestions |
| `GET` | `/api/v1/ml/suggest-tags/{documentId}` | Suggest tags for document |
| `POST` | `/api/v1/ml/train` | Train model (admin only) |
| `POST` | `/api/v1/ml/train/from-documents` | Train from existing docs (admin) |

## API Examples

### Create an Automation Rule

```http
POST /api/v1/rules
Content-Type: application/json

{
  "name": "Auto-tag PDF Invoices",
  "description": "Automatically tag PDF documents containing 'invoice' as finance documents",
  "triggerType": "DOCUMENT_CREATED",
  "condition": {
    "type": "AND",
    "children": [
      {
        "type": "SIMPLE",
        "field": "mimeType",
        "operator": "equals",
        "value": "application/pdf"
      },
      {
        "type": "SIMPLE",
        "field": "name",
        "operator": "contains",
        "value": "invoice",
        "ignoreCase": true
      }
    ]
  },
  "actions": [
    {
      "type": "ADD_TAG",
      "params": { "tagName": "invoice" },
      "order": 0
    },
    {
      "type": "SET_CATEGORY",
      "params": { "categoryName": "Finance" },
      "order": 1
    },
    {
      "type": "MOVE_TO_FOLDER",
      "params": { "folderId": "550e8400-e29b-41d4-a716-446655440000" },
      "order": 2
    }
  ],
  "priority": 50,
  "enabled": true,
  "stopOnMatch": false
}
```

### Test a Rule

```http
POST /api/v1/rules/123e4567-e89b-12d3-a456-426614174000/test
Content-Type: application/json

{
  "testData": {
    "name": "Q4-2024-invoice.pdf",
    "mimeType": "application/pdf",
    "size": 524288
  }
}
```

Response:
```json
{
  "ruleId": "123e4567-e89b-12d3-a456-426614174000",
  "ruleName": "Auto-tag PDF Invoices",
  "matched": true,
  "message": "Condition matched",
  "testData": {
    "name": "Q4-2024-invoice.pdf",
    "mimeType": "application/pdf",
    "size": 524288
  }
}
```

### Classify Text with ML

```http
POST /api/v1/ml/classify
Content-Type: application/json

{
  "text": "This quarterly financial report shows revenue growth of 15% compared to the previous quarter...",
  "candidates": ["Finance", "Marketing", "Engineering", "HR", "Legal"]
}
```

Response:
```json
{
  "suggestedCategory": "Finance",
  "confidence": 0.92,
  "alternatives": [
    { "category": "Marketing", "confidence": 0.05 },
    { "category": "Legal", "confidence": 0.02 }
  ],
  "success": true,
  "errorMessage": null
}
```

### Suggest Tags for Document

```http
GET /api/v1/ml/suggest-tags/550e8400-e29b-41d4-a716-446655440000?maxTags=5
```

Response:
```json
["financial-report", "quarterly", "revenue", "growth", "q4-2024"]
```

### Get Rule Statistics

```http
GET /api/v1/rules/stats
```

Response:
```json
{
  "totalRules": 25,
  "enabledRules": 20,
  "disabledRules": 5,
  "totalExecutions": 15432,
  "totalFailures": 23,
  "successRate": 99.85,
  "byTriggerType": {
    "DOCUMENT_CREATED": 12,
    "DOCUMENT_UPDATED": 5,
    "DOCUMENT_MOVED": 4,
    "DOCUMENT_TAGGED": 2,
    "VERSION_CREATED": 2
  }
}
```

## Predefined Rule Templates

The system includes ready-to-use templates:

| Template | Trigger | Condition | Action |
|----------|---------|-----------|--------|
| Auto-tag PDF | DOCUMENT_CREATED | mimeType = application/pdf | Add "pdf" tag |
| Auto-categorize Invoices | DOCUMENT_CREATED | name contains "invoice" | Set category "Finance" |
| Large File Notification | DOCUMENT_CREATED | size > 100MB | Send notification to admin |
| Archive Old Docs | DOCUMENT_MOVED | path starts with "/Archive" | Set status ARCHIVED |

## Database Schema

```sql
CREATE TABLE automation_rules (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    trigger_type VARCHAR(50) NOT NULL,
    condition JSONB,
    actions JSONB,
    priority INTEGER DEFAULT 100,
    enabled BOOLEAN DEFAULT true,
    owner VARCHAR(255),
    scope_folder_id UUID,
    scope_mime_types VARCHAR(500),
    execution_count BIGINT DEFAULT 0,
    failure_count BIGINT DEFAULT 0,
    stop_on_match BOOLEAN DEFAULT false,
    created_date TIMESTAMP,
    created_by VARCHAR(255),
    last_modified_date TIMESTAMP,
    last_modified_by VARCHAR(255)
);

CREATE INDEX idx_automation_rules_trigger_enabled
    ON automation_rules(trigger_type, enabled);
CREATE INDEX idx_automation_rules_owner
    ON automation_rules(owner);
CREATE INDEX idx_automation_rules_priority
    ON automation_rules(priority);
```

## Configuration

```yaml
# application.yml
ecm:
  rules:
    max-rules-per-user: 100
    max-actions-per-rule: 10
    max-condition-depth: 5
    default-priority: 100

  ml:
    service:
      url: ${ML_SERVICE_URL:http://ml-service:8080}
    enabled: ${ML_ENABLED:true}
    timeout: ${ML_TIMEOUT:30000}
    min-text-length: 50
```

## Integration Points

### Event-Driven Execution

Rules are triggered by Spring application events:

```java
@EventListener
public void onDocumentCreated(NodeCreatedEvent event) {
    if (event.getNode() instanceof Document doc) {
        ruleEngineService.evaluateAndExecute(doc, TriggerType.DOCUMENT_CREATED);
    }
}
```

### ML-Assisted Auto-Classification

Combine rule engine with ML for intelligent automation:

```java
// In RuleEngineService
private void executeMLClassifyAction(Document document, RuleAction action) {
    String text = extractDocumentText(document);
    ClassificationResult result = mlServiceClient.classify(text);

    if (result.isSuccess() && result.isHighConfidence()) {
        categoryService.assignCategory(document.getId(), result.getSuggestedCategory());
    }
}
```

## Security

- Rule creation/modification requires `ADMIN` or `EDITOR` role
- Model training requires `ADMIN` role
- Rules can only be deleted by owner or admin
- Webhook actions validated against allowlist
- Script execution sandboxed (if enabled)

## Files Created

| File | Description |
|------|-------------|
| `entity/AutomationRule.java` | Rule entity with JSONB condition/actions |
| `entity/RuleCondition.java` | Condition model with nested support |
| `entity/RuleAction.java` | Action model with parameters |
| `entity/RuleExecutionResult.java` | Execution result tracking |
| `repository/AutomationRuleRepository.java` | JPA repository with custom queries |
| `service/RuleEngineService.java` | Core rule execution engine |
| `controller/RuleController.java` | REST API for rules |
| `ml/MLServiceClient.java` | HTTP client for ML service |
| `controller/MLController.java` | REST API for ML features |

## Next Steps

1. **ML Service Implementation** - Create the FastAPI Python service for actual ML classification
2. **Event Integration** - Wire up Spring events to trigger rule execution
3. **Admin UI** - Build rule management interface
4. **Monitoring** - Add detailed execution logging and metrics
5. **Scheduled Rules** - Implement cron-based rule triggers
