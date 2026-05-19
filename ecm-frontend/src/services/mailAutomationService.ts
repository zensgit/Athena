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
  passwordConfigured?: boolean;
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
  runId?: string | null;
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
  runId?: string | null;
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

export interface MailReportTotals {
  processed: number;
  errors: number;
  total: number;
}

export interface MailReportAccountRow {
  accountId: string;
  accountName?: string | null;
  processed: number;
  errors: number;
  total: number;
  lastProcessedAt?: string | null;
  lastErrorAt?: string | null;
}

export interface MailReportRuleRow {
  ruleId: string;
  ruleName?: string | null;
  accountId?: string | null;
  accountName?: string | null;
  processed: number;
  errors: number;
  total: number;
  lastProcessedAt?: string | null;
  lastErrorAt?: string | null;
}

export interface MailReportTrendRow {
  date: string;
  processed: number;
  errors: number;
  total: number;
}

export interface MailReportResponse {
  accountId?: string | null;
  ruleId?: string | null;
  startDate: string;
  endDate: string;
  days: number;
  totals: MailReportTotals;
  accounts: MailReportAccountRow[];
  rules: MailReportRuleRow[];
  trend: MailReportTrendRow[];
}

export interface MailReportScheduledExportResult {
  attempted: boolean;
  success: boolean;
  status: string;
  message: string;
  manual: boolean;
  filename?: string | null;
  folderId?: string | null;
  documentId?: string | null;
  startedAt?: string | null;
  finishedAt?: string | null;
  durationMs?: number | null;
  days?: number | null;
}

export interface MailReportScheduleStatus {
  enabled: boolean;
  cron?: string | null;
  folderId?: string | null;
  days: number;
  accountId?: string | null;
  ruleId?: string | null;
  lastExport?: MailReportScheduledExportResult | null;
}

export interface MailReportFilters {
  accountId?: string | null;
  ruleId?: string | null;
  from?: string | null;
  to?: string | null;
  days?: number | null;
}

export interface MailDiagnosticsFilters {
  accountId?: string | null;
  ruleId?: string | null;
  status?: 'PROCESSED' | 'ERROR' | string | null;
  subject?: string | null;
  errorContains?: string | null;
  processedFrom?: string | null;
  processedTo?: string | null;
  sort?: MailDiagnosticsSortField | string | null;
  order?: MailDiagnosticsSortOrder | string | null;
}

export type MailDiagnosticsSortField = 'processedAt' | 'status' | 'rule' | 'account';
export type MailDiagnosticsSortOrder = 'asc' | 'desc';

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

export interface MailReplayResult {
  processedMailId: string;
  attempted: boolean;
  processed: boolean;
  message: string;
  replayStatus?: string | null;
}

export interface MailRuntimeErrorStat {
  errorMessage: string;
  count: number;
  lastSeenAt?: string | null;
}

export interface MailRuntimeTrend {
  direction: 'IMPROVING' | 'STABLE' | 'WORSENING' | string;
  currentTotal: number;
  previousTotal: number;
  deltaTotal: number;
  currentErrorRate: number;
  previousErrorRate: number;
  deltaErrorRate: number;
  summary?: string | null;
}

export interface MailRuntimeMetrics {
  windowMinutes: number;
  attempts: number;
  successes: number;
  errors: number;
  errorRate: number;
  avgDurationMs?: number | null;
  lastSuccessAt?: string | null;
  lastErrorAt?: string | null;
  status: 'HEALTHY' | 'DEGRADED' | 'DOWN' | 'UNKNOWN' | string;
  topErrors?: MailRuntimeErrorStat[];
  trend?: MailRuntimeTrend | null;
}

export type MailProviderPresetId =
  | 'ALIYUN_QIYE'
  | 'TENCENT_EXMAIL'
  | 'TENCENT_EXMAIL_OVERSEAS'
  | 'MAIL_263'
  | 'MAIL_263_OVERSEAS';

export type MailTransportSecurity = 'SSL' | 'STARTTLS' | 'NONE';

export interface MailProviderPreset {
  id: MailProviderPresetId;
  label: string;
  imapHost: string;
  imapPort: number;
  imapSecurity: MailTransportSecurity;
  smtpHost: string;
  smtpPort: number;
  smtpSecurity: MailTransportSecurity;
}

export interface EmailTestSmtpRequest {
  to: string;
}

export interface EmailTestSmtpResponse {
  ok: boolean;
  message: string;
  smtpHost: string | null;
  smtpPort: number | null;
  fromAddress: string | null;
  diagnostic: string | null;
}

export const MAIL_AUTOMATION_UNEXPECTED_RESPONSE_MESSAGE =
  'Mail automation endpoint returned an unexpected response. Mocked CI gate may not cover it; backend route may be missing or returned an HTML fallback.';

// Phase 5 Mocked harness can serve SPA index.html with HTTP 200 for unmocked
// routes. A naive consumer would crash on `response.ok`. Surface a
// recognizable synthetic error so the dialog's error path renders a sensible
// operator-facing message instead. See feedback_phase5_mocked_html_fallback.md.
export const TEST_SMTP_UNEXPECTED_RESPONSE_MESSAGE =
  'Test SMTP endpoint returned an unexpected response. Mocked CI gate may not cover it; runtime configuration may be missing.';

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

const isStringArray = (value: unknown): value is string[] => (
  Array.isArray(value) && value.every((entry) => typeof entry === 'string')
);

const isNumberRecord = (value: unknown): value is Record<string, number> => {
  if (!isObject(value)) {
    return false;
  }
  return Object.values(value).every((entry) => isFiniteNumber(entry));
};

const failUnexpectedResponse = (): never => {
  throw new Error(MAIL_AUTOMATION_UNEXPECTED_RESPONSE_MESSAGE);
};

const assertMailResponse = <T>(value: unknown, guard: (input: unknown) => input is T): T => (
  guard(value) ? value : failUnexpectedResponse()
);

const assertMailArray = <T>(value: unknown, guard: (input: unknown) => input is T): T[] => {
  if (!Array.isArray(value) || !value.every(guard)) {
    return failUnexpectedResponse();
  }
  return value;
};

const assertMailStringArray = (value: unknown): string[] => (
  isStringArray(value) ? value : failUnexpectedResponse()
);

const isMailAccount = (value: unknown): value is MailAccount => (
  isObject(value)
  && typeof value.id === 'string'
  && typeof value.name === 'string'
  && typeof value.host === 'string'
  && isFiniteNumber(value.port)
  && typeof value.username === 'string'
  && typeof value.security === 'string'
  && typeof value.enabled === 'boolean'
  && isFiniteNumber(value.pollIntervalMinutes)
  && isStringOrNullish(value.oauthProvider)
  && isStringOrNullish(value.oauthTokenEndpoint)
  && isStringOrNullish(value.oauthTenantId)
  && isStringOrNullish(value.oauthScope)
  && isStringOrNullish(value.oauthCredentialKey)
  && isBooleanOrNullish(value.passwordConfigured)
  && isBooleanOrNullish(value.oauthEnvConfigured)
  && (value.oauthMissingEnvKeys === undefined || isStringArray(value.oauthMissingEnvKeys))
  && isBooleanOrNullish(value.oauthConnected)
  && isStringOrNullish(value.lastFetchAt)
  && isStringOrNullish(value.lastFetchStatus)
  && isStringOrNullish(value.lastFetchError)
);

const isMailRule = (value: unknown): value is MailRule => (
  isObject(value)
  && typeof value.id === 'string'
  && typeof value.name === 'string'
  && isStringOrNullish(value.accountId)
  && isFiniteNumber(value.priority)
  && isBooleanOrNullish(value.enabled)
  && isStringOrNullish(value.folder)
  && isStringOrNullish(value.subjectFilter)
  && isStringOrNullish(value.fromFilter)
  && isStringOrNullish(value.toFilter)
  && isStringOrNullish(value.bodyFilter)
  && isStringOrNullish(value.attachmentFilenameInclude)
  && isStringOrNullish(value.attachmentFilenameExclude)
  && isNumberOrNullish(value.maxAgeDays)
  && isBooleanOrNullish(value.includeInlineAttachments)
  && typeof value.actionType === 'string'
  && isStringOrNullish(value.mailAction)
  && isStringOrNullish(value.mailActionParam)
  && isStringOrNullish(value.assignTagId)
  && isStringOrNullish(value.assignFolderId)
);

const isMailConnectionTestResult = (value: unknown): value is MailConnectionTestResult => (
  isObject(value)
  && typeof value.success === 'boolean'
  && typeof value.message === 'string'
  && isFiniteNumber(value.durationMs)
);

const isOAuthAuthorizeUrlResponse = (
  value: unknown,
): value is { url: string; state?: string } => (
  isObject(value)
  && typeof value.url === 'string'
  && (value.state === undefined || typeof value.state === 'string')
);

const isMailFetchSummary = (value: unknown): value is MailFetchSummary => (
  isObject(value)
  && isFiniteNumber(value.accounts)
  && isFiniteNumber(value.attemptedAccounts)
  && isFiniteNumber(value.skippedAccounts)
  && isFiniteNumber(value.accountErrors)
  && isFiniteNumber(value.foundMessages)
  && isFiniteNumber(value.matchedMessages)
  && isFiniteNumber(value.processedMessages)
  && isFiniteNumber(value.skippedMessages)
  && isFiniteNumber(value.errorMessages)
  && isFiniteNumber(value.durationMs)
  && isStringOrNullish(value.runId)
);

const isMailFetchSummaryStatus = (value: unknown): value is MailFetchSummaryStatus => (
  isObject(value)
  && (value.summary === null || isMailFetchSummary(value.summary))
  && isStringOrNullish(value.fetchedAt)
);

const isMailFetchDebugFolderResult = (value: unknown): value is MailFetchDebugFolderResult => (
  isObject(value)
  && typeof value.folder === 'string'
  && isFiniteNumber(value.rules)
  && isFiniteNumber(value.foundMessages)
  && isFiniteNumber(value.scannedMessages)
  && isFiniteNumber(value.matchedMessages)
  && isFiniteNumber(value.processableMessages)
  && isFiniteNumber(value.skippedMessages)
  && isFiniteNumber(value.errorMessages)
  && isNumberRecord(value.skipReasons)
);

const isMailFetchDebugAccountResult = (value: unknown): value is MailFetchDebugAccountResult => (
  isObject(value)
  && typeof value.accountId === 'string'
  && typeof value.accountName === 'string'
  && typeof value.attempted === 'boolean'
  && isStringOrNullish(value.skipReason)
  && isStringOrNullish(value.accountError)
  && isFiniteNumber(value.rules)
  && isFiniteNumber(value.folders)
  && isFiniteNumber(value.foundMessages)
  && isFiniteNumber(value.scannedMessages)
  && isFiniteNumber(value.matchedMessages)
  && isFiniteNumber(value.processableMessages)
  && isFiniteNumber(value.skippedMessages)
  && isFiniteNumber(value.errorMessages)
  && isNumberRecord(value.skipReasons)
  && isNumberRecord(value.ruleMatches)
  && Array.isArray(value.folderResults)
  && value.folderResults.every(isMailFetchDebugFolderResult)
);

const isMailFetchDebugResult = (value: unknown): value is MailFetchDebugResult => (
  isObject(value)
  && isMailFetchSummary(value.summary)
  && isFiniteNumber(value.maxMessagesPerFolder)
  && isNumberRecord(value.skipReasons)
  && Array.isArray(value.accounts)
  && value.accounts.every(isMailFetchDebugAccountResult)
);

const isMailRulePreviewMessage = (value: unknown): value is MailRulePreviewMessage => (
  isObject(value)
  && typeof value.folder === 'string'
  && typeof value.uid === 'string'
  && isStringOrNullish(value.subject)
  && isStringOrNullish(value.from)
  && isStringOrNullish(value.recipients)
  && isStringOrNullish(value.receivedAt)
  && isFiniteNumber(value.attachmentCount)
  && typeof value.processable === 'boolean'
);

const isMailRulePreviewResult = (value: unknown): value is MailRulePreviewResult => (
  isObject(value)
  && typeof value.accountId === 'string'
  && typeof value.accountName === 'string'
  && typeof value.ruleId === 'string'
  && typeof value.ruleName === 'string'
  && isFiniteNumber(value.maxMessagesPerFolder)
  && isFiniteNumber(value.foundMessages)
  && isFiniteNumber(value.scannedMessages)
  && isFiniteNumber(value.matchedMessages)
  && isFiniteNumber(value.processableMessages)
  && isFiniteNumber(value.skippedMessages)
  && isFiniteNumber(value.errorMessages)
  && isNumberRecord(value.skipReasons)
  && Array.isArray(value.matches)
  && value.matches.every(isMailRulePreviewMessage)
  && isStringOrNullish(value.runId)
);

const isProcessedMailDiagnosticItem = (value: unknown): value is ProcessedMailDiagnosticItem => (
  isObject(value)
  && typeof value.id === 'string'
  && typeof value.processedAt === 'string'
  && typeof value.status === 'string'
  && isStringOrNullish(value.accountId)
  && isStringOrNullish(value.accountName)
  && isStringOrNullish(value.ruleId)
  && isStringOrNullish(value.ruleName)
  && typeof value.folder === 'string'
  && typeof value.uid === 'string'
  && isStringOrNullish(value.subject)
  && isStringOrNullish(value.errorMessage)
);

const isMailDocumentDiagnosticItem = (value: unknown): value is MailDocumentDiagnosticItem => (
  isObject(value)
  && typeof value.documentId === 'string'
  && typeof value.name === 'string'
  && typeof value.path === 'string'
  && typeof value.createdDate === 'string'
  && typeof value.createdBy === 'string'
  && isStringOrNullish(value.mimeType)
  && isNumberOrNullish(value.fileSize)
  && isStringOrNullish(value.accountId)
  && isStringOrNullish(value.accountName)
  && isStringOrNullish(value.ruleId)
  && isStringOrNullish(value.ruleName)
  && isStringOrNullish(value.folder)
  && isStringOrNullish(value.uid)
);

const isMailDiagnosticsResult = (value: unknown): value is MailDiagnosticsResult => (
  isObject(value)
  && isFiniteNumber(value.limit)
  && Array.isArray(value.recentProcessed)
  && value.recentProcessed.every(isProcessedMailDiagnosticItem)
  && Array.isArray(value.recentDocuments)
  && value.recentDocuments.every(isMailDocumentDiagnosticItem)
);

const isMailReportTotals = (value: unknown): value is MailReportTotals => (
  isObject(value)
  && isFiniteNumber(value.processed)
  && isFiniteNumber(value.errors)
  && isFiniteNumber(value.total)
);

const isMailReportAccountRow = (value: unknown): value is MailReportAccountRow => (
  isObject(value)
  && typeof value.accountId === 'string'
  && isStringOrNullish(value.accountName)
  && isFiniteNumber(value.processed)
  && isFiniteNumber(value.errors)
  && isFiniteNumber(value.total)
  && isStringOrNullish(value.lastProcessedAt)
  && isStringOrNullish(value.lastErrorAt)
);

const isMailReportRuleRow = (value: unknown): value is MailReportRuleRow => (
  isObject(value)
  && typeof value.ruleId === 'string'
  && isStringOrNullish(value.ruleName)
  && isStringOrNullish(value.accountId)
  && isStringOrNullish(value.accountName)
  && isFiniteNumber(value.processed)
  && isFiniteNumber(value.errors)
  && isFiniteNumber(value.total)
  && isStringOrNullish(value.lastProcessedAt)
  && isStringOrNullish(value.lastErrorAt)
);

const isMailReportTrendRow = (value: unknown): value is MailReportTrendRow => (
  isObject(value)
  && typeof value.date === 'string'
  && isFiniteNumber(value.processed)
  && isFiniteNumber(value.errors)
  && isFiniteNumber(value.total)
);

const isMailReportResponse = (value: unknown): value is MailReportResponse => (
  isObject(value)
  && isStringOrNullish(value.accountId)
  && isStringOrNullish(value.ruleId)
  && typeof value.startDate === 'string'
  && typeof value.endDate === 'string'
  && isFiniteNumber(value.days)
  && isMailReportTotals(value.totals)
  && Array.isArray(value.accounts)
  && value.accounts.every(isMailReportAccountRow)
  && Array.isArray(value.rules)
  && value.rules.every(isMailReportRuleRow)
  && Array.isArray(value.trend)
  && value.trend.every(isMailReportTrendRow)
);

const isMailReportScheduledExportResult = (
  value: unknown,
): value is MailReportScheduledExportResult => (
  isObject(value)
  && typeof value.attempted === 'boolean'
  && typeof value.success === 'boolean'
  && typeof value.status === 'string'
  && typeof value.message === 'string'
  && typeof value.manual === 'boolean'
  && isStringOrNullish(value.filename)
  && isStringOrNullish(value.folderId)
  && isStringOrNullish(value.documentId)
  && isStringOrNullish(value.startedAt)
  && isStringOrNullish(value.finishedAt)
  && isNumberOrNullish(value.durationMs)
  && isNumberOrNullish(value.days)
);

const isMailReportScheduleStatus = (value: unknown): value is MailReportScheduleStatus => (
  isObject(value)
  && typeof value.enabled === 'boolean'
  && isStringOrNullish(value.cron)
  && isStringOrNullish(value.folderId)
  && isFiniteNumber(value.days)
  && isStringOrNullish(value.accountId)
  && isStringOrNullish(value.ruleId)
  && (value.lastExport === null
    || value.lastExport === undefined
    || isMailReportScheduledExportResult(value.lastExport))
);

const isProcessedMailRetentionStatus = (value: unknown): value is ProcessedMailRetentionStatus => (
  isObject(value)
  && isFiniteNumber(value.retentionDays)
  && typeof value.enabled === 'boolean'
  && isFiniteNumber(value.expiredCount)
);

const isMailReplayResult = (value: unknown): value is MailReplayResult => (
  isObject(value)
  && typeof value.processedMailId === 'string'
  && typeof value.attempted === 'boolean'
  && typeof value.processed === 'boolean'
  && typeof value.message === 'string'
  && isStringOrNullish(value.replayStatus)
);

const isMailRuntimeErrorStat = (value: unknown): value is MailRuntimeErrorStat => (
  isObject(value)
  && typeof value.errorMessage === 'string'
  && isFiniteNumber(value.count)
  && isStringOrNullish(value.lastSeenAt)
);

const isMailRuntimeTrend = (value: unknown): value is MailRuntimeTrend => (
  isObject(value)
  && typeof value.direction === 'string'
  && isFiniteNumber(value.currentTotal)
  && isFiniteNumber(value.previousTotal)
  && isFiniteNumber(value.deltaTotal)
  && isFiniteNumber(value.currentErrorRate)
  && isFiniteNumber(value.previousErrorRate)
  && isFiniteNumber(value.deltaErrorRate)
  && isStringOrNullish(value.summary)
);

const isMailRuntimeMetrics = (value: unknown): value is MailRuntimeMetrics => (
  isObject(value)
  && isFiniteNumber(value.windowMinutes)
  && isFiniteNumber(value.attempts)
  && isFiniteNumber(value.successes)
  && isFiniteNumber(value.errors)
  && isFiniteNumber(value.errorRate)
  && isNumberOrNullish(value.avgDurationMs)
  && isStringOrNullish(value.lastSuccessAt)
  && isStringOrNullish(value.lastErrorAt)
  && typeof value.status === 'string'
  && (value.topErrors === undefined
    || (Array.isArray(value.topErrors) && value.topErrors.every(isMailRuntimeErrorStat)))
  && (value.trend === null || value.trend === undefined || isMailRuntimeTrend(value.trend))
);

const isMailProviderPreset = (value: unknown): value is MailProviderPreset => (
  isObject(value)
  && typeof value.id === 'string'
  && typeof value.label === 'string'
  && typeof value.imapHost === 'string'
  && isFiniteNumber(value.imapPort)
  && typeof value.imapSecurity === 'string'
  && typeof value.smtpHost === 'string'
  && isFiniteNumber(value.smtpPort)
  && typeof value.smtpSecurity === 'string'
);

const isDeletedCountResponse = (value: unknown): value is { deleted: number } => (
  isObject(value) && isFiniteNumber(value.deleted)
);

const isTestSmtpResponse = (value: unknown): value is EmailTestSmtpResponse => (
  isObject(value) && typeof value.ok === 'boolean'
);

class MailAutomationService {
  async listAccounts(): Promise<MailAccount[]> {
    const response = await api.get<unknown>('/integration/mail/accounts');
    return assertMailArray(response, isMailAccount);
  }

  async createAccount(data: MailAccountRequest): Promise<MailAccount> {
    const response = await api.post<unknown>('/integration/mail/accounts', data);
    return assertMailResponse(response, isMailAccount);
  }

  async updateAccount(accountId: string, data: MailAccountRequest): Promise<MailAccount> {
    const response = await api.put<unknown>(`/integration/mail/accounts/${accountId}`, data);
    return assertMailResponse(response, isMailAccount);
  }

  async deleteAccount(accountId: string): Promise<void> {
    return api.delete(`/integration/mail/accounts/${accountId}`);
  }

  async resetOAuth(accountId: string): Promise<MailAccount> {
    const response = await api.post<unknown>(`/integration/mail/accounts/${accountId}/oauth/reset`);
    return assertMailResponse(response, isMailAccount);
  }

  async listRules(): Promise<MailRule[]> {
    const response = await api.get<unknown>('/integration/mail/rules');
    return assertMailArray(response, isMailRule);
  }

  async createRule(data: MailRuleRequest): Promise<MailRule> {
    const response = await api.post<unknown>('/integration/mail/rules', data);
    return assertMailResponse(response, isMailRule);
  }

  async updateRule(ruleId: string, data: Partial<MailRuleRequest>): Promise<MailRule> {
    const response = await api.put<unknown>(`/integration/mail/rules/${ruleId}`, data);
    return assertMailResponse(response, isMailRule);
  }

  async deleteRule(ruleId: string): Promise<void> {
    return api.delete(`/integration/mail/rules/${ruleId}`);
  }

  async testConnection(accountId: string): Promise<MailConnectionTestResult> {
    const response = await api.post<unknown>(`/integration/mail/accounts/${accountId}/test`);
    return assertMailResponse(response, isMailConnectionTestResult);
  }

  async getOAuthAuthorizeUrl(accountId: string, redirectUrl?: string): Promise<{ url: string; state?: string }> {
    const response = await api.get<unknown>('/integration/mail/oauth/authorize', {
      params: {
        accountId,
        redirectUrl,
      },
    });
    return assertMailResponse(response, isOAuthAuthorizeUrlResponse);
  }

  async listFolders(accountId: string): Promise<string[]> {
    const response = await api.get<unknown>(`/integration/mail/accounts/${accountId}/folders`);
    return assertMailStringArray(response);
  }

  async getDiagnostics(limit = 25, filters?: MailDiagnosticsFilters): Promise<MailDiagnosticsResult> {
    const response = await api.get<unknown>('/integration/mail/diagnostics', {
      params: {
        limit,
        accountId: filters?.accountId || undefined,
        ruleId: filters?.ruleId || undefined,
        status: filters?.status || undefined,
        subject: filters?.subject || undefined,
        errorContains: filters?.errorContains || undefined,
        processedFrom: filters?.processedFrom || undefined,
        processedTo: filters?.processedTo || undefined,
        sort: filters?.sort || undefined,
        order: filters?.order || undefined,
      },
    });
    return assertMailResponse(response, isMailDiagnosticsResult);
  }

  async getReport(filters?: MailReportFilters): Promise<MailReportResponse> {
    const response = await api.get<unknown>('/integration/mail/report', {
      params: {
        accountId: filters?.accountId || undefined,
        ruleId: filters?.ruleId || undefined,
        from: filters?.from || undefined,
        to: filters?.to || undefined,
        days: filters?.days ?? undefined,
      },
    });
    return assertMailResponse(response, isMailReportResponse);
  }

  async exportReportCsv(filters?: MailReportFilters): Promise<Blob> {
    return api.getBlob('/integration/mail/report/export', {
      params: {
        accountId: filters?.accountId || undefined,
        ruleId: filters?.ruleId || undefined,
        from: filters?.from || undefined,
        to: filters?.to || undefined,
        days: filters?.days ?? undefined,
      },
    });
  }

  async getReportSchedule(): Promise<MailReportScheduleStatus> {
    const response = await api.get<unknown>('/integration/mail/report/schedule');
    return assertMailResponse(response, isMailReportScheduleStatus);
  }

  async runReportScheduleNow(): Promise<MailReportScheduledExportResult> {
    const response = await api.post<unknown>('/integration/mail/report/schedule/run');
    return assertMailResponse(response, isMailReportScheduledExportResult);
  }

  async exportDiagnosticsCsv(
    limit = 25,
    filters?: MailDiagnosticsFilters,
    options?: MailDiagnosticsExportOptions,
    runId?: string | null,
  ): Promise<Blob> {
    return api.getBlob('/integration/mail/diagnostics/export', {
      params: {
        limit,
        accountId: filters?.accountId || undefined,
        ruleId: filters?.ruleId || undefined,
        status: filters?.status || undefined,
        subject: filters?.subject || undefined,
        errorContains: filters?.errorContains || undefined,
        processedFrom: filters?.processedFrom || undefined,
        processedTo: filters?.processedTo || undefined,
        sort: filters?.sort || undefined,
        order: filters?.order || undefined,
        includeProcessed: options?.includeProcessed ?? undefined,
        includeDocuments: options?.includeDocuments ?? undefined,
        includeSubject: options?.includeSubject ?? undefined,
        includeError: options?.includeError ?? undefined,
        includePath: options?.includePath ?? undefined,
        includeMimeType: options?.includeMimeType ?? undefined,
        includeFileSize: options?.includeFileSize ?? undefined,
        runId: runId || undefined,
      },
    });
  }

  async bulkDeleteProcessedMail(ids: string[]): Promise<{ deleted: number }> {
    const response = await api.post<unknown>('/integration/mail/processed/bulk-delete', { ids });
    return assertMailResponse(response, isDeletedCountResponse);
  }

  async replayProcessedMail(id: string): Promise<MailReplayResult> {
    const response = await api.post<unknown>(`/integration/mail/processed/${id}/replay`);
    return assertMailResponse(response, isMailReplayResult);
  }

  async listProcessedMailDocuments(id: string, limit?: number): Promise<MailDocumentDiagnosticItem[]> {
    const response = await api.get<unknown>(`/integration/mail/processed/${id}/documents`, {
      params: {
        limit: limit ?? undefined,
      },
    });
    return assertMailArray(response, isMailDocumentDiagnosticItem);
  }

  async getProcessedRetention(): Promise<ProcessedMailRetentionStatus> {
    const response = await api.get<unknown>('/integration/mail/processed/retention');
    return assertMailResponse(response, isProcessedMailRetentionStatus);
  }

  async cleanupProcessedRetention(): Promise<{ deleted: number }> {
    const response = await api.post<unknown>('/integration/mail/processed/cleanup');
    return assertMailResponse(response, isDeletedCountResponse);
  }

  async getRuntimeMetrics(windowMinutes?: number): Promise<MailRuntimeMetrics> {
    const response = await api.get<unknown>('/integration/mail/runtime-metrics', {
      params: {
        windowMinutes: windowMinutes ?? undefined,
      },
    });
    return assertMailResponse(response, isMailRuntimeMetrics);
  }

  async triggerFetch(): Promise<MailFetchSummary> {
    const response = await api.post<unknown>('/integration/mail/fetch');
    return assertMailResponse(response, isMailFetchSummary);
  }

  async getFetchSummary(): Promise<MailFetchSummaryStatus> {
    const response = await api.get<unknown>('/integration/mail/fetch/summary');
    return assertMailResponse(response, isMailFetchSummaryStatus);
  }

  async triggerFetchDebug(options?: {
    force?: boolean;
    maxMessagesPerFolder?: number;
  }): Promise<MailFetchDebugResult> {
    const force = options?.force ?? true;
    const maxMessagesPerFolder = options?.maxMessagesPerFolder;
    const response = await api.post<unknown>('/integration/mail/fetch/debug', undefined, {
      params: {
        force,
        maxMessagesPerFolder,
      },
    });
    return assertMailResponse(response, isMailFetchDebugResult);
  }

  async previewRule(
    ruleId: string,
    accountId: string,
    maxMessagesPerFolder?: number,
  ): Promise<MailRulePreviewResult> {
    const response = await api.post<unknown>(`/integration/mail/rules/${ruleId}/preview`, {
      accountId,
      maxMessagesPerFolder,
    });
    return assertMailResponse(response, isMailRulePreviewResult);
  }

  async listProviderPresets(): Promise<MailProviderPreset[]> {
    // Phase 5 Mocked may serve SPA HTML for unmocked routes. Falling back to
    // an empty list lets the page render the Custom option instead of
    // crashing — preferred to throwing because the provider preset is purely
    // a convenience dropdown and the page already tolerates an empty list.
    const response = await api.get<unknown>('/integration/mail/provider-presets');
    if (!Array.isArray(response)) {
      return [];
    }
    return response.every(isMailProviderPreset) ? (response as MailProviderPreset[]) : [];
  }

  async testSmtp(payload: EmailTestSmtpRequest): Promise<EmailTestSmtpResponse> {
    const response = await api.post<unknown>('/admin/email/test-smtp', payload);
    if (!isTestSmtpResponse(response)) {
      throw new Error(TEST_SMTP_UNEXPECTED_RESPONSE_MESSAGE);
    }
    return response;
  }
}

const mailAutomationService = new MailAutomationService();
export default mailAutomationService;
