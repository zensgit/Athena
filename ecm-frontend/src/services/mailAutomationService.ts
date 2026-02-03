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
  oauthConnected?: boolean | null;
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
  enabled?: boolean | null;
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
  enabled?: boolean | null;
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

export interface MailFetchSummaryStatus {
  summary: MailFetchSummary | null;
  fetchedAt?: string | null;
}

export interface MailFetchDebugFolderResult {
  folder: string;
  rules: number;
  foundMessages: number;
  scannedMessages: number;
  matchedMessages: number;
  processableMessages: number;
  skippedMessages: number;
  errorMessages: number;
  skipReasons: Record<string, number>;
}

export interface MailFetchDebugAccountResult {
  accountId: string;
  accountName: string;
  attempted: boolean;
  skipReason?: string | null;
  accountError?: string | null;
  rules: number;
  folders: number;
  foundMessages: number;
  scannedMessages: number;
  matchedMessages: number;
  processableMessages: number;
  skippedMessages: number;
  errorMessages: number;
  skipReasons: Record<string, number>;
  ruleMatches: Record<string, number>;
  folderResults: MailFetchDebugFolderResult[];
}

export interface MailFetchDebugResult {
  summary: MailFetchSummary;
  maxMessagesPerFolder: number;
  skipReasons: Record<string, number>;
  accounts: MailFetchDebugAccountResult[];
}

export interface MailRulePreviewMessage {
  folder: string;
  uid: string;
  subject?: string | null;
  from?: string | null;
  recipients?: string | null;
  receivedAt?: string | null;
  attachmentCount: number;
  processable: boolean;
}

export interface MailRulePreviewResult {
  accountId: string;
  accountName: string;
  ruleId: string;
  ruleName: string;
  maxMessagesPerFolder: number;
  foundMessages: number;
  scannedMessages: number;
  matchedMessages: number;
  processableMessages: number;
  skippedMessages: number;
  errorMessages: number;
  skipReasons: Record<string, number>;
  matches: MailRulePreviewMessage[];
}

export interface ProcessedMailDiagnosticItem {
  id: string;
  processedAt: string;
  status: 'PROCESSED' | 'ERROR' | string;
  accountId?: string | null;
  accountName?: string | null;
  ruleId?: string | null;
  ruleName?: string | null;
  folder: string;
  uid: string;
  subject?: string | null;
  errorMessage?: string | null;
}

export interface MailDocumentDiagnosticItem {
  documentId: string;
  name: string;
  path: string;
  createdDate: string;
  createdBy: string;
  mimeType?: string | null;
  fileSize?: number | null;
  accountId?: string | null;
  accountName?: string | null;
  ruleId?: string | null;
  ruleName?: string | null;
  folder?: string | null;
  uid?: string | null;
}

export interface MailDiagnosticsResult {
  limit: number;
  recentProcessed: ProcessedMailDiagnosticItem[];
  recentDocuments: MailDocumentDiagnosticItem[];
}

export interface MailDiagnosticsFilters {
  accountId?: string | null;
  ruleId?: string | null;
  status?: 'PROCESSED' | 'ERROR' | string | null;
  subject?: string | null;
  processedFrom?: string | null;
  processedTo?: string | null;
}

export interface MailDiagnosticsExportOptions {
  includeProcessed?: boolean;
  includeDocuments?: boolean;
  includeSubject?: boolean;
  includeError?: boolean;
  includePath?: boolean;
  includeMimeType?: boolean;
  includeFileSize?: boolean;
}

export interface ProcessedMailRetentionStatus {
  retentionDays: number;
  enabled: boolean;
  expiredCount: number;
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

  async updateRule(ruleId: string, data: Partial<MailRuleRequest>): Promise<MailRule> {
    return api.put<MailRule>(`/integration/mail/rules/${ruleId}`, data);
  }

  async deleteRule(ruleId: string): Promise<void> {
    return api.delete(`/integration/mail/rules/${ruleId}`);
  }

  async testConnection(accountId: string): Promise<MailConnectionTestResult> {
    return api.post<MailConnectionTestResult>(`/integration/mail/accounts/${accountId}/test`);
  }

  async getOAuthAuthorizeUrl(accountId: string, redirectUrl?: string): Promise<{ url: string; state?: string }> {
    return api.get<{ url: string; state?: string }>('/integration/mail/oauth/authorize', {
      params: {
        accountId,
        redirectUrl,
      },
    });
  }

  async listFolders(accountId: string): Promise<string[]> {
    return api.get<string[]>(`/integration/mail/accounts/${accountId}/folders`);
  }

  async getDiagnostics(limit = 25, filters?: MailDiagnosticsFilters): Promise<MailDiagnosticsResult> {
    return api.get<MailDiagnosticsResult>('/integration/mail/diagnostics', {
      params: {
        limit,
        accountId: filters?.accountId || undefined,
        ruleId: filters?.ruleId || undefined,
        status: filters?.status || undefined,
        subject: filters?.subject || undefined,
        processedFrom: filters?.processedFrom || undefined,
        processedTo: filters?.processedTo || undefined,
      },
    });
  }

  async exportDiagnosticsCsv(
    limit = 25,
    filters?: MailDiagnosticsFilters,
    options?: MailDiagnosticsExportOptions,
  ): Promise<Blob> {
    return api.getBlob('/integration/mail/diagnostics/export', {
      params: {
        limit,
        accountId: filters?.accountId || undefined,
        ruleId: filters?.ruleId || undefined,
        status: filters?.status || undefined,
        subject: filters?.subject || undefined,
        processedFrom: filters?.processedFrom || undefined,
        processedTo: filters?.processedTo || undefined,
        includeProcessed: options?.includeProcessed ?? undefined,
        includeDocuments: options?.includeDocuments ?? undefined,
        includeSubject: options?.includeSubject ?? undefined,
        includeError: options?.includeError ?? undefined,
        includePath: options?.includePath ?? undefined,
        includeMimeType: options?.includeMimeType ?? undefined,
        includeFileSize: options?.includeFileSize ?? undefined,
      },
    });
  }

  async bulkDeleteProcessedMail(ids: string[]): Promise<{ deleted: number }> {
    return api.post<{ deleted: number }>('/integration/mail/processed/bulk-delete', { ids });
  }

  async getProcessedRetention(): Promise<ProcessedMailRetentionStatus> {
    return api.get<ProcessedMailRetentionStatus>('/integration/mail/processed/retention');
  }

  async cleanupProcessedRetention(): Promise<{ deleted: number }> {
    return api.post<{ deleted: number }>('/integration/mail/processed/cleanup');
  }

  async triggerFetch(): Promise<MailFetchSummary> {
    return api.post<MailFetchSummary>('/integration/mail/fetch');
  }

  async getFetchSummary(): Promise<MailFetchSummaryStatus> {
    return api.get<MailFetchSummaryStatus>('/integration/mail/fetch/summary');
  }

  async triggerFetchDebug(options?: {
    force?: boolean;
    maxMessagesPerFolder?: number;
  }): Promise<MailFetchDebugResult> {
    const force = options?.force ?? true;
    const maxMessagesPerFolder = options?.maxMessagesPerFolder;
    return api.post<MailFetchDebugResult>('/integration/mail/fetch/debug', undefined, {
      params: {
        force,
        maxMessagesPerFolder,
      },
    });
  }

  async previewRule(
    ruleId: string,
    accountId: string,
    maxMessagesPerFolder?: number,
  ): Promise<MailRulePreviewResult> {
    return api.post<MailRulePreviewResult>(`/integration/mail/rules/${ruleId}/preview`, {
      accountId,
      maxMessagesPerFolder,
    });
  }
}

const mailAutomationService = new MailAutomationService();
export default mailAutomationService;
