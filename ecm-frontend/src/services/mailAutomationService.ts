import api from './api';

export type MailSecurityType = 'NONE' | 'SSL' | 'STARTTLS';
export type MailActionType = 'ATTACHMENTS_ONLY' | 'METADATA_ONLY' | 'EVERYTHING';

export interface MailAccount {
  id: string;
  name: string;
  host: string;
  port: number;
  username: string;
  security: MailSecurityType;
  enabled: boolean;
  pollIntervalMinutes: number;
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
}

export interface MailRule {
  id: string;
  name: string;
  accountId?: string | null;
  priority: number;
  subjectFilter?: string | null;
  fromFilter?: string | null;
  bodyFilter?: string | null;
  actionType: MailActionType;
  assignTagId?: string | null;
  assignFolderId?: string | null;
}

export interface MailRuleRequest {
  name: string;
  accountId?: string | null;
  priority?: number;
  subjectFilter?: string | null;
  fromFilter?: string | null;
  bodyFilter?: string | null;
  actionType?: MailActionType;
  assignTagId?: string | null;
  assignFolderId?: string | null;
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

  async triggerFetch(): Promise<void> {
    return api.post('/integration/mail/fetch');
  }
}

const mailAutomationService = new MailAutomationService();
export default mailAutomationService;
