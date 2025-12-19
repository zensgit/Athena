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

  async testRule(ruleId: string, testData: Record<string, any>): Promise<RuleTestResult> {
    return api.post<RuleTestResult>(`/rules/${ruleId}/test`, { testData });
  }

  async validateCondition(condition: RuleCondition): Promise<ValidationResult> {
    return api.post<ValidationResult>('/rules/validate', condition);
  }

  async getTemplates(): Promise<RuleTemplate[]> {
    return api.get<RuleTemplate[]>('/rules/templates');
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

