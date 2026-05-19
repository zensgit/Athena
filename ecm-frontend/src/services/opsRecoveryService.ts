import api from './api';

export const OPS_RECOVERY_UNEXPECTED_RESPONSE_MESSAGE =
  'Ops recovery endpoint returned an unexpected response. Mocked CI gate may not cover it; backend route may be missing.';

export type RecoveryJobState =
  | 'READY'
  | 'PROCESSING'
  | 'FAILED'
  | 'QUEUED'
  | 'CLEARED'
  | 'PENDING'
  | 'SKIPPED'
  | 'UNSUPPORTED'
  | 'UNKNOWN';

export type RecoveryFailureCategory =
  | 'TEMPORARY'
  | 'PERMANENT'
  | 'UNSUPPORTED'
  | 'UNKNOWN';

export type RecoveryBatchItem = {
  documentId: string;
  jobState: RecoveryJobState;
  outcome: 'QUEUED' | 'SKIPPED' | 'FAILED' | string;
  message: string | null;
  previewStatus: string | null;
  failureCategory: RecoveryFailureCategory;
  previewFailureReason?: string | null;
  previewFailureCategory?: string | null;
  previewLastUpdated?: string | null;
  attempts: number;
  nextAttemptAt: string | null;
};

export type RecoveryBatchResult = {
  domain: string;
  mode: 'QUEUE_BY_REASON' | 'QUEUE_BY_WINDOW' | 'REPLAY_BATCH' | string;
  windowDays: number;
  maxDocuments: number;
  totalCandidates: number;
  scanned: number;
  matched: number;
  truncated: boolean;
  requested: number;
  deduplicated: number;
  queued: number;
  skipped: number;
  failed: number;
  results: RecoveryBatchItem[];
  error?: string | null;
};

export type RecoveryDryRunItem = {
  documentId: string;
  name: string | null;
  path: string | null;
  mimeType: string | null;
  previewStatus: string | null;
  failureCategory: RecoveryFailureCategory;
  previewFailureReason?: string | null;
  previewFailureCategory?: string | null;
  previewLastUpdated?: string | null;
  predictedState: RecoveryJobState;
  predictedOutcome: string;
  predictedReason: string;
};

export type RecoveryDryRunResult = {
  domain: string;
  mode: 'QUEUE_BY_REASON' | 'QUEUE_BY_WINDOW' | 'REPLAY_BATCH' | string;
  windowDays: number;
  maxDocuments: number;
  totalCandidates: number;
  scanned: number;
  matched: number;
  truncated: boolean;
  estimatedQueued: number;
  estimatedSkipped: number;
  estimatedFailed: number;
  samples: RecoveryDryRunItem[];
  error?: string | null;
};

export type RecoveryHistoryItem = {
  id: string | null;
  nodeId?: string | null;
  nodeName?: string | null;
  eventType: string | null;
  mode: string;
  actor: string | null;
  eventTime: string | null;
  details: string | null;
  previewStatus?: string | null;
  previewFailureReason?: string | null;
  previewFailureCategory?: string | null;
  previewLastUpdated?: string | null;
};

export type RecoveryHistoryMode =
  | 'QUEUE_BY_REASON'
  | 'QUEUE_BY_WINDOW'
  | 'CLEAR_BATCH'
  | 'CLEAR_BY_FILTER'
  | 'REPLAY_BATCH'
  | 'REPLAY_BY_FILTER'
  | 'DRY_RUN';

export type RecoveryHistoryEventType =
  | 'OPS_RECOVERY_QUEUE_BY_REASON'
  | 'OPS_RECOVERY_QUEUE_BY_WINDOW'
  | 'OPS_RECOVERY_CLEAR_BATCH'
  | 'OPS_RECOVERY_CLEAR_BY_FILTER'
  | 'OPS_RECOVERY_REPLAY_BATCH'
  | 'OPS_RECOVERY_REPLAY_BY_FILTER'
  | 'OPS_RECOVERY_DRY_RUN'
  | 'OPS_RECOVERY_HISTORY_EXPORT'
  | 'OPS_RECOVERY_HISTORY_SUMMARY_EXPORT'
  | 'OPS_RECOVERY_HISTORY_TREND_EXPORT'
  | 'OPS_RECOVERY_HISTORY_COMPARE_EXPORT'
  | 'OPS_RECOVERY_HISTORY_COMPARE_BREAKDOWN_EXPORT'
  | 'OPS_RECOVERY_HISTORY_ACTOR_COMPARE_EXPORT';

export type RecoveryHistoryExportAsyncType =
  | 'HISTORY'
  | 'HISTORY_SUMMARY'
  | 'HISTORY_TREND'
  | 'HISTORY_COMPARE'
  | 'HISTORY_COMPARE_BREAKDOWN'
  | 'HISTORY_COMPARE_ACTORS';

export type RecoveryHistoryExportAsyncStatusFilter =
  | 'QUEUED'
  | 'RUNNING'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'FAILED'
  | 'TIMED_OUT'
  | 'EXPIRED';

export type RecoveryHistoryExportAsyncActiveStatusFilter =
  | 'QUEUED'
  | 'RUNNING';

export type RecoveryHistoryExportAsyncTerminalStatusFilter =
  | 'COMPLETED'
  | 'CANCELLED'
  | 'FAILED'
  | 'TIMED_OUT'
  | 'EXPIRED';

export type RecoveryHistoryExportAsyncRequest = {
  exportType?: RecoveryHistoryExportAsyncType;
  limit?: number;
  days?: number;
  mode?: RecoveryHistoryMode;
  actor?: string;
  eventType?: RecoveryHistoryEventType;
  compareBreakdownLimit?: number;
  compareBreakdownSort?: RecoveryHistoryCompareBreakdownSort;
  compareActorLimit?: number;
  compareActorSort?: RecoveryHistoryCompareActorSort;
};

export type RecoveryHistoryExportAsyncRequestSnapshot = {
  exportType?: RecoveryHistoryExportAsyncType | string | null;
  limit?: number;
  days?: number;
  mode?: RecoveryHistoryMode | string | null;
  actor?: string | null;
  eventType?: RecoveryHistoryEventType | string | null;
  compareBreakdownLimit?: number;
  compareBreakdownSort?: RecoveryHistoryCompareBreakdownSort | string | null;
  compareActorLimit?: number;
  compareActorSort?: RecoveryHistoryCompareActorSort | string | null;
};

export type RecoveryHistoryExportAsyncCreateResponse = {
  taskId: string;
  exportType: RecoveryHistoryExportAsyncType | string;
  status: string;
  request?: RecoveryHistoryExportAsyncRequestSnapshot | null;
  deduplicated?: boolean;
  deduplicatedFromTaskId?: string | null;
  message?: string | null;
  createdAt: string | null;
  timeoutAt?: string | null;
  expiresAt?: string | null;
  createdBy?: string | null;
  updatedBy?: string | null;
};

export type RecoveryHistoryExportAsyncTaskStatus = {
  taskId: string;
  exportType: RecoveryHistoryExportAsyncType | string;
  status: string;
  error: string | null;
  request?: RecoveryHistoryExportAsyncRequestSnapshot | null;
  createdAt: string | null;
  startedAt?: string | null;
  updatedAt?: string | null;
  timeoutAt?: string | null;
  expiresAt?: string | null;
  finishedAt: string | null;
  filename: string | null;
  createdBy?: string | null;
  updatedBy?: string | null;
};

export type RecoveryHistoryExportAsyncTaskList = {
  count: number;
  paging?: RecoveryTaskCenterPaging | null;
  items: RecoveryHistoryExportAsyncTaskStatus[];
};

export type RecoveryTaskCenterPaging = {
  skipCount: number;
  maxItems: number;
  totalItems: number;
  hasMoreItems: boolean;
};

export type RecoveryHistoryExportAsyncTaskSummary = {
  totalCount: number;
  queuedCount: number;
  runningCount: number;
  completedCount: number;
  cancelledCount: number;
  failedCount: number;
  timedOutCount?: number;
  expiredCount?: number;
  activeCount: number;
  terminalCount: number;
};

export type RecoveryHistoryExportAsyncTaskCleanupResponse = {
  deletedCount: number;
  remainingCount: number;
  exportTypeFilter?: RecoveryHistoryExportAsyncType | string | null;
  statusFilter?: string | null;
  message: string;
};

export type RecoveryHistoryExportAsyncTaskCancelActiveResponse = {
  cancelledCount: number;
  remainingActiveCount: number;
  exportTypeFilter?: RecoveryHistoryExportAsyncType | string | null;
  statusFilter?: string | null;
  message: string;
};

export type RecoveryHistoryExportAsyncRetryTerminalItem = {
  sourceTaskId?: string | null;
  retriedTaskId?: string | null;
  exportType?: RecoveryHistoryExportAsyncType | string | null;
  sourceStatus?: string | null;
  outcome?: string | null;
  message?: string | null;
};

export type RecoveryHistoryExportAsyncRetryTerminalResponse = {
  requested: number;
  retried: number;
  reused: number;
  skipped: number;
  failed: number;
  limit: number;
  exportTypeFilter?: RecoveryHistoryExportAsyncType | string | null;
  statusFilter?: string | null;
  message: string;
  results: RecoveryHistoryExportAsyncRetryTerminalItem[];
};

export type RecoveryHistoryExportAsyncRetryTerminalDryRunItem = {
  sourceTaskId?: string | null;
  exportType?: RecoveryHistoryExportAsyncType | string | null;
  sourceStatus?: string | null;
  outcome?: string | null;
  reasonCode?: string | null;
  message?: string | null;
};

export type RecoveryHistoryExportAsyncRetryTerminalDryRunReasonCount = {
  reasonCode?: string | null;
  outcome?: string | null;
  count: number;
};

export type RecoveryHistoryExportAsyncRetryTerminalDryRunResponse = {
  requested: number;
  retryable: number;
  skipped: number;
  limit: number;
  exportTypeFilter?: RecoveryHistoryExportAsyncType | string | null;
  statusFilter?: string | null;
  message: string;
  results: RecoveryHistoryExportAsyncRetryTerminalDryRunItem[];
  reasonBreakdown?: RecoveryHistoryExportAsyncRetryTerminalDryRunReasonCount[];
};

export type RecoveryHistoryExportAsyncRetryTerminalByTaskIdsRequest = {
  sourceTaskIds: string[];
};

export type RecoveryHistoryResult = {
  domain: string;
  windowDays: number;
  limit: number;
  page: number;
  totalPages: number;
  total: number;
  modeFilter?: RecoveryHistoryMode | null;
  actorFilter?: string | null;
  eventTypeFilter?: RecoveryHistoryEventType | null;
  items: RecoveryHistoryItem[];
};

export type RecoveryHistorySummaryItem = {
  eventType: string;
  mode: string;
  count: number;
};

export type RecoveryHistoryActorSummaryItem = {
  actor: string;
  count: number;
};

export type RecoveryHistorySummaryResult = {
  domain: string;
  windowDays: number;
  modeFilter?: RecoveryHistoryMode | null;
  actorFilter?: string | null;
  eventTypeFilter?: RecoveryHistoryEventType | null;
  total: number;
  items: RecoveryHistorySummaryItem[];
  actorItems: RecoveryHistoryActorSummaryItem[];
};

export type RecoveryHistoryTrendItem = {
  day: string;
  count: number;
};

export type RecoveryHistoryTrendResult = {
  domain: string;
  windowDays: number;
  modeFilter?: RecoveryHistoryMode | null;
  actorFilter?: string | null;
  eventTypeFilter?: RecoveryHistoryEventType | null;
  total: number;
  truncated: boolean;
  items: RecoveryHistoryTrendItem[];
};

export type RecoveryHistorySummaryCompareResult = {
  domain: string;
  windowDays: number;
  previousWindowDays: number;
  modeFilter?: RecoveryHistoryMode | null;
  actorFilter?: string | null;
  eventTypeFilter?: RecoveryHistoryEventType | null;
  currentTotal: number;
  previousTotal: number;
  delta: number;
  deltaPercent?: number | null;
  compareAvailable: boolean;
  truncated: boolean;
};

export type RecoveryHistorySummaryCompareActorItem = {
  actor: string;
  currentCount: number;
  previousCount: number;
  delta: number;
  deltaPercent?: number | null;
};

export type RecoveryHistorySummaryCompareBreakdownItem = {
  eventType: string;
  mode: string;
  currentCount: number;
  previousCount: number;
  delta: number;
  deltaPercent?: number | null;
};

export type RecoveryHistoryCompareBreakdownSort =
  | 'DELTA_ABS_DESC'
  | 'DELTA_DESC'
  | 'DELTA_ASC'
  | 'CURRENT_DESC'
  | 'PREVIOUS_DESC'
  | 'EVENT_TYPE_ASC';

export type RecoveryHistoryCompareActorSort =
  | 'DELTA_ABS_DESC'
  | 'DELTA_DESC'
  | 'DELTA_ASC'
  | 'CURRENT_DESC'
  | 'PREVIOUS_DESC'
  | 'ACTOR_ASC';

export type RecoveryHistorySummaryCompareActorsResult = {
  domain: string;
  windowDays: number;
  previousWindowDays: number;
  modeFilter?: RecoveryHistoryMode | null;
  actorFilter?: string | null;
  eventTypeFilter?: RecoveryHistoryEventType | null;
  compareAvailable: boolean;
  truncated: boolean;
  sortBy?: RecoveryHistoryCompareActorSort | string;
  requestedLimit?: number;
  totalItems?: number;
  limited?: boolean;
  items: RecoveryHistorySummaryCompareActorItem[];
};

export type RecoveryHistorySummaryCompareBreakdownResult = {
  domain: string;
  windowDays: number;
  previousWindowDays: number;
  modeFilter?: RecoveryHistoryMode | null;
  actorFilter?: string | null;
  eventTypeFilter?: RecoveryHistoryEventType | null;
  compareAvailable: boolean;
  truncated: boolean;
  sortBy?: RecoveryHistoryCompareBreakdownSort | string;
  requestedLimit?: number;
  totalItems?: number;
  limited?: boolean;
  items: RecoveryHistorySummaryCompareBreakdownItem[];
};

export type QueueByReasonRequest = {
  domain?: 'PREVIEW' | string;
  reason: string;
  category?: string;
  retryable?: boolean;
  maxDocuments?: number;
  days?: number;
  force?: boolean;
};

export type QueueByWindowRequest = {
  domain?: 'PREVIEW' | string;
  reason?: string;
  category?: string;
  retryable?: boolean;
  maxDocuments?: number;
  days?: number;
  force?: boolean;
};

export type ReplayBatchRequest = {
  domain?: 'PREVIEW' | string;
  documentIds?: string[];
  entryKeys?: string[];
  force?: boolean;
};

export type ClearBatchRequest = {
  domain?: 'PREVIEW' | string;
  documentIds?: string[];
  entryKeys?: string[];
};

export type ClearByFilterRequest = {
  domain?: 'PREVIEW' | string;
  reason?: string;
  category?: string;
  retryable?: boolean;
  maxDocuments?: number;
  days?: number;
};

export type ReplayByFilterRequest = {
  domain?: 'PREVIEW' | string;
  reason?: string;
  category?: string;
  retryable?: boolean;
  maxDocuments?: number;
  days?: number;
  force?: boolean;
};

export type RecoveryDryRunRequest = {
  domain?: 'PREVIEW' | string;
  mode?: 'QUEUE_BY_REASON' | 'QUEUE_BY_WINDOW' | 'CLEAR_BY_FILTER' | 'REPLAY_BY_FILTER' | 'REPLAY_BATCH' | string;
  reason?: string;
  category?: string;
  retryable?: boolean;
  maxDocuments?: number;
  days?: number;
  force?: boolean;
  documentIds?: string[];
};

const isRecord = (value: unknown): value is Record<string, unknown> =>
  typeof value === 'object' && value !== null && !Array.isArray(value);

const isFiniteNumber = (value: unknown): value is number =>
  typeof value === 'number' && Number.isFinite(value);

const isNullableString = (value: unknown): value is string | null =>
  value === null || typeof value === 'string';

const isOptionalNullableString = (value: unknown): value is string | null | undefined =>
  value === undefined || isNullableString(value);

const isOptionalString = (value: unknown): value is string | undefined =>
  value === undefined || typeof value === 'string';

const isOptionalBoolean = (value: unknown): value is boolean | undefined =>
  value === undefined || typeof value === 'boolean';

const isOptionalFiniteNumber = (value: unknown): value is number | undefined =>
  value === undefined || isFiniteNumber(value);

const isOptionalNullableFiniteNumber = (value: unknown): value is number | null | undefined =>
  value === undefined || value === null || isFiniteNumber(value);

function assertOpsRecoveryResponse(condition: unknown): asserts condition {
  if (!condition) {
    throw new Error(OPS_RECOVERY_UNEXPECTED_RESPONSE_MESSAGE);
  }
}

const assertRecoveryBatchItem = (value: unknown): RecoveryBatchItem => {
  assertOpsRecoveryResponse(isRecord(value));
  assertOpsRecoveryResponse(typeof value.documentId === 'string');
  assertOpsRecoveryResponse(typeof value.jobState === 'string');
  assertOpsRecoveryResponse(typeof value.outcome === 'string');
  assertOpsRecoveryResponse(isNullableString(value.message));
  assertOpsRecoveryResponse(isNullableString(value.previewStatus));
  assertOpsRecoveryResponse(typeof value.failureCategory === 'string');
  assertOpsRecoveryResponse(isOptionalNullableString(value.previewFailureReason));
  assertOpsRecoveryResponse(isOptionalNullableString(value.previewFailureCategory));
  assertOpsRecoveryResponse(isOptionalNullableString(value.previewLastUpdated));
  assertOpsRecoveryResponse(isFiniteNumber(value.attempts));
  assertOpsRecoveryResponse(isNullableString(value.nextAttemptAt));

  return value as unknown as RecoveryBatchItem;
};

const assertRecoveryBatchItems = (value: unknown): RecoveryBatchItem[] => {
  assertOpsRecoveryResponse(Array.isArray(value));
  return value.map(assertRecoveryBatchItem);
};

const assertRecoveryBatchResult = (value: unknown): RecoveryBatchResult => {
  assertOpsRecoveryResponse(isRecord(value));
  assertOpsRecoveryResponse(typeof value.domain === 'string');
  assertOpsRecoveryResponse(typeof value.mode === 'string');
  assertOpsRecoveryResponse(isFiniteNumber(value.windowDays));
  assertOpsRecoveryResponse(isFiniteNumber(value.maxDocuments));
  assertOpsRecoveryResponse(isFiniteNumber(value.totalCandidates));
  assertOpsRecoveryResponse(isFiniteNumber(value.scanned));
  assertOpsRecoveryResponse(isFiniteNumber(value.matched));
  assertOpsRecoveryResponse(typeof value.truncated === 'boolean');
  assertOpsRecoveryResponse(isFiniteNumber(value.requested));
  assertOpsRecoveryResponse(isFiniteNumber(value.deduplicated));
  assertOpsRecoveryResponse(isFiniteNumber(value.queued));
  assertOpsRecoveryResponse(isFiniteNumber(value.skipped));
  assertOpsRecoveryResponse(isFiniteNumber(value.failed));
  assertOpsRecoveryResponse(isOptionalNullableString(value.error));
  const results = assertRecoveryBatchItems(value.results);

  return {
    ...value,
    results,
  } as RecoveryBatchResult;
};

const assertRecoveryDryRunItem = (value: unknown): RecoveryDryRunItem => {
  assertOpsRecoveryResponse(isRecord(value));
  assertOpsRecoveryResponse(typeof value.documentId === 'string');
  assertOpsRecoveryResponse(isNullableString(value.name));
  assertOpsRecoveryResponse(isNullableString(value.path));
  assertOpsRecoveryResponse(isNullableString(value.mimeType));
  assertOpsRecoveryResponse(isNullableString(value.previewStatus));
  assertOpsRecoveryResponse(typeof value.failureCategory === 'string');
  assertOpsRecoveryResponse(isOptionalNullableString(value.previewFailureReason));
  assertOpsRecoveryResponse(isOptionalNullableString(value.previewFailureCategory));
  assertOpsRecoveryResponse(isOptionalNullableString(value.previewLastUpdated));
  assertOpsRecoveryResponse(typeof value.predictedState === 'string');
  assertOpsRecoveryResponse(typeof value.predictedOutcome === 'string');
  assertOpsRecoveryResponse(typeof value.predictedReason === 'string');

  return value as unknown as RecoveryDryRunItem;
};

const assertRecoveryDryRunResult = (value: unknown): RecoveryDryRunResult => {
  assertOpsRecoveryResponse(isRecord(value));
  assertOpsRecoveryResponse(typeof value.domain === 'string');
  assertOpsRecoveryResponse(typeof value.mode === 'string');
  assertOpsRecoveryResponse(isFiniteNumber(value.windowDays));
  assertOpsRecoveryResponse(isFiniteNumber(value.maxDocuments));
  assertOpsRecoveryResponse(isFiniteNumber(value.totalCandidates));
  assertOpsRecoveryResponse(isFiniteNumber(value.scanned));
  assertOpsRecoveryResponse(isFiniteNumber(value.matched));
  assertOpsRecoveryResponse(typeof value.truncated === 'boolean');
  assertOpsRecoveryResponse(isFiniteNumber(value.estimatedQueued));
  assertOpsRecoveryResponse(isFiniteNumber(value.estimatedSkipped));
  assertOpsRecoveryResponse(isFiniteNumber(value.estimatedFailed));
  assertOpsRecoveryResponse(isOptionalNullableString(value.error));
  assertOpsRecoveryResponse(Array.isArray(value.samples));
  const samples = value.samples.map(assertRecoveryDryRunItem);

  return {
    ...value,
    samples,
  } as RecoveryDryRunResult;
};

const assertRecoveryHistoryItem = (value: unknown): RecoveryHistoryItem => {
  assertOpsRecoveryResponse(isRecord(value));
  assertOpsRecoveryResponse(isNullableString(value.id));
  assertOpsRecoveryResponse(isOptionalNullableString(value.nodeId));
  assertOpsRecoveryResponse(isOptionalNullableString(value.nodeName));
  assertOpsRecoveryResponse(isNullableString(value.eventType));
  assertOpsRecoveryResponse(typeof value.mode === 'string');
  assertOpsRecoveryResponse(isNullableString(value.actor));
  assertOpsRecoveryResponse(isNullableString(value.eventTime));
  assertOpsRecoveryResponse(isNullableString(value.details));
  assertOpsRecoveryResponse(isOptionalNullableString(value.previewStatus));
  assertOpsRecoveryResponse(isOptionalNullableString(value.previewFailureReason));
  assertOpsRecoveryResponse(isOptionalNullableString(value.previewFailureCategory));
  assertOpsRecoveryResponse(isOptionalNullableString(value.previewLastUpdated));

  return value as unknown as RecoveryHistoryItem;
};

const assertRecoveryHistoryResult = (value: unknown): RecoveryHistoryResult => {
  assertOpsRecoveryResponse(isRecord(value));
  assertOpsRecoveryResponse(typeof value.domain === 'string');
  assertOpsRecoveryResponse(isFiniteNumber(value.windowDays));
  assertOpsRecoveryResponse(isFiniteNumber(value.limit));
  assertOpsRecoveryResponse(isFiniteNumber(value.page));
  assertOpsRecoveryResponse(isFiniteNumber(value.totalPages));
  assertOpsRecoveryResponse(isFiniteNumber(value.total));
  assertOpsRecoveryResponse(isOptionalNullableString(value.modeFilter));
  assertOpsRecoveryResponse(isOptionalNullableString(value.actorFilter));
  assertOpsRecoveryResponse(isOptionalNullableString(value.eventTypeFilter));
  assertOpsRecoveryResponse(Array.isArray(value.items));
  const items = value.items.map(assertRecoveryHistoryItem);

  return {
    ...value,
    items,
  } as RecoveryHistoryResult;
};

const assertRecoveryHistorySummaryItem = (value: unknown): RecoveryHistorySummaryItem => {
  assertOpsRecoveryResponse(isRecord(value));
  assertOpsRecoveryResponse(typeof value.eventType === 'string');
  assertOpsRecoveryResponse(typeof value.mode === 'string');
  assertOpsRecoveryResponse(isFiniteNumber(value.count));

  return value as unknown as RecoveryHistorySummaryItem;
};

const assertRecoveryHistoryActorSummaryItem = (value: unknown): RecoveryHistoryActorSummaryItem => {
  assertOpsRecoveryResponse(isRecord(value));
  assertOpsRecoveryResponse(typeof value.actor === 'string');
  assertOpsRecoveryResponse(isFiniteNumber(value.count));

  return value as unknown as RecoveryHistoryActorSummaryItem;
};

const assertRecoveryHistorySummaryResult = (value: unknown): RecoveryHistorySummaryResult => {
  assertOpsRecoveryResponse(isRecord(value));
  assertOpsRecoveryResponse(typeof value.domain === 'string');
  assertOpsRecoveryResponse(isFiniteNumber(value.windowDays));
  assertOpsRecoveryResponse(isOptionalNullableString(value.modeFilter));
  assertOpsRecoveryResponse(isOptionalNullableString(value.actorFilter));
  assertOpsRecoveryResponse(isOptionalNullableString(value.eventTypeFilter));
  assertOpsRecoveryResponse(isFiniteNumber(value.total));
  assertOpsRecoveryResponse(Array.isArray(value.items));
  assertOpsRecoveryResponse(Array.isArray(value.actorItems));
  const items = value.items.map(assertRecoveryHistorySummaryItem);
  const actorItems = value.actorItems.map(assertRecoveryHistoryActorSummaryItem);

  return {
    ...value,
    items,
    actorItems,
  } as RecoveryHistorySummaryResult;
};

const assertRecoveryHistoryTrendItem = (value: unknown): RecoveryHistoryTrendItem => {
  assertOpsRecoveryResponse(isRecord(value));
  assertOpsRecoveryResponse(typeof value.day === 'string');
  assertOpsRecoveryResponse(isFiniteNumber(value.count));

  return value as unknown as RecoveryHistoryTrendItem;
};

const assertRecoveryHistoryTrendResult = (value: unknown): RecoveryHistoryTrendResult => {
  assertOpsRecoveryResponse(isRecord(value));
  assertOpsRecoveryResponse(typeof value.domain === 'string');
  assertOpsRecoveryResponse(isFiniteNumber(value.windowDays));
  assertOpsRecoveryResponse(isOptionalNullableString(value.modeFilter));
  assertOpsRecoveryResponse(isOptionalNullableString(value.actorFilter));
  assertOpsRecoveryResponse(isOptionalNullableString(value.eventTypeFilter));
  assertOpsRecoveryResponse(isFiniteNumber(value.total));
  assertOpsRecoveryResponse(typeof value.truncated === 'boolean');
  assertOpsRecoveryResponse(Array.isArray(value.items));
  const items = value.items.map(assertRecoveryHistoryTrendItem);

  return {
    ...value,
    items,
  } as RecoveryHistoryTrendResult;
};

const assertRecoveryHistorySummaryCompareResult = (
  value: unknown
): RecoveryHistorySummaryCompareResult => {
  assertOpsRecoveryResponse(isRecord(value));
  assertOpsRecoveryResponse(typeof value.domain === 'string');
  assertOpsRecoveryResponse(isFiniteNumber(value.windowDays));
  assertOpsRecoveryResponse(isFiniteNumber(value.previousWindowDays));
  assertOpsRecoveryResponse(isOptionalNullableString(value.modeFilter));
  assertOpsRecoveryResponse(isOptionalNullableString(value.actorFilter));
  assertOpsRecoveryResponse(isOptionalNullableString(value.eventTypeFilter));
  assertOpsRecoveryResponse(isFiniteNumber(value.currentTotal));
  assertOpsRecoveryResponse(isFiniteNumber(value.previousTotal));
  assertOpsRecoveryResponse(isFiniteNumber(value.delta));
  assertOpsRecoveryResponse(isOptionalNullableFiniteNumber(value.deltaPercent));
  assertOpsRecoveryResponse(typeof value.compareAvailable === 'boolean');
  assertOpsRecoveryResponse(typeof value.truncated === 'boolean');

  return value as unknown as RecoveryHistorySummaryCompareResult;
};

const assertRecoveryHistorySummaryCompareActorItem = (
  value: unknown
): RecoveryHistorySummaryCompareActorItem => {
  assertOpsRecoveryResponse(isRecord(value));
  assertOpsRecoveryResponse(typeof value.actor === 'string');
  assertOpsRecoveryResponse(isFiniteNumber(value.currentCount));
  assertOpsRecoveryResponse(isFiniteNumber(value.previousCount));
  assertOpsRecoveryResponse(isFiniteNumber(value.delta));
  assertOpsRecoveryResponse(isOptionalNullableFiniteNumber(value.deltaPercent));

  return value as unknown as RecoveryHistorySummaryCompareActorItem;
};

const assertRecoveryHistorySummaryCompareBreakdownItem = (
  value: unknown
): RecoveryHistorySummaryCompareBreakdownItem => {
  assertOpsRecoveryResponse(isRecord(value));
  assertOpsRecoveryResponse(typeof value.eventType === 'string');
  assertOpsRecoveryResponse(typeof value.mode === 'string');
  assertOpsRecoveryResponse(isFiniteNumber(value.currentCount));
  assertOpsRecoveryResponse(isFiniteNumber(value.previousCount));
  assertOpsRecoveryResponse(isFiniteNumber(value.delta));
  assertOpsRecoveryResponse(isOptionalNullableFiniteNumber(value.deltaPercent));

  return value as unknown as RecoveryHistorySummaryCompareBreakdownItem;
};

const assertRecoveryHistorySummaryCompareActorsResult = (
  value: unknown
): RecoveryHistorySummaryCompareActorsResult => {
  assertOpsRecoveryResponse(isRecord(value));
  assertOpsRecoveryResponse(typeof value.domain === 'string');
  assertOpsRecoveryResponse(isFiniteNumber(value.windowDays));
  assertOpsRecoveryResponse(isFiniteNumber(value.previousWindowDays));
  assertOpsRecoveryResponse(isOptionalNullableString(value.modeFilter));
  assertOpsRecoveryResponse(isOptionalNullableString(value.actorFilter));
  assertOpsRecoveryResponse(isOptionalNullableString(value.eventTypeFilter));
  assertOpsRecoveryResponse(typeof value.compareAvailable === 'boolean');
  assertOpsRecoveryResponse(typeof value.truncated === 'boolean');
  assertOpsRecoveryResponse(isOptionalString(value.sortBy));
  assertOpsRecoveryResponse(isOptionalFiniteNumber(value.requestedLimit));
  assertOpsRecoveryResponse(isOptionalFiniteNumber(value.totalItems));
  assertOpsRecoveryResponse(isOptionalBoolean(value.limited));
  assertOpsRecoveryResponse(Array.isArray(value.items));
  const items = value.items.map(assertRecoveryHistorySummaryCompareActorItem);

  return {
    ...value,
    items,
  } as RecoveryHistorySummaryCompareActorsResult;
};

const assertRecoveryHistorySummaryCompareBreakdownResult = (
  value: unknown
): RecoveryHistorySummaryCompareBreakdownResult => {
  assertOpsRecoveryResponse(isRecord(value));
  assertOpsRecoveryResponse(typeof value.domain === 'string');
  assertOpsRecoveryResponse(isFiniteNumber(value.windowDays));
  assertOpsRecoveryResponse(isFiniteNumber(value.previousWindowDays));
  assertOpsRecoveryResponse(isOptionalNullableString(value.modeFilter));
  assertOpsRecoveryResponse(isOptionalNullableString(value.actorFilter));
  assertOpsRecoveryResponse(isOptionalNullableString(value.eventTypeFilter));
  assertOpsRecoveryResponse(typeof value.compareAvailable === 'boolean');
  assertOpsRecoveryResponse(typeof value.truncated === 'boolean');
  assertOpsRecoveryResponse(isOptionalString(value.sortBy));
  assertOpsRecoveryResponse(isOptionalFiniteNumber(value.requestedLimit));
  assertOpsRecoveryResponse(isOptionalFiniteNumber(value.totalItems));
  assertOpsRecoveryResponse(isOptionalBoolean(value.limited));
  assertOpsRecoveryResponse(Array.isArray(value.items));
  const items = value.items.map(assertRecoveryHistorySummaryCompareBreakdownItem);

  return {
    ...value,
    items,
  } as RecoveryHistorySummaryCompareBreakdownResult;
};

// --- async export-tail guards ---
// Reuses the existing OPS_RECOVERY_UNEXPECTED_RESPONSE_MESSAGE sentinel,
// assertOpsRecoveryResponse, and the predicate bundle above. No new sentinel.

// request is consumed by the UI for display, so it gets lightweight deep
// validation: undefined | null | record-with-typed-fields. A bare isRecord
// would let { mode: {} } reach the UI and render as "[object Object]".
// Per the wire type, the numeric fields are not nullable.
const assertRecoveryHistoryExportAsyncRequestSnapshot = (value: unknown): void => {
  if (value === undefined || value === null) {
    return;
  }
  assertOpsRecoveryResponse(isRecord(value));
  assertOpsRecoveryResponse(isOptionalNullableString(value.exportType));
  assertOpsRecoveryResponse(isOptionalFiniteNumber(value.limit));
  assertOpsRecoveryResponse(isOptionalFiniteNumber(value.days));
  assertOpsRecoveryResponse(isOptionalNullableString(value.mode));
  assertOpsRecoveryResponse(isOptionalNullableString(value.actor));
  assertOpsRecoveryResponse(isOptionalNullableString(value.eventType));
  assertOpsRecoveryResponse(isOptionalFiniteNumber(value.compareBreakdownLimit));
  assertOpsRecoveryResponse(isOptionalNullableString(value.compareBreakdownSort));
  assertOpsRecoveryResponse(isOptionalFiniteNumber(value.compareActorLimit));
  assertOpsRecoveryResponse(isOptionalNullableString(value.compareActorSort));
};

const assertRecoveryTaskCenterPaging = (value: unknown): void => {
  if (value === undefined || value === null) {
    return;
  }
  assertOpsRecoveryResponse(isRecord(value));
  assertOpsRecoveryResponse(isFiniteNumber(value.skipCount));
  assertOpsRecoveryResponse(isFiniteNumber(value.maxItems));
  assertOpsRecoveryResponse(isFiniteNumber(value.totalItems));
  assertOpsRecoveryResponse(typeof value.hasMoreItems === 'boolean');
};

const assertRecoveryHistoryExportAsyncCreateResponse = (
  value: unknown
): RecoveryHistoryExportAsyncCreateResponse => {
  assertOpsRecoveryResponse(isRecord(value));
  assertOpsRecoveryResponse(typeof value.taskId === 'string');
  assertOpsRecoveryResponse(typeof value.exportType === 'string');
  assertOpsRecoveryResponse(typeof value.status === 'string');
  assertRecoveryHistoryExportAsyncRequestSnapshot(value.request);
  assertOpsRecoveryResponse(isOptionalBoolean(value.deduplicated));
  assertOpsRecoveryResponse(isOptionalNullableString(value.deduplicatedFromTaskId));
  assertOpsRecoveryResponse(isOptionalNullableString(value.message));
  assertOpsRecoveryResponse(isNullableString(value.createdAt));
  assertOpsRecoveryResponse(isOptionalNullableString(value.timeoutAt));
  assertOpsRecoveryResponse(isOptionalNullableString(value.expiresAt));
  assertOpsRecoveryResponse(isOptionalNullableString(value.createdBy));
  assertOpsRecoveryResponse(isOptionalNullableString(value.updatedBy));
  return value as unknown as RecoveryHistoryExportAsyncCreateResponse;
};

const assertRecoveryHistoryExportAsyncTaskStatus = (
  value: unknown
): RecoveryHistoryExportAsyncTaskStatus => {
  assertOpsRecoveryResponse(isRecord(value));
  assertOpsRecoveryResponse(typeof value.taskId === 'string');
  assertOpsRecoveryResponse(typeof value.exportType === 'string');
  assertOpsRecoveryResponse(typeof value.status === 'string');
  assertOpsRecoveryResponse(isNullableString(value.error));
  assertRecoveryHistoryExportAsyncRequestSnapshot(value.request);
  assertOpsRecoveryResponse(isNullableString(value.createdAt));
  assertOpsRecoveryResponse(isOptionalNullableString(value.startedAt));
  assertOpsRecoveryResponse(isOptionalNullableString(value.updatedAt));
  assertOpsRecoveryResponse(isOptionalNullableString(value.timeoutAt));
  assertOpsRecoveryResponse(isOptionalNullableString(value.expiresAt));
  assertOpsRecoveryResponse(isNullableString(value.finishedAt));
  assertOpsRecoveryResponse(isNullableString(value.filename));
  assertOpsRecoveryResponse(isOptionalNullableString(value.createdBy));
  assertOpsRecoveryResponse(isOptionalNullableString(value.updatedBy));
  return value as unknown as RecoveryHistoryExportAsyncTaskStatus;
};

const assertRecoveryHistoryExportAsyncTaskList = (
  value: unknown
): RecoveryHistoryExportAsyncTaskList => {
  assertOpsRecoveryResponse(isRecord(value));
  assertOpsRecoveryResponse(isFiniteNumber(value.count));
  assertRecoveryTaskCenterPaging(value.paging);
  assertOpsRecoveryResponse(Array.isArray(value.items));
  const items = value.items.map(assertRecoveryHistoryExportAsyncTaskStatus);
  return {
    ...value,
    items,
  } as RecoveryHistoryExportAsyncTaskList;
};

const assertRecoveryHistoryExportAsyncTaskSummary = (
  value: unknown
): RecoveryHistoryExportAsyncTaskSummary => {
  assertOpsRecoveryResponse(isRecord(value));
  assertOpsRecoveryResponse(isFiniteNumber(value.totalCount));
  assertOpsRecoveryResponse(isFiniteNumber(value.queuedCount));
  assertOpsRecoveryResponse(isFiniteNumber(value.runningCount));
  assertOpsRecoveryResponse(isFiniteNumber(value.completedCount));
  assertOpsRecoveryResponse(isFiniteNumber(value.cancelledCount));
  assertOpsRecoveryResponse(isFiniteNumber(value.failedCount));
  assertOpsRecoveryResponse(isOptionalFiniteNumber(value.timedOutCount));
  assertOpsRecoveryResponse(isOptionalFiniteNumber(value.expiredCount));
  assertOpsRecoveryResponse(isFiniteNumber(value.activeCount));
  assertOpsRecoveryResponse(isFiniteNumber(value.terminalCount));
  return value as unknown as RecoveryHistoryExportAsyncTaskSummary;
};

const assertRecoveryHistoryExportAsyncTaskCleanupResponse = (
  value: unknown
): RecoveryHistoryExportAsyncTaskCleanupResponse => {
  assertOpsRecoveryResponse(isRecord(value));
  assertOpsRecoveryResponse(isFiniteNumber(value.deletedCount));
  assertOpsRecoveryResponse(isFiniteNumber(value.remainingCount));
  assertOpsRecoveryResponse(isOptionalNullableString(value.exportTypeFilter));
  assertOpsRecoveryResponse(isOptionalNullableString(value.statusFilter));
  assertOpsRecoveryResponse(typeof value.message === 'string');
  return value as unknown as RecoveryHistoryExportAsyncTaskCleanupResponse;
};

const assertRecoveryHistoryExportAsyncTaskCancelActiveResponse = (
  value: unknown
): RecoveryHistoryExportAsyncTaskCancelActiveResponse => {
  assertOpsRecoveryResponse(isRecord(value));
  assertOpsRecoveryResponse(isFiniteNumber(value.cancelledCount));
  assertOpsRecoveryResponse(isFiniteNumber(value.remainingActiveCount));
  assertOpsRecoveryResponse(isOptionalNullableString(value.exportTypeFilter));
  assertOpsRecoveryResponse(isOptionalNullableString(value.statusFilter));
  assertOpsRecoveryResponse(typeof value.message === 'string');
  return value as unknown as RecoveryHistoryExportAsyncTaskCancelActiveResponse;
};

const assertRecoveryHistoryExportAsyncRetryTerminalItem = (
  value: unknown
): RecoveryHistoryExportAsyncRetryTerminalItem => {
  assertOpsRecoveryResponse(isRecord(value));
  assertOpsRecoveryResponse(isOptionalNullableString(value.sourceTaskId));
  assertOpsRecoveryResponse(isOptionalNullableString(value.retriedTaskId));
  assertOpsRecoveryResponse(isOptionalNullableString(value.exportType));
  assertOpsRecoveryResponse(isOptionalNullableString(value.sourceStatus));
  assertOpsRecoveryResponse(isOptionalNullableString(value.outcome));
  assertOpsRecoveryResponse(isOptionalNullableString(value.message));
  return value as unknown as RecoveryHistoryExportAsyncRetryTerminalItem;
};

const assertRecoveryHistoryExportAsyncRetryTerminalResponse = (
  value: unknown
): RecoveryHistoryExportAsyncRetryTerminalResponse => {
  assertOpsRecoveryResponse(isRecord(value));
  assertOpsRecoveryResponse(isFiniteNumber(value.requested));
  assertOpsRecoveryResponse(isFiniteNumber(value.retried));
  assertOpsRecoveryResponse(isFiniteNumber(value.reused));
  assertOpsRecoveryResponse(isFiniteNumber(value.skipped));
  assertOpsRecoveryResponse(isFiniteNumber(value.failed));
  assertOpsRecoveryResponse(isFiniteNumber(value.limit));
  assertOpsRecoveryResponse(isOptionalNullableString(value.exportTypeFilter));
  assertOpsRecoveryResponse(isOptionalNullableString(value.statusFilter));
  assertOpsRecoveryResponse(typeof value.message === 'string');
  assertOpsRecoveryResponse(Array.isArray(value.results));
  const results = value.results.map(assertRecoveryHistoryExportAsyncRetryTerminalItem);
  return {
    ...value,
    results,
  } as RecoveryHistoryExportAsyncRetryTerminalResponse;
};

const assertRecoveryHistoryExportAsyncRetryTerminalDryRunItem = (
  value: unknown
): RecoveryHistoryExportAsyncRetryTerminalDryRunItem => {
  assertOpsRecoveryResponse(isRecord(value));
  assertOpsRecoveryResponse(isOptionalNullableString(value.sourceTaskId));
  assertOpsRecoveryResponse(isOptionalNullableString(value.exportType));
  assertOpsRecoveryResponse(isOptionalNullableString(value.sourceStatus));
  assertOpsRecoveryResponse(isOptionalNullableString(value.outcome));
  assertOpsRecoveryResponse(isOptionalNullableString(value.reasonCode));
  assertOpsRecoveryResponse(isOptionalNullableString(value.message));
  return value as unknown as RecoveryHistoryExportAsyncRetryTerminalDryRunItem;
};

const assertRecoveryHistoryExportAsyncRetryTerminalDryRunReasonCount = (
  value: unknown
): RecoveryHistoryExportAsyncRetryTerminalDryRunReasonCount => {
  assertOpsRecoveryResponse(isRecord(value));
  assertOpsRecoveryResponse(isOptionalNullableString(value.reasonCode));
  assertOpsRecoveryResponse(isOptionalNullableString(value.outcome));
  assertOpsRecoveryResponse(isFiniteNumber(value.count));
  return value as unknown as RecoveryHistoryExportAsyncRetryTerminalDryRunReasonCount;
};

const assertRecoveryHistoryExportAsyncRetryTerminalDryRunResponse = (
  value: unknown
): RecoveryHistoryExportAsyncRetryTerminalDryRunResponse => {
  assertOpsRecoveryResponse(isRecord(value));
  assertOpsRecoveryResponse(isFiniteNumber(value.requested));
  assertOpsRecoveryResponse(isFiniteNumber(value.retryable));
  assertOpsRecoveryResponse(isFiniteNumber(value.skipped));
  assertOpsRecoveryResponse(isFiniteNumber(value.limit));
  assertOpsRecoveryResponse(isOptionalNullableString(value.exportTypeFilter));
  assertOpsRecoveryResponse(isOptionalNullableString(value.statusFilter));
  assertOpsRecoveryResponse(typeof value.message === 'string');
  assertOpsRecoveryResponse(Array.isArray(value.results));
  const results = value.results.map(assertRecoveryHistoryExportAsyncRetryTerminalDryRunItem);
  if (value.reasonBreakdown === undefined) {
    return {
      ...value,
      results,
    } as RecoveryHistoryExportAsyncRetryTerminalDryRunResponse;
  }
  assertOpsRecoveryResponse(Array.isArray(value.reasonBreakdown));
  const reasonBreakdown = value.reasonBreakdown.map(
    assertRecoveryHistoryExportAsyncRetryTerminalDryRunReasonCount
  );
  return {
    ...value,
    results,
    reasonBreakdown,
  } as RecoveryHistoryExportAsyncRetryTerminalDryRunResponse;
};

class OpsRecoveryService {
  async queueByReason(payload: QueueByReasonRequest): Promise<RecoveryBatchResult> {
    const result = await api.post<unknown>('/ops/recovery/queue-by-reason', payload);
    return assertRecoveryBatchResult(result);
  }

  async queueByWindow(payload: QueueByWindowRequest): Promise<RecoveryBatchResult> {
    const result = await api.post<unknown>('/ops/recovery/queue-by-window', payload);
    return assertRecoveryBatchResult(result);
  }

  async replayBatch(payload: ReplayBatchRequest): Promise<RecoveryBatchResult> {
    const result = await api.post<unknown>('/ops/recovery/replay-batch', payload);
    return assertRecoveryBatchResult(result);
  }

  async clearBatch(payload: ClearBatchRequest): Promise<RecoveryBatchResult> {
    const result = await api.post<unknown>('/ops/recovery/clear-batch', payload);
    return assertRecoveryBatchResult(result);
  }

  async clearByFilter(payload: ClearByFilterRequest): Promise<RecoveryBatchResult> {
    const result = await api.post<unknown>('/ops/recovery/clear-by-filter', payload);
    return assertRecoveryBatchResult(result);
  }

  async replayByFilter(payload: ReplayByFilterRequest): Promise<RecoveryBatchResult> {
    const result = await api.post<unknown>('/ops/recovery/replay-by-filter', payload);
    return assertRecoveryBatchResult(result);
  }

  async dryRun(payload: RecoveryDryRunRequest): Promise<RecoveryDryRunResult> {
    const result = await api.post<unknown>('/ops/recovery/dry-run', payload);
    return assertRecoveryDryRunResult(result);
  }

  async exportDryRunCsv(payload: RecoveryDryRunRequest): Promise<Blob> {
    return api.post<Blob>('/ops/recovery/dry-run/export', payload, {
      responseType: 'blob',
    });
  }

  async getHistory(
    limit = 20,
    days = 7,
    mode?: RecoveryHistoryMode,
    page = 0,
    actor?: string,
    eventType?: RecoveryHistoryEventType
  ): Promise<RecoveryHistoryResult> {
    const result = await api.get<unknown>('/ops/recovery/history', {
      params: {
        limit,
        days,
        page,
        mode: mode || undefined,
        actor: actor || undefined,
        eventType: eventType || undefined,
      },
    });
    return assertRecoveryHistoryResult(result);
  }

  async getHistorySummary(
    days = 7,
    mode?: RecoveryHistoryMode,
    actor?: string,
    eventType?: RecoveryHistoryEventType
  ): Promise<RecoveryHistorySummaryResult> {
    const result = await api.get<unknown>('/ops/recovery/history/summary', {
      params: {
        days,
        mode: mode || undefined,
        actor: actor || undefined,
        eventType: eventType || undefined,
      },
    });
    return assertRecoveryHistorySummaryResult(result);
  }

  async getHistorySummaryTrend(
    days = 7,
    mode?: RecoveryHistoryMode,
    actor?: string,
    eventType?: RecoveryHistoryEventType
  ): Promise<RecoveryHistoryTrendResult> {
    const result = await api.get<unknown>('/ops/recovery/history/summary/trend', {
      params: {
        days,
        mode: mode || undefined,
        actor: actor || undefined,
        eventType: eventType || undefined,
      },
    });
    return assertRecoveryHistoryTrendResult(result);
  }

  async getHistorySummaryCompare(
    days = 7,
    mode?: RecoveryHistoryMode,
    actor?: string,
    eventType?: RecoveryHistoryEventType
  ): Promise<RecoveryHistorySummaryCompareResult> {
    const result = await api.get<unknown>('/ops/recovery/history/summary/compare', {
      params: {
        days,
        mode: mode || undefined,
        actor: actor || undefined,
        eventType: eventType || undefined,
      },
    });
    return assertRecoveryHistorySummaryCompareResult(result);
  }

  async getHistorySummaryCompareBreakdown(
    days = 7,
    mode?: RecoveryHistoryMode,
    actor?: string,
    eventType?: RecoveryHistoryEventType,
    limit = 10,
    sort: RecoveryHistoryCompareBreakdownSort = 'DELTA_ABS_DESC'
  ): Promise<RecoveryHistorySummaryCompareBreakdownResult> {
    const result = await api.get<unknown>('/ops/recovery/history/summary/compare/breakdown', {
      params: {
        days,
        mode: mode || undefined,
        actor: actor || undefined,
        eventType: eventType || undefined,
        limit,
        sort,
      },
    });
    return assertRecoveryHistorySummaryCompareBreakdownResult(result);
  }

  async getHistorySummaryCompareActors(
    days = 7,
    mode?: RecoveryHistoryMode,
    actor?: string,
    eventType?: RecoveryHistoryEventType,
    limit = 10,
    sort: RecoveryHistoryCompareActorSort = 'DELTA_ABS_DESC'
  ): Promise<RecoveryHistorySummaryCompareActorsResult> {
    const result = await api.get<unknown>('/ops/recovery/history/summary/compare/actors', {
      params: {
        days,
        mode: mode || undefined,
        actor: actor || undefined,
        eventType: eventType || undefined,
        limit,
        sort,
      },
    });
    return assertRecoveryHistorySummaryCompareActorsResult(result);
  }

  async exportHistorySummaryCompareActorsCsv(
    days = 7,
    mode?: RecoveryHistoryMode,
    actor?: string,
    eventType?: RecoveryHistoryEventType,
    limit = 10,
    sort: RecoveryHistoryCompareActorSort = 'DELTA_ABS_DESC'
  ): Promise<void> {
    const filename = `ops_recovery_history_compare_actors_${new Date().toISOString().replace(/[:.]/g, '-')}.csv`;
    return api.downloadFile('/ops/recovery/history/summary/compare/actors/export', filename, {
      params: {
        days,
        mode: mode || undefined,
        actor: actor || undefined,
        eventType: eventType || undefined,
        limit,
        sort,
      },
    });
  }

  async exportHistoryCsv(
    limit = 500,
    days = 7,
    mode?: RecoveryHistoryMode,
    actor?: string,
    eventType?: RecoveryHistoryEventType
  ): Promise<void> {
    const filename = `ops_recovery_history_${new Date().toISOString().replace(/[:.]/g, '-')}.csv`;
    return api.downloadFile('/ops/recovery/history/export', filename, {
      params: {
        limit,
        days,
        mode: mode || undefined,
        actor: actor || undefined,
        eventType: eventType || undefined,
      },
    });
  }

  async startHistoryExportAsync(
    payload: RecoveryHistoryExportAsyncRequest
  ): Promise<RecoveryHistoryExportAsyncCreateResponse> {
    const result = await api.post<unknown>('/ops/recovery/history/export-async', payload);
    return assertRecoveryHistoryExportAsyncCreateResponse(result);
  }

  async listHistoryExportAsyncTasks(
    maxItems = 20,
    exportType?: RecoveryHistoryExportAsyncType,
    status?: RecoveryHistoryExportAsyncStatusFilter,
    skipCount = 0
  ): Promise<RecoveryHistoryExportAsyncTaskList> {
    const result = await api.get<unknown>('/ops/recovery/history/export-async', {
      params: {
        maxItems,
        limit: maxItems,
        skipCount: Math.max(0, Math.floor(skipCount)),
        exportType: exportType || undefined,
        status: status || undefined,
      },
    });
    return assertRecoveryHistoryExportAsyncTaskList(result);
  }

  async getHistoryExportAsyncTask(taskId: string): Promise<RecoveryHistoryExportAsyncTaskStatus> {
    const result = await api.get<unknown>(
      `/ops/recovery/history/export-async/${encodeURIComponent(taskId)}`
    );
    return assertRecoveryHistoryExportAsyncTaskStatus(result);
  }

  async getHistoryExportAsyncTaskSummary(): Promise<RecoveryHistoryExportAsyncTaskSummary> {
    const result = await api.get<unknown>('/ops/recovery/history/export-async/summary');
    return assertRecoveryHistoryExportAsyncTaskSummary(result);
  }

  async getHistoryExportAsyncTaskSummaryFiltered(
    exportType?: RecoveryHistoryExportAsyncType,
    status?: RecoveryHistoryExportAsyncStatusFilter
  ): Promise<RecoveryHistoryExportAsyncTaskSummary> {
    const result = await api.get<unknown>('/ops/recovery/history/export-async/summary', {
      params: {
        exportType: exportType || undefined,
        status: status || undefined,
      },
    });
    return assertRecoveryHistoryExportAsyncTaskSummary(result);
  }

  async cancelHistoryExportAsyncTask(taskId: string): Promise<RecoveryHistoryExportAsyncTaskStatus> {
    const result = await api.post<unknown>(
      `/ops/recovery/history/export-async/${encodeURIComponent(taskId)}/cancel`
    );
    return assertRecoveryHistoryExportAsyncTaskStatus(result);
  }

  async retryHistoryExportAsyncTask(taskId: string): Promise<RecoveryHistoryExportAsyncCreateResponse> {
    const result = await api.post<unknown>(
      `/ops/recovery/history/export-async/${encodeURIComponent(taskId)}/retry`
    );
    return assertRecoveryHistoryExportAsyncCreateResponse(result);
  }

  async retryTerminalHistoryExportAsyncTasks(
    exportType?: RecoveryHistoryExportAsyncType,
    status?: RecoveryHistoryExportAsyncTerminalStatusFilter,
    limit = 20
  ): Promise<RecoveryHistoryExportAsyncRetryTerminalResponse> {
    const result = await api.post<unknown>(
      '/ops/recovery/history/export-async/retry-terminal',
      {},
      {
        params: {
          exportType: exportType || undefined,
          status: status || undefined,
          limit,
        },
      }
    );
    return assertRecoveryHistoryExportAsyncRetryTerminalResponse(result);
  }

  async dryRunRetryTerminalHistoryExportAsyncTasks(
    exportType?: RecoveryHistoryExportAsyncType,
    status?: RecoveryHistoryExportAsyncTerminalStatusFilter,
    limit = 20
  ): Promise<RecoveryHistoryExportAsyncRetryTerminalDryRunResponse> {
    const result = await api.post<unknown>(
      '/ops/recovery/history/export-async/retry-terminal/dry-run',
      {},
      {
        params: {
          exportType: exportType || undefined,
          status: status || undefined,
          limit,
        },
      }
    );
    return assertRecoveryHistoryExportAsyncRetryTerminalDryRunResponse(result);
  }

  async exportDryRunRetryTerminalHistoryExportAsyncTasks(
    exportType?: RecoveryHistoryExportAsyncType,
    status?: RecoveryHistoryExportAsyncTerminalStatusFilter,
    limit = 20
  ): Promise<void> {
    const filename = `ops_recovery_history_async_retry_dry_run_${new Date().toISOString().replace(/[:.]/g, '-')}.csv`;
    return api.downloadFile('/ops/recovery/history/export-async/retry-terminal/dry-run/export', filename, {
      params: {
        exportType: exportType || undefined,
        status: status || undefined,
        limit,
      },
    });
  }

  async retryTerminalHistoryExportAsyncTasksByTaskIds(
    sourceTaskIds: string[],
    exportType?: RecoveryHistoryExportAsyncType
  ): Promise<RecoveryHistoryExportAsyncRetryTerminalResponse> {
    const payload: RecoveryHistoryExportAsyncRetryTerminalByTaskIdsRequest = {
      sourceTaskIds: Array.from(new Set((sourceTaskIds || [])
        .map((taskId) => String(taskId || '').trim())
        .filter((taskId) => taskId.length > 0))),
    };
    const result = await api.post<unknown>(
      '/ops/recovery/history/export-async/retry-terminal/by-task-ids',
      payload,
      {
        params: {
          exportType: exportType || undefined,
        },
      }
    );
    return assertRecoveryHistoryExportAsyncRetryTerminalResponse(result);
  }

  async cancelActiveHistoryExportAsyncTasks(
    exportType?: RecoveryHistoryExportAsyncType,
    status?: RecoveryHistoryExportAsyncActiveStatusFilter
  ): Promise<RecoveryHistoryExportAsyncTaskCancelActiveResponse> {
    const result = await api.post<unknown>(
      '/ops/recovery/history/export-async/cancel-active',
      {},
      {
        params: {
          exportType: exportType || undefined,
          status: status || undefined,
        },
      }
    );
    return assertRecoveryHistoryExportAsyncTaskCancelActiveResponse(result);
  }

  async cleanupHistoryExportAsyncTasks(
    exportType?: RecoveryHistoryExportAsyncType,
    status?: RecoveryHistoryExportAsyncStatusFilter
  ): Promise<RecoveryHistoryExportAsyncTaskCleanupResponse> {
    const result = await api.post<unknown>(
      '/ops/recovery/history/export-async/cleanup',
      {},
      {
        params: {
          exportType: exportType || undefined,
          status: status || undefined,
        },
      }
    );
    return assertRecoveryHistoryExportAsyncTaskCleanupResponse(result);
  }

  async downloadHistoryExportAsyncTask(taskId: string): Promise<Blob> {
    return api.getBlob(`/ops/recovery/history/export-async/${encodeURIComponent(taskId)}/download`);
  }

  async exportHistorySummaryCsv(
    days = 7,
    mode?: RecoveryHistoryMode,
    actor?: string,
    eventType?: RecoveryHistoryEventType
  ): Promise<void> {
    const filename = `ops_recovery_history_summary_${new Date().toISOString().replace(/[:.]/g, '-')}.csv`;
    return api.downloadFile('/ops/recovery/history/summary/export', filename, {
      params: {
        days,
        mode: mode || undefined,
        actor: actor || undefined,
        eventType: eventType || undefined,
      },
    });
  }

  async exportHistorySummaryTrendCsv(
    days = 7,
    mode?: RecoveryHistoryMode,
    actor?: string,
    eventType?: RecoveryHistoryEventType
  ): Promise<void> {
    const filename = `ops_recovery_history_trend_${new Date().toISOString().replace(/[:.]/g, '-')}.csv`;
    return api.downloadFile('/ops/recovery/history/summary/trend/export', filename, {
      params: {
        days,
        mode: mode || undefined,
        actor: actor || undefined,
        eventType: eventType || undefined,
      },
    });
  }

  async exportHistorySummaryCompareCsv(
    days = 7,
    mode?: RecoveryHistoryMode,
    actor?: string,
    eventType?: RecoveryHistoryEventType
  ): Promise<void> {
    const filename = `ops_recovery_history_compare_${new Date().toISOString().replace(/[:.]/g, '-')}.csv`;
    return api.downloadFile('/ops/recovery/history/summary/compare/export', filename, {
      params: {
        days,
        mode: mode || undefined,
        actor: actor || undefined,
        eventType: eventType || undefined,
      },
    });
  }

  async exportHistorySummaryCompareBreakdownCsv(
    days = 7,
    mode?: RecoveryHistoryMode,
    actor?: string,
    eventType?: RecoveryHistoryEventType,
    limit = 10,
    sort: RecoveryHistoryCompareBreakdownSort = 'DELTA_ABS_DESC'
  ): Promise<void> {
    const filename = `ops_recovery_history_compare_breakdown_${new Date().toISOString().replace(/[:.]/g, '-')}.csv`;
    return api.downloadFile('/ops/recovery/history/summary/compare/breakdown/export', filename, {
      params: {
        days,
        mode: mode || undefined,
        actor: actor || undefined,
        eventType: eventType || undefined,
        limit,
        sort,
      },
    });
  }

}

const opsRecoveryService = new OpsRecoveryService();
export default opsRecoveryService;
