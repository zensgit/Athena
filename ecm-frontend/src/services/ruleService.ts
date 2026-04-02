import api from './api';

export type TriggerType =
  | 'DOCUMENT_CREATED'
  | 'DOCUMENT_UPDATED'
  | 'DOCUMENT_TAGGED'
  | 'DOCUMENT_MOVED'
  | 'DOCUMENT_CATEGORIZED'
  | 'VERSION_CREATED'
  | 'COMMENT_ADDED'
  | 'SCHEDULED';

export interface RuleCondition {
  type: string;
  field?: string;
  operator?: string;
  value?: any;
  children?: RuleCondition[];
  ignoreCase?: boolean;
}

export interface RuleAction {
  type: string;
  params?: Record<string, any>;
  continueOnError?: boolean;
  order?: number;
}

export interface RuleActionDefinition {
  type: string;
  supported: boolean;
  requiredParams: string[];
  optionalParams: string[];
  constraints: string[];
}

interface RuleActionDefinitionsResponse {
  actions: RuleActionDefinition[];
}

export interface RuleTemplate {
  id: string;
  name: string;
  description: string;
  triggerType: TriggerType;
  condition: RuleCondition;
  actions: RuleAction[];
}

export interface RuleResponse {
  id: string;
  name: string;
  description?: string;
  triggerType: TriggerType;
  condition: RuleCondition;
  actions: RuleAction[];
  priority?: number;
  enabled?: boolean;
  owner?: string;
  scopeFolderId?: string | null;
  scopeMimeTypes?: string;
  stopOnMatch?: boolean;
  executionCount?: number;
  failureCount?: number;
  createdDate?: string;
  createdBy?: string;
  lastModifiedDate?: string;
  lastModifiedBy?: string;
  // Scheduled rule fields
  cronExpression?: string;
  timezone?: string;
  lastRunAt?: string;
  nextRunAt?: string;
  maxItemsPerRun?: number;
  manualBackfillMinutes?: number;
}

export interface ValidationResult {
  valid: boolean;
  message: string;
  error?: string | null;
}

export interface RuleTestResult {
  ruleId: string;
  ruleName: string;
  matched: boolean;
  message: string;
  testData?: Record<string, any>;
}

export interface FolderRuleReorderRequest {
  ruleIds: string[];
  basePriority?: number;
  step?: number;
}

export interface FolderRuleReorderResponse {
  scopeFolderId: string;
  updated: number;
  rules: RuleResponse[];
}

export interface FolderRuleDryRunRequest {
  triggerType?: TriggerType;
  testData?: Record<string, any>;
  limit?: number;
}

export interface FolderRuleDryRunItem {
  ruleId: string;
  ruleName: string;
  priority: number | null;
  matched: boolean;
  processable: boolean;
  skipReason?: string | null;
  unsupportedActions?: string[];
  error?: string | null;
}

export interface FolderRuleDryRunResult {
  scopeFolderId: string;
  triggerType: TriggerType;
  found: number;
  scanned: number;
  matched: number;
  processable: number;
  skipped: number;
  errors: number;
  skipReasons?: Record<string, number>;
  results: FolderRuleDryRunItem[];
}

export interface RuleExecutionCommandRequest {
  documentId: string;
  triggerType?: TriggerType;
  idempotencyKey?: string;
}

export interface RuleRunActionRecord {
  actionType?: string | null;
  success: boolean;
  errorMessage?: string | null;
  durationMs?: number | null;
  details?: string | null;
}

export interface RuleRunRecord {
  runId: string;
  ruleId: string;
  ruleName: string;
  documentId: string;
  documentName: string;
  triggerType: TriggerType;
  idempotencyKey?: string | null;
  conditionMatched: boolean;
  success: boolean;
  successfulActions: number;
  failedActions: number;
  totalActions: number;
  errorMessage?: string | null;
  startedAt?: string | null;
  completedAt?: string | null;
  durationMs?: number | null;
  actions: RuleRunActionRecord[];
}

export interface RuleExecutionTimelineFilters {
  ruleId?: string;
  documentId?: string;
  triggerType?: TriggerType;
  success?: boolean;
  from?: string;
  to?: string;
  actor?: string;
  limit?: number;
}

export interface RuleAuditTimelineFilters {
  eventType?: string;
  actor?: string;
  nodeId?: string;
  from?: string;
  to?: string;
  limit?: number;
}

export interface RuleAuditTimelineItem {
  eventTime?: string | null;
  eventType?: string | null;
  username?: string | null;
  actor?: string | null;
  nodeId?: string | null;
  nodeName?: string | null;
  details?: string | null;
}

export interface RuleExecutionCommandResponse {
  runId: string;
  deduplicated: boolean;
  deduplicatedFromRunId?: string | null;
  run: RuleRunRecord;
}

export interface CreateRuleRequest {
  name: string;
  description?: string;
  triggerType: TriggerType;
  condition: RuleCondition;
  actions: RuleAction[];
  priority?: number;
  enabled?: boolean;
  scopeFolderId?: string | null;
  scopeMimeTypes?: string;
  stopOnMatch?: boolean;
  // Scheduled rule fields
  cronExpression?: string;
  timezone?: string;
  maxItemsPerRun?: number;
  manualBackfillMinutes?: number;
}

export interface CronValidationResult {
  valid: boolean;
  nextExecutions?: string[];
  error?: string;
}

export type UpdateRuleRequest = CreateRuleRequest;

interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

class RuleService {
  async getAllRules(page = 0, size = 100): Promise<RuleResponse[]> {
    const response = await api.get<PageResponse<RuleResponse>>('/rules', {
      params: { page, size, sort: 'priority,asc' },
    });
    return response.content || [];
  }

  async getMyRules(page = 0, size = 100): Promise<RuleResponse[]> {
    const response = await api.get<PageResponse<RuleResponse>>('/rules/my', {
      params: { page, size, sort: 'priority,asc' },
    });
    return response.content || [];
  }

  async searchRules(query: string, page = 0, size = 100): Promise<RuleResponse[]> {
    const response = await api.get<PageResponse<RuleResponse>>('/rules/search', {
      params: { q: query, page, size },
    });
    return response.content || [];
  }

  async getScopeFolderRules(folderId: string, page = 0, size = 100): Promise<RuleResponse[]> {
    const response = await api.get<PageResponse<RuleResponse>>(`/rules/folders/${folderId}`, {
      params: { page, size, sort: 'priority,asc' },
    });
    return response.content || [];
  }

  async getRule(ruleId: string): Promise<RuleResponse> {
    return api.get<RuleResponse>(`/rules/${ruleId}`);
  }

  async createRule(data: CreateRuleRequest): Promise<RuleResponse> {
    return api.post<RuleResponse>('/rules', data);
  }

  async updateRule(ruleId: string, data: UpdateRuleRequest): Promise<RuleResponse> {
    return api.put<RuleResponse>(`/rules/${ruleId}`, data);
  }

  async deleteRule(ruleId: string): Promise<void> {
    return api.delete<void>(`/rules/${ruleId}`);
  }

  async setEnabled(ruleId: string, enabled: boolean): Promise<RuleResponse> {
    const endpoint = enabled ? 'enable' : 'disable';
    return api.patch<RuleResponse>(`/rules/${ruleId}/${endpoint}`);
  }

  async reorderScopeFolderRules(
    folderId: string,
    data: FolderRuleReorderRequest
  ): Promise<FolderRuleReorderResponse> {
    return api.post<FolderRuleReorderResponse>(`/rules/folders/${folderId}/reorder`, data);
  }

  async dryRunScopeFolderRules(
    folderId: string,
    data: FolderRuleDryRunRequest
  ): Promise<FolderRuleDryRunResult> {
    return api.post<FolderRuleDryRunResult>(`/rules/folders/${folderId}/dry-run`, data);
  }

  async executeRuleManually(
    ruleId: string,
    data: RuleExecutionCommandRequest
  ): Promise<RuleExecutionCommandResponse> {
    return api.post<RuleExecutionCommandResponse>(`/rules/${ruleId}/execute`, data);
  }

  async listRuleExecutions(ruleId?: string, limit = 20): Promise<RuleRunRecord[]> {
    return api.get<RuleRunRecord[]>('/rules/executions', {
      params: {
        ruleId: ruleId || undefined,
        limit,
      },
    });
  }

  async listRuleExecutionTimeline(filters?: RuleExecutionTimelineFilters): Promise<RuleRunRecord[]> {
    return api.get<RuleRunRecord[]>('/rules/executions/timeline', {
      params: {
        ruleId: filters?.ruleId || undefined,
        documentId: filters?.documentId || undefined,
        triggerType: filters?.triggerType || undefined,
        success: filters?.success ?? undefined,
        from: filters?.from || undefined,
        to: filters?.to || undefined,
        actor: filters?.actor || undefined,
        limit: filters?.limit ?? undefined,
      },
    });
  }

  async exportRuleExecutionTimelineCsv(filters?: RuleExecutionTimelineFilters): Promise<void> {
    const filename = `rule_execution_timeline_${new Date().toISOString().replace(/[:.]/g, '-')}.csv`;
    return api.downloadFile('/rules/executions/timeline/export', filename, {
      params: {
        ruleId: filters?.ruleId || undefined,
        documentId: filters?.documentId || undefined,
        triggerType: filters?.triggerType || undefined,
        success: filters?.success ?? undefined,
        from: filters?.from || undefined,
        to: filters?.to || undefined,
        actor: filters?.actor || undefined,
        limit: filters?.limit ?? undefined,
      },
    });
  }

  async listRuleAuditTimeline(filters?: RuleAuditTimelineFilters): Promise<RuleAuditTimelineItem[]> {
    return api.get<RuleAuditTimelineItem[]>('/rules/executions/audit', {
      params: {
        eventType: filters?.eventType || undefined,
        actor: filters?.actor || undefined,
        nodeId: filters?.nodeId || undefined,
        from: filters?.from || undefined,
        to: filters?.to || undefined,
        limit: filters?.limit ?? undefined,
      },
    });
  }

  async exportRuleAuditTimelineCsv(filters?: RuleAuditTimelineFilters): Promise<void> {
    const filename = `rule_audit_timeline_${new Date().toISOString().replace(/[:.]/g, '-')}.csv`;
    return api.downloadFile('/rules/executions/audit/export', filename, {
      params: {
        eventType: filters?.eventType || undefined,
        actor: filters?.actor || undefined,
        nodeId: filters?.nodeId || undefined,
        from: filters?.from || undefined,
        to: filters?.to || undefined,
        limit: filters?.limit ?? undefined,
      },
    });
  }

  async getRuleExecution(runId: string): Promise<RuleRunRecord> {
    return api.get<RuleRunRecord>(`/rules/executions/${runId}`);
  }

  async testRule(ruleId: string, testData: Record<string, any>): Promise<RuleTestResult> {
    return api.post<RuleTestResult>(`/rules/${ruleId}/test`, { testData });
  }

  async validateCondition(condition: RuleCondition): Promise<ValidationResult> {
    return api.post<ValidationResult>('/rules/validate', condition);
  }

  async getTemplates(): Promise<RuleTemplate[]> {
    return api.get<RuleTemplate[]>('/rules/templates');
  }

  async getActionDefinitions(): Promise<RuleActionDefinition[]> {
    const response = await api.get<RuleActionDefinitionsResponse>('/rules/actions/definitions');
    return response.actions || [];
  }

  async getStats(): Promise<Record<string, any>> {
    return api.get<Record<string, any>>('/rules/stats');
  }

  async getRuleStats(ruleId: string): Promise<Record<string, any>> {
    return api.get<Record<string, any>>(`/rules/${ruleId}/stats`);
  }

  async validateCronExpression(
    cronExpression: string,
    timezone?: string
  ): Promise<CronValidationResult> {
    return api.post<CronValidationResult>('/rules/validate-cron', {
      cronExpression,
      timezone: timezone || 'UTC',
    });
  }

  async triggerScheduledRule(ruleId: string): Promise<void> {
    return api.post<void>(`/rules/${ruleId}/trigger`);
  }
}

const ruleService = new RuleService();
export default ruleService;
