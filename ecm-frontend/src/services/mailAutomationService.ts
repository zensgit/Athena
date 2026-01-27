import api from './api';

export type MailSecurityType = 'NONE' | 'SSL' | 'STARTTLS' | 'OAUTH2';
export type MailOAuthProvider = 'GOOGLE' | 'MICROSOFT' | 'CUSTOM';
export type MailActionType = 'ATTACHMENTS_ONLY' | 'METADATA_ONLY' | 'EVERYTHING';
export type MailPostAction = 'NONE' | 'MARK_READ' | 'DELETE' | 'MOVE' | 'FLAG' | 'TAG';

export interface MailAccount {
  id: string;
  name: string;
  host: string;
  port: number;
  username: string;
  security: MailSecurityType;
  enabled: boolean;
  pollIntervalMinutes: number;
  oauthProvider?: MailOAuthProvider | null;
  oauthTokenEndpoint?: string | null;
  oauthTenantId?: string | null;
  oauthScope?: string | null;
  oauthCredentialKey?: string | null;
  oauthEnvConfigured?: boolean;
  oauthMissingEnvKeys?: string[];
  lastFetchAt?: string | null;
  lastFetchStatus?: string | null;
  lastFetchError?: string | null;
}

export interface MailAccountRequest {
  name: string;
  host: string;
  port: number;
  username: string;
  password?: string;
  security?: MailSecurityType;
  enabled?: boolean;
  pollIntervalMinutes?: number;
  oauthProvider?: MailOAuthProvider | null;
  oauthTokenEndpoint?: string;
  oauthTenantId?: string;
  oauthScope?: string;
  oauthCredentialKey?: string;
}

export interface MailRule {
  id: string;
  name: string;
  accountId?: string | null;
  priority: number;
  folder?: string | null;
  subjectFilter?: string | null;
  fromFilter?: string | null;
  toFilter?: string | null;
  bodyFilter?: string | null;
  attachmentFilenameInclude?: string | null;
  attachmentFilenameExclude?: string | null;
  maxAgeDays?: number | null;
  includeInlineAttachments?: boolean | null;
  actionType: MailActionType;
  mailAction?: MailPostAction | null;
  mailActionParam?: string | null;
  assignTagId?: string | null;
  assignFolderId?: string | null;
}

export interface MailRuleRequest {
  name: string;
  accountId?: string | null;
  priority?: number;
  folder?: string | null;
  subjectFilter?: string | null;
  fromFilter?: string | null;
  toFilter?: string | null;
  bodyFilter?: string | null;
  attachmentFilenameInclude?: string | null;
  attachmentFilenameExclude?: string | null;
  maxAgeDays?: number | null;
  includeInlineAttachments?: boolean | null;
  actionType?: MailActionType;
  mailAction?: MailPostAction | null;
  mailActionParam?: string | null;
  assignTagId?: string | null;
  assignFolderId?: string | null;
}

export interface MailConnectionTestResult {
  success: boolean;
  message: string;
  durationMs: number;
}

export interface MailFetchSummary {
  accounts: number;
  attemptedAccounts: number;
  skippedAccounts: number;
  accountErrors: number;
  foundMessages: number;
  matchedMessages: number;
  processedMessages: number;
  skippedMessages: number;
  errorMessages: number;
  durationMs: number;
}

class MailAutomationService {
  async listAccounts(): Promise<MailAccount[]> {
    return api.get<MailAccount[]>('/integration/mail/accounts');
  }

  async createAccount(data: MailAccountRequest): Promise<MailAccount> {
    return api.post<MailAccount>('/integration/mail/accounts', data);
  }

  async updateAccount(accountId: string, data: MailAccountRequest): Promise<MailAccount> {
    return api.put<MailAccount>(`/integration/mail/accounts/${accountId}`, data);
  }

  async deleteAccount(accountId: string): Promise<void> {
    return api.delete(`/integration/mail/accounts/${accountId}`);
  }

  async listRules(): Promise<MailRule[]> {
    return api.get<MailRule[]>('/integration/mail/rules');
  }

  async createRule(data: MailRuleRequest): Promise<MailRule> {
    return api.post<MailRule>('/integration/mail/rules', data);
  }

  async updateRule(ruleId: string, data: MailRuleRequest): Promise<MailRule> {
    return api.put<MailRule>(`/integration/mail/rules/${ruleId}`, data);
  }

  async deleteRule(ruleId: string): Promise<void> {
    return api.delete(`/integration/mail/rules/${ruleId}`);
  }

  async testConnection(accountId: string): Promise<MailConnectionTestResult> {
    return api.post<MailConnectionTestResult>(`/integration/mail/accounts/${accountId}/test`);
  }

  async triggerFetch(): Promise<MailFetchSummary> {
    return api.post<MailFetchSummary>('/integration/mail/fetch');
  }
}

const mailAutomationService = new MailAutomationService();
export default mailAutomationService;
