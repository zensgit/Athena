import api from './api';

export const RULE_UNEXPECTED_RESPONSE_MESSAGE =
  'Rule endpoint returned an unexpected response. Mocked CI gate may not cover it; backend route may be missing.';

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

const isObject = (value: unknown): value is Record<string, unknown> => (
  value !== null && typeof value === 'object' && !Array.isArray(value)
);

const isFiniteNumber = (value: unknown): value is number => (
  typeof value === 'number' && Number.isFinite(value)
);

const isStringOrNullish = (value: unknown): value is string | null | undefined => (
  value === null || value === undefined || typeof value === 'string'
);

const isBooleanOrNullish = (value: unknown): value is boolean | null | undefined => (
  value === null || value === undefined || typeof value === 'boolean'
);

const isNumberOrNullish = (value: unknown): value is number | null | undefined => (
  value === null || value === undefined || isFiniteNumber(value)
);

const isRecordOrNullish = (value: unknown): value is Record<string, any> | null | undefined => (
  value === null || value === undefined || isObject(value)
);

const assertUnexpectedResponse = (): never => {
  throw new Error(RULE_UNEXPECTED_RESPONSE_MESSAGE);
};

const assertObject = (value: unknown): Record<string, unknown> => (
  isObject(value) ? value : assertUnexpectedResponse()
);

const assertArray = <T>(value: unknown, itemGuard: (item: unknown) => item is T): T[] => {
  if (!Array.isArray(value) || !value.every(itemGuard)) {
    return assertUnexpectedResponse();
  }

  return value;
};

const assertPageResponse = <T>(
  value: unknown,
  itemGuard: (item: unknown) => item is T
): PageResponse<T> => {
  if (!isObject(value)
    || !Array.isArray(value.content)
    || !value.content.every(itemGuard)
    || !isFiniteNumber(value.totalElements)
    || !isFiniteNumber(value.totalPages)
    || !isFiniteNumber(value.number)
    || !isFiniteNumber(value.size)) {
    return assertUnexpectedResponse();
  }

  return value as unknown as PageResponse<T>;
};

const isRuleCondition = (value: unknown): value is RuleCondition => (
  isObject(value)
  && typeof value.type === 'string'
  && isStringOrNullish(value.field)
  && isStringOrNullish(value.operator)
  && (value.children === null || value.children === undefined || (
    Array.isArray(value.children) && value.children.every(isRuleCondition)
  ))
  && isBooleanOrNullish(value.ignoreCase)
);

const isRuleAction = (value: unknown): value is RuleAction => (
  isObject(value)
  && typeof value.type === 'string'
  && isRecordOrNullish(value.params)
  && isBooleanOrNullish(value.continueOnError)
  && isNumberOrNullish(value.order)
);

const isTriggerType = (value: unknown): value is TriggerType => (
  value === 'DOCUMENT_CREATED'
  || value === 'DOCUMENT_UPDATED'
  || value === 'DOCUMENT_TAGGED'
  || value === 'DOCUMENT_MOVED'
  || value === 'DOCUMENT_CATEGORIZED'
  || value === 'VERSION_CREATED'
  || value === 'COMMENT_ADDED'
  || value === 'SCHEDULED'
);

const isRuleResponse = (value: unknown): value is RuleResponse => (
  isObject(value)
  && typeof value.id === 'string'
  && typeof value.name === 'string'
  && isTriggerType(value.triggerType)
  && isRuleCondition(value.condition)
  && Array.isArray(value.actions)
  && value.actions.every(isRuleAction)
  && isStringOrNullish(value.description)
  && isNumberOrNullish(value.priority)
  && isBooleanOrNullish(value.enabled)
  && isStringOrNullish(value.owner)
  && isStringOrNullish(value.scopeFolderId)
  && isStringOrNullish(value.scopeMimeTypes)
  && isBooleanOrNullish(value.stopOnMatch)
  && isNumberOrNullish(value.executionCount)
  && isNumberOrNullish(value.failureCount)
  && isStringOrNullish(value.createdDate)
  && isStringOrNullish(value.createdBy)
  && isStringOrNullish(value.lastModifiedDate)
  && isStringOrNullish(value.lastModifiedBy)
  && isStringOrNullish(value.cronExpression)
  && isStringOrNullish(value.timezone)
  && isStringOrNullish(value.lastRunAt)
  && isStringOrNullish(value.nextRunAt)
  && isNumberOrNullish(value.maxItemsPerRun)
  && isNumberOrNullish(value.manualBackfillMinutes)
);

const assertRuleResponse = (value: unknown): RuleResponse => (
  isRuleResponse(value) ? value : assertUnexpectedResponse()
);

const isValidationResult = (value: unknown): value is ValidationResult => (
  isObject(value)
  && typeof value.valid === 'boolean'
  && typeof value.message === 'string'
  && isStringOrNullish(value.error)
);

const assertValidationResult = (value: unknown): ValidationResult => (
  isValidationResult(value) ? value : assertUnexpectedResponse()
);

const isRuleTestResult = (value: unknown): value is RuleTestResult => (
  isObject(value)
  && typeof value.ruleId === 'string'
  && typeof value.ruleName === 'string'
  && typeof value.matched === 'boolean'
  && typeof value.message === 'string'
  && isRecordOrNullish(value.testData)
);

const isRuleActionDefinition = (value: unknown): value is RuleActionDefinition => (
  isObject(value)
  && typeof value.type === 'string'
  && typeof value.supported === 'boolean'
  && Array.isArray(value.requiredParams)
  && value.requiredParams.every((entry) => typeof entry === 'string')
  && Array.isArray(value.optionalParams)
  && value.optionalParams.every((entry) => typeof entry === 'string')
  && Array.isArray(value.constraints)
  && value.constraints.every((entry) => typeof entry === 'string')
);

const isRuleTemplate = (value: unknown): value is RuleTemplate => (
  isObject(value)
  && typeof value.id === 'string'
  && typeof value.name === 'string'
  && typeof value.description === 'string'
  && isTriggerType(value.triggerType)
  && isRuleCondition(value.condition)
  && Array.isArray(value.actions)
  && value.actions.every(isRuleAction)
);

const isFolderRuleReorderResponse = (value: unknown): value is FolderRuleReorderResponse => (
  isObject(value)
  && typeof value.scopeFolderId === 'string'
  && isFiniteNumber(value.updated)
  && Array.isArray(value.rules)
  && value.rules.every(isRuleResponse)
);

const isFolderRuleDryRunItem = (value: unknown): value is FolderRuleDryRunItem => (
  isObject(value)
  && typeof value.ruleId === 'string'
  && typeof value.ruleName === 'string'
  && (value.priority === null || isFiniteNumber(value.priority))
  && typeof value.matched === 'boolean'
  && typeof value.processable === 'boolean'
  && isStringOrNullish(value.skipReason)
  && (value.unsupportedActions === null || value.unsupportedActions === undefined || (
    Array.isArray(value.unsupportedActions) && value.unsupportedActions.every((entry) => typeof entry === 'string')
  ))
  && isStringOrNullish(value.error)
);

const isFolderRuleDryRunResult = (value: unknown): value is FolderRuleDryRunResult => (
  isObject(value)
  && typeof value.scopeFolderId === 'string'
  && isTriggerType(value.triggerType)
  && isFiniteNumber(value.found)
  && isFiniteNumber(value.scanned)
  && isFiniteNumber(value.matched)
  && isFiniteNumber(value.processable)
  && isFiniteNumber(value.skipped)
  && isFiniteNumber(value.errors)
  && (value.skipReasons === null || value.skipReasons === undefined || isObject(value.skipReasons))
  && Array.isArray(value.results)
  && value.results.every(isFolderRuleDryRunItem)
);

const isRuleRunActionRecord = (value: unknown): value is RuleRunActionRecord => (
  isObject(value)
  && typeof value.success === 'boolean'
  && isStringOrNullish(value.actionType)
  && isStringOrNullish(value.errorMessage)
  && isNumberOrNullish(value.durationMs)
  && isStringOrNullish(value.details)
);

const isRuleRunRecord = (value: unknown): value is RuleRunRecord => (
  isObject(value)
  && typeof value.runId === 'string'
  && typeof value.ruleId === 'string'
  && typeof value.ruleName === 'string'
  && typeof value.documentId === 'string'
  && typeof value.documentName === 'string'
  && isTriggerType(value.triggerType)
  && isStringOrNullish(value.idempotencyKey)
  && typeof value.conditionMatched === 'boolean'
  && typeof value.success === 'boolean'
  && isFiniteNumber(value.successfulActions)
  && isFiniteNumber(value.failedActions)
  && isFiniteNumber(value.totalActions)
  && isStringOrNullish(value.errorMessage)
  && isStringOrNullish(value.startedAt)
  && isStringOrNullish(value.completedAt)
  && isNumberOrNullish(value.durationMs)
  && Array.isArray(value.actions)
  && value.actions.every(isRuleRunActionRecord)
);

const isRuleExecutionCommandResponse = (value: unknown): value is RuleExecutionCommandResponse => (
  isObject(value)
  && typeof value.runId === 'string'
  && typeof value.deduplicated === 'boolean'
  && isStringOrNullish(value.deduplicatedFromRunId)
  && isRuleRunRecord(value.run)
);

const isRuleAuditTimelineItem = (value: unknown): value is RuleAuditTimelineItem => (
  isObject(value)
  && isStringOrNullish(value.eventTime)
  && isStringOrNullish(value.eventType)
  && isStringOrNullish(value.username)
  && isStringOrNullish(value.actor)
  && isStringOrNullish(value.nodeId)
  && isStringOrNullish(value.nodeName)
  && isStringOrNullish(value.details)
);

const isCronValidationResult = (value: unknown): value is CronValidationResult => (
  isObject(value)
  && typeof value.valid === 'boolean'
  && (value.nextExecutions === null || value.nextExecutions === undefined || (
    Array.isArray(value.nextExecutions) && value.nextExecutions.every((entry) => typeof entry === 'string')
  ))
  && isStringOrNullish(value.error)
);

class RuleService {
  async getAllRules(page = 0, size = 100): Promise<RuleResponse[]> {
    const response = await api.get<unknown>('/rules', {
      params: { page, size, sort: 'priority,asc' },
    });
    return assertPageResponse(response, isRuleResponse).content;
  }

  async getMyRules(page = 0, size = 100): Promise<RuleResponse[]> {
    const response = await api.get<unknown>('/rules/my', {
      params: { page, size, sort: 'priority,asc' },
    });
    return assertPageResponse(response, isRuleResponse).content;
  }

  async searchRules(query: string, page = 0, size = 100): Promise<RuleResponse[]> {
    const response = await api.get<unknown>('/rules/search', {
      params: { q: query, page, size },
    });
    return assertPageResponse(response, isRuleResponse).content;
  }

  async getScopeFolderRules(folderId: string, page = 0, size = 100): Promise<RuleResponse[]> {
    const response = await api.get<unknown>(`/rules/folders/${folderId}`, {
      params: { page, size, sort: 'priority,asc' },
    });
    return assertPageResponse(response, isRuleResponse).content;
  }

  async getRule(ruleId: string): Promise<RuleResponse> {
    const response = await api.get<unknown>(`/rules/${ruleId}`);
    return assertRuleResponse(response);
  }

  async createRule(data: CreateRuleRequest): Promise<RuleResponse> {
    const response = await api.post<unknown>('/rules', data);
    return assertRuleResponse(response);
  }

  async updateRule(ruleId: string, data: UpdateRuleRequest): Promise<RuleResponse> {
    const response = await api.put<unknown>(`/rules/${ruleId}`, data);
    return assertRuleResponse(response);
  }

  async deleteRule(ruleId: string): Promise<void> {
    return api.delete<void>(`/rules/${ruleId}`);
  }

  async setEnabled(ruleId: string, enabled: boolean): Promise<RuleResponse> {
    const endpoint = enabled ? 'enable' : 'disable';
    const response = await api.patch<unknown>(`/rules/${ruleId}/${endpoint}`);
    return assertRuleResponse(response);
  }

  async reorderScopeFolderRules(
    folderId: string,
    data: FolderRuleReorderRequest
  ): Promise<FolderRuleReorderResponse> {
    const response = await api.post<unknown>(`/rules/folders/${folderId}/reorder`, data);
    return isFolderRuleReorderResponse(response) ? response : assertUnexpectedResponse();
  }

  async dryRunScopeFolderRules(
    folderId: string,
    data: FolderRuleDryRunRequest
  ): Promise<FolderRuleDryRunResult> {
    const response = await api.post<unknown>(`/rules/folders/${folderId}/dry-run`, data);
    return isFolderRuleDryRunResult(response) ? response : assertUnexpectedResponse();
  }

  async executeRuleManually(
    ruleId: string,
    data: RuleExecutionCommandRequest
  ): Promise<RuleExecutionCommandResponse> {
    const response = await api.post<unknown>(`/rules/${ruleId}/execute`, data);
    return isRuleExecutionCommandResponse(response) ? response : assertUnexpectedResponse();
  }

  async listRuleExecutions(ruleId?: string, limit = 20): Promise<RuleRunRecord[]> {
    const response = await api.get<unknown>('/rules/executions', {
      params: {
        ruleId: ruleId || undefined,
        limit,
      },
    });
    return assertArray(response, isRuleRunRecord);
  }

  async listRuleExecutionTimeline(filters?: RuleExecutionTimelineFilters): Promise<RuleRunRecord[]> {
    const response = await api.get<unknown>('/rules/executions/timeline', {
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
    return assertArray(response, isRuleRunRecord);
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
    const response = await api.get<unknown>('/rules/executions/audit', {
      params: {
        eventType: filters?.eventType || undefined,
        actor: filters?.actor || undefined,
        nodeId: filters?.nodeId || undefined,
        from: filters?.from || undefined,
        to: filters?.to || undefined,
        limit: filters?.limit ?? undefined,
      },
    });
    return assertArray(response, isRuleAuditTimelineItem);
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
    const response = await api.get<unknown>(`/rules/executions/${runId}`);
    return isRuleRunRecord(response) ? response : assertUnexpectedResponse();
  }

  async testRule(ruleId: string, testData: Record<string, any>): Promise<RuleTestResult> {
    const response = await api.post<unknown>(`/rules/${ruleId}/test`, { testData });
    return isRuleTestResult(response) ? response : assertUnexpectedResponse();
  }

  async validateCondition(condition: RuleCondition): Promise<ValidationResult> {
    const response = await api.post<unknown>('/rules/validate', condition);
    return assertValidationResult(response);
  }

  async getTemplates(): Promise<RuleTemplate[]> {
    const response = await api.get<unknown>('/rules/templates');
    return assertArray(response, isRuleTemplate);
  }

  async getActionDefinitions(): Promise<RuleActionDefinition[]> {
    const response = await api.get<unknown>('/rules/actions/definitions');
    if (!isObject(response) || !Array.isArray(response.actions) || !response.actions.every(isRuleActionDefinition)) {
      return assertUnexpectedResponse();
    }
    return response.actions;
  }

  async getStats(): Promise<Record<string, any>> {
    const response = await api.get<unknown>('/rules/stats');
    return assertObject(response);
  }

  async getRuleStats(ruleId: string): Promise<Record<string, any>> {
    const response = await api.get<unknown>(`/rules/${ruleId}/stats`);
    return assertObject(response);
  }

  async validateCronExpression(
    cronExpression: string,
    timezone?: string
  ): Promise<CronValidationResult> {
    const response = await api.post<unknown>('/rules/validate-cron', {
      cronExpression,
      timezone: timezone || 'UTC',
    });
    return isCronValidationResult(response) ? response : assertUnexpectedResponse();
  }

  async triggerScheduledRule(ruleId: string): Promise<void> {
    return api.post<void>(`/rules/${ruleId}/trigger`);
  }
}

const ruleService = new RuleService();
export default ruleService;
