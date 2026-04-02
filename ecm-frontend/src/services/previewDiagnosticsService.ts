import api from './api';

export type PreviewFailureSample = {
  id: string;
  name: string;
  path: string;
  mimeType: string;
  previewStatus: string | null;
  previewFailureReason: string | null;
  previewFailureCategory: string | null;
  previewLastUpdated: string | null;
};

export type PreviewFailureStatusCount = {
  status: string;
  count: number;
};

export type PreviewFailureCategoryCount = {
  category: string;
  retryable: boolean;
  count: number;
};

export type PreviewFailureReasonCount = {
  reason: string;
  category: string;
  retryable: boolean;
  count: number;
};

export type PreviewFailureSummary = {
  totalFailures: number;
  sampledFailures: number;
  sampleLimit: number;
  windowDays: number;
  windowStart: string | null;
  sampleTruncated: boolean;
  confidenceLevel: 'HIGH' | 'LOW' | string;
  confidenceReason: 'sample_complete' | 'sample_truncated' | string;
  statusCounts: PreviewFailureStatusCount[];
  categoryCounts: PreviewFailureCategoryCount[];
  topReasons: PreviewFailureReasonCount[];
};

export type PreviewFailureLedgerItem = {
  documentId: string;
  name: string | null;
  path: string | null;
  mimeType: string | null;
  previewStatus: string | null;
  failureCount: number;
  failedAt: string | null;
  lastReason: string | null;
  category: string | null;
  retryable: boolean;
  previewLastUpdated: string | null;
  failureContentHash: string | null;
  currentContentHash: string | null;
  staleByContentChange: boolean;
};

export type PreviewFailureLedgerDiagnostics = {
  totalEntries: number;
  sampledEntries: number;
  limit: number;
  windowDays: number;
  windowStart: string | null;
  sampleTruncated: boolean;
  items: PreviewFailureLedgerItem[];
};

export type PreviewFailureLedgerResetItem = {
  documentId: string | null;
  name: string | null;
  previousFailureCount: number;
  previousFailedAt: string | null;
  previousReason: string | null;
  outcome: 'RESET' | 'SKIPPED' | 'FAILED' | string;
  message: string | null;
};

export type PreviewFailureLedgerResetBatchResult = {
  requested: number;
  deduplicated: number;
  reset: number;
  failed: number;
  results: PreviewFailureLedgerResetItem[];
};

export type PreviewFailureLedgerResetByFilterRequest = {
  reason: string;
  category?: string;
  retryable?: boolean;
  maxDocuments?: number;
  days?: number;
};

export type PreviewFailureLedgerResetByFilterResult = {
  reason: string;
  category: string;
  retryable: boolean | null;
  windowDays: number;
  maxDocuments: number;
  totalCandidates: number;
  scanned: number;
  matched: number;
  truncated: boolean;
  reset: number;
  skipped: number;
  failed: number;
  results: PreviewFailureLedgerResetItem[];
};

export type PreviewRenditionStatusCount = {
  status: string;
  count: number;
};

export type PreviewRenditionReasonCount = {
  reason: string;
  count: number;
};

export type PreviewRenditionSummary = {
  totalResources: number;
  sampledResources: number;
  sampleLimit: number;
  windowDays: number;
  windowStart: string | null;
  sampleTruncated: boolean;
  statusCounts: PreviewRenditionStatusCount[];
  topReasons: PreviewRenditionReasonCount[];
};

export type PreviewRenditionResource = {
  documentId?: string | null;
  name: string | null;
  path?: string | null;
  status: string | null;
  mimeType: string | null;
  reason: string | null;
  category?: string | null;
  previewStatus?: string | null;
  updatedAt: string | null;
};

type PreviewRenditionResourceApiItem = {
  documentId?: string | null;
  name?: string | null;
  path?: string | null;
  mimeType?: string | null;
  previewStatus?: string | null;
  renditionStatus?: string | null;
  previewFailureReason?: string | null;
  previewFailureCategory?: string | null;
  previewLastUpdated?: string | null;
  status?: string | null;
  reason?: string | null;
  updatedAt?: string | null;
  category?: string | null;
};

type PreviewRenditionResourcesDiagnostics = {
  totalResources: number;
  sampledResources: number;
  limit: number;
  windowDays: number;
  windowStart: string | null;
  sampleTruncated: boolean;
  items: PreviewRenditionResourceApiItem[];
};

export type PreviewRenditionResourcesExportTask = {
  taskId: string;
  status?: string | null;
  error?: string | null;
  message?: string | null;
  deduplicated?: boolean;
  deduplicatedFromTaskId?: string | null;
  createdAt?: string | null;
  startedAt?: string | null;
  updatedAt?: string | null;
  timeoutAt?: string | null;
  expiresAt?: string | null;
  createdBy?: string | null;
  updatedBy?: string | null;
  finishedAt?: string | null;
  filename?: string | null;
};

export type PreviewRenditionResourcesExportTaskStatusFilter =
  | 'QUEUED'
  | 'RUNNING'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'FAILED'
  | 'TIMED_OUT'
  | 'EXPIRED';

export type PreviewRenditionResourcesExportTaskActiveStatusFilter =
  | 'QUEUED'
  | 'RUNNING';

export type PreviewRenditionResourcesExportTaskTerminalStatusFilter =
  | 'COMPLETED'
  | 'CANCELLED'
  | 'FAILED'
  | 'TIMED_OUT'
  | 'EXPIRED';

export type PreviewRenditionResourcesExportTaskList = {
  count: number;
  paging?: PreviewTaskCenterPaging | null;
  items: PreviewRenditionResourcesExportTask[];
};

export type PreviewRenditionResourcesExportTaskSummary = {
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

export type PreviewRenditionResourcesExportTaskCleanupResponse = {
  deletedCount: number;
  remainingCount: number;
  statusFilter?: string | null;
  message: string;
};

export type PreviewRenditionResourcesExportTaskCancelActiveResponse = {
  cancelledCount: number;
  remainingActiveCount: number;
  statusFilter?: string | null;
  message: string;
};

export type PreviewRenditionResourcesExportTaskRetryTerminalItem = {
  sourceTaskId?: string | null;
  newTaskId?: string | null;
  sourceStatus?: string | null;
  outcome?: string | null;
  message?: string | null;
};

export type PreviewRenditionResourcesExportTaskRetryTerminalResponse = {
  requested: number;
  retried: number;
  reused: number;
  skipped: number;
  failed: number;
  limit: number;
  statusFilter?: string | null;
  message: string;
  results: PreviewRenditionResourcesExportTaskRetryTerminalItem[];
};

export type PreviewRenditionResourcesExportTaskRetryTerminalDryRunItem = {
  sourceTaskId?: string | null;
  sourceStatus?: string | null;
  outcome?: string | null;
  reasonCode?: string | null;
  message?: string | null;
};

export type PreviewRenditionResourcesExportTaskRetryTerminalDryRunReasonCount = {
  reasonCode?: string | null;
  outcome?: string | null;
  count: number;
};

export type PreviewRenditionResourcesExportTaskRetryTerminalDryRunResponse = {
  requested: number;
  retryable: number;
  skipped: number;
  limit: number;
  statusFilter?: string | null;
  message: string;
  results: PreviewRenditionResourcesExportTaskRetryTerminalDryRunItem[];
  reasonBreakdown?: PreviewRenditionResourcesExportTaskRetryTerminalDryRunReasonCount[];
};

export type PreviewRenditionResourcesExportTaskRetryTerminalByTaskIdsRequest = {
  sourceTaskIds: string[];
};

export type PreviewQueueBatchItem = {
  documentId: string;
  outcome: 'QUEUED' | 'SKIPPED' | 'FAILED' | string;
  message: string | null;
  previewStatus: string | null;
  previewFailureReason?: string | null;
  previewFailureCategory?: string | null;
  previewLastUpdated?: string | null;
  attempts: number;
  nextAttemptAt: string | null;
};

export type PreviewQueueBatchResult = {
  requested: number;
  deduplicated: number;
  queued: number;
  skipped: number;
  failed: number;
  results: PreviewQueueBatchItem[];
};

export type PreviewQueueDiagnosticsItem = {
  documentId: string;
  name: string | null;
  path: string | null;
  mimeType: string | null;
  previewStatus: string | null;
  previewFailureReason?: string | null;
  previewFailureCategory?: string | null;
  previewLastUpdated?: string | null;
  queueState: 'QUEUED' | 'RUNNING' | 'CANCEL_REQUESTED' | string;
  governanceKey: string | null;
  attempts: number;
  nextAttemptAt: string | null;
  running: boolean;
  cancelRequested: boolean;
};

export type PreviewQueueDiagnosticsStateFilter = 'ALL' | 'QUEUED' | 'RUNNING' | 'CANCEL_REQUESTED';

export type PreviewQueueDiagnosticsSummary = {
  backend: string;
  queueEnabled: boolean;
  scheduledCount: number;
  governanceCount: number;
  runningCount: number;
  runningCountAccurate: boolean;
  cancellationRequestedCount: number;
  sampleLimit: number;
  sampleTruncated: boolean;
  stateFilter: PreviewQueueDiagnosticsStateFilter;
  queryFilter: string | null;
  totalSampledItems: number;
  filteredSampledItems: number;
  items: PreviewQueueDiagnosticsItem[];
};

export type PreviewQueueDeclinedItem = {
  documentId: string | null;
  name: string | null;
  path: string | null;
  mimeType: string | null;
  previewStatus: string | null;
  previewFailureReason?: string | null;
  previewFailureCategory?: string | null;
  previewLastUpdated?: string | null;
  reason: string | null;
  category: string | null;
  governanceKey: string | null;
  declinedAt: string | null;
  nextEligibleAt: string | null;
  forceRequired: boolean;
};

export type PreviewQueueDeclinedForceRequiredFilter = 'ANY' | 'YES' | 'NO';
export type PreviewQueueDeclinedWindowHours = 0 | 1 | 6 | 24 | 168;

export type PreviewQueueDeclinedCategoryCount = {
  category: string;
  count: number;
  forceRequiredCount: number;
};

export type PreviewQueueDeclinedSummary = {
  queueEnabled: boolean;
  totalDeclined: number;
  sampleLimit: number;
  sampleTruncated: boolean;
  categoryFilter: string;
  forceRequiredFilter: PreviewQueueDeclinedForceRequiredFilter;
  windowHoursFilter: number | null;
  windowHours?: number;
  queryFilter: string | null;
  forceRequiredCount: number;
  categoryCounts: PreviewQueueDeclinedCategoryCount[];
  totalSampledItems: number;
  filteredSampledItems: number;
  items: PreviewQueueDeclinedItem[];
};

export type PreviewQueueDeclinedRequeueItem = {
  documentId: string | null;
  category: string | null;
  outcome: 'QUEUED' | 'SKIPPED' | 'FAILED' | string;
  message: string | null;
  previewStatus: string | null;
  previewFailureReason?: string | null;
  previewFailureCategory?: string | null;
  previewLastUpdated?: string | null;
};

export type PreviewQueueDeclinedRequeueResult = {
  categoryFilter: string;
  forceRequiredFilter: PreviewQueueDeclinedForceRequiredFilter;
  windowHoursFilter: number | null;
  windowHours?: number;
  queryFilter: string | null;
  limit: number;
  force: boolean;
  requested: number;
  queued: number;
  skipped: number;
  failed: number;
  results: PreviewQueueDeclinedRequeueItem[];
};

export type PreviewQueueDeclinedRequeueDryRunItem = {
  documentId: string | null;
  category: string | null;
  outcome: 'QUEUED' | 'SKIPPED' | 'FAILED' | string;
  reasonCode: string | null;
  message: string | null;
  previewStatus: string | null;
  previewFailureReason?: string | null;
  previewFailureCategory?: string | null;
  previewLastUpdated?: string | null;
  nextAttemptAt: string | null;
  preflightStatus?: string | null;
  preflightSkipReason?: string | null;
  preflightRoute?: string | null;
  preflightPolicyProfile?: string | null;
  preflightPipeline?: string | null;
};

export type PreviewQueueDeclinedRequeueDryRunReasonCount = {
  reasonCode: string | null;
  count: number;
};

export type PreviewQueueDeclinedRequeueDryRunResult = {
  categoryFilter: string;
  forceRequiredFilter: PreviewQueueDeclinedForceRequiredFilter;
  windowHoursFilter: number | null;
  windowHours?: number;
  queryFilter: string | null;
  limit: number;
  force: boolean;
  requested: number;
  estimatedQueued: number;
  estimatedSkipped: number;
  estimatedFailed: number;
  results: PreviewQueueDeclinedRequeueDryRunItem[];
  reasonBreakdown: PreviewQueueDeclinedRequeueDryRunReasonCount[];
};

export type PreviewQueueDeclinedClearItem = {
  documentId: string | null;
  category: string | null;
  outcome: 'CLEARED' | 'SKIPPED' | 'FAILED' | string;
  message: string | null;
};

export type PreviewQueueDeclinedClearResult = {
  categoryFilter: string;
  forceRequiredFilter: PreviewQueueDeclinedForceRequiredFilter;
  windowHoursFilter: number | null;
  windowHours?: number;
  queryFilter: string | null;
  limit: number;
  requested: number;
  cleared: number;
  skipped: number;
  failed: number;
  results: PreviewQueueDeclinedClearItem[];
};

export type PreviewQueueDeclinedExportTask = {
  taskId: string;
  status?: string | null;
  error?: string | null;
  message?: string | null;
  deduplicated?: boolean | null;
  deduplicatedFromTaskId?: string | null;
  createdAt?: string | null;
  startedAt?: string | null;
  updatedAt?: string | null;
  timeoutAt?: string | null;
  expiresAt?: string | null;
  createdBy?: string | null;
  updatedBy?: string | null;
  finishedAt?: string | null;
  filename?: string | null;
  categoryFilter?: string | null;
  forceRequiredFilter?: PreviewQueueDeclinedForceRequiredFilter | string | null;
  queryFilter?: string | null;
  windowHoursFilter?: number | null;
  limit?: number | null;
};

export type PreviewQueueDeclinedExportTaskStatusFilter =
  | 'QUEUED'
  | 'RUNNING'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'FAILED'
  | 'TIMED_OUT'
  | 'EXPIRED';

export type PreviewQueueDeclinedExportTaskTerminalStatusFilter =
  | 'COMPLETED'
  | 'CANCELLED'
  | 'FAILED'
  | 'TIMED_OUT'
  | 'EXPIRED';

export type PreviewQueueDeclinedExportTaskActiveStatusFilter =
  | 'QUEUED'
  | 'RUNNING';

export type PreviewQueueDeclinedExportTaskList = {
  count: number;
  paging?: PreviewTaskCenterPaging | null;
  items: PreviewQueueDeclinedExportTask[];
};

export type PreviewQueueDeclinedExportTaskSummary = {
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

export type PreviewQueueDeclinedExportTaskCleanupResponse = {
  deletedCount: number;
  remainingCount: number;
  statusFilter?: string | null;
  message: string;
};

export type PreviewQueueDeclinedExportTaskCancelActiveResponse = {
  cancelledCount: number;
  remainingActiveCount: number;
  statusFilter?: string | null;
  message: string;
};

export type PreviewQueueDeclinedExportTaskRetryTerminalItem = {
  sourceTaskId?: string | null;
  newTaskId?: string | null;
  sourceStatus?: string | null;
  outcome?: string | null;
  message?: string | null;
};

export type PreviewQueueDeclinedExportTaskRetryTerminalResponse = {
  requested: number;
  retried: number;
  reused: number;
  skipped: number;
  failed: number;
  limit: number;
  statusFilter?: string | null;
  message: string;
  results: PreviewQueueDeclinedExportTaskRetryTerminalItem[];
};

export type PreviewQueueDeclinedExportTaskRetryTerminalDryRunItem = {
  sourceTaskId?: string | null;
  sourceStatus?: string | null;
  outcome?: string | null;
  reasonCode?: string | null;
  message?: string | null;
};

export type PreviewQueueDeclinedExportTaskRetryTerminalDryRunReasonCount = {
  reasonCode?: string | null;
  outcome?: string | null;
  count: number;
};

export type PreviewQueueDeclinedExportTaskRetryTerminalDryRunResponse = {
  requested: number;
  retryable: number;
  skipped: number;
  limit: number;
  statusFilter?: string | null;
  message: string;
  results: PreviewQueueDeclinedExportTaskRetryTerminalDryRunItem[];
  reasonBreakdown?: PreviewQueueDeclinedExportTaskRetryTerminalDryRunReasonCount[];
};

export type PreviewQueueDeclinedExportTaskRetryTerminalByTaskIdsRequest = {
  sourceTaskIds: string[];
};

export type PreviewQueueDeclinedRequeueDryRunExportTask = {
  taskId: string;
  status?: string | null;
  error?: string | null;
  message?: string | null;
  deduplicated?: boolean | null;
  deduplicatedFromTaskId?: string | null;
  createdAt?: string | null;
  startedAt?: string | null;
  updatedAt?: string | null;
  timeoutAt?: string | null;
  expiresAt?: string | null;
  createdBy?: string | null;
  updatedBy?: string | null;
  finishedAt?: string | null;
  filename?: string | null;
  categoryFilter?: string | null;
  forceRequiredFilter?: PreviewQueueDeclinedForceRequiredFilter | string | null;
  queryFilter?: string | null;
  windowHoursFilter?: number | null;
  limit?: number | null;
  force?: boolean | null;
};

export type PreviewQueueDeclinedRequeueDryRunExportTaskStatusFilter =
  | 'QUEUED'
  | 'RUNNING'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'FAILED'
  | 'TIMED_OUT'
  | 'EXPIRED';

export type PreviewQueueDeclinedRequeueDryRunExportTaskActiveStatusFilter =
  | 'QUEUED'
  | 'RUNNING';

export type PreviewQueueDeclinedRequeueDryRunExportTaskTerminalStatusFilter =
  | 'COMPLETED'
  | 'CANCELLED'
  | 'FAILED'
  | 'TIMED_OUT'
  | 'EXPIRED';

export type PreviewQueueDeclinedRequeueDryRunExportTaskRetryTerminalItem = {
  sourceTaskId?: string | null;
  newTaskId?: string | null;
  sourceStatus?: string | null;
  outcome?: string | null;
  message?: string | null;
};

export type PreviewQueueDeclinedRequeueDryRunExportTaskRetryTerminalResponse = {
  requested: number;
  retried: number;
  reused: number;
  skipped: number;
  failed: number;
  limit: number;
  statusFilter?: string | null;
  message: string;
  results: PreviewQueueDeclinedRequeueDryRunExportTaskRetryTerminalItem[];
};

export type PreviewQueueDeclinedRequeueDryRunExportTaskRetryTerminalDryRunItem = {
  sourceTaskId?: string | null;
  sourceStatus?: string | null;
  outcome?: string | null;
  reasonCode?: string | null;
  message?: string | null;
};

export type PreviewQueueDeclinedRequeueDryRunExportTaskRetryTerminalDryRunReasonCount = {
  reasonCode?: string | null;
  outcome?: string | null;
  count: number;
};

export type PreviewQueueDeclinedRequeueDryRunExportTaskRetryTerminalDryRunResponse = {
  requested: number;
  retryable: number;
  skipped: number;
  limit: number;
  statusFilter?: string | null;
  message: string;
  results: PreviewQueueDeclinedRequeueDryRunExportTaskRetryTerminalDryRunItem[];
  reasonBreakdown?: PreviewQueueDeclinedRequeueDryRunExportTaskRetryTerminalDryRunReasonCount[];
};

export type PreviewQueueDeclinedRequeueDryRunExportTaskRetryTerminalByTaskIdsRequest = {
  sourceTaskIds: string[];
};

export type PreviewQueueDeclinedRequeueDryRunExportTaskList = {
  count: number;
  paging?: PreviewTaskCenterPaging | null;
  items: PreviewQueueDeclinedRequeueDryRunExportTask[];
};

export type PreviewTaskCenterPaging = {
  skipCount: number;
  maxItems: number;
  totalItems: number;
  hasMoreItems: boolean;
};

export type PreviewQueueDeclinedRequeueDryRunExportTaskSummary = {
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

export type PreviewQueueDeclinedRequeueDryRunExportTaskCleanupResponse = {
  deletedCount: number;
  remainingCount: number;
  statusFilter?: string | null;
  message: string;
};

export type PreviewQueueDeclinedRequeueDryRunExportTaskCancelActiveResponse = {
  cancelledCount: number;
  remainingActiveCount: number;
  statusFilter?: string | null;
  message: string;
};

export type PreviewQueueCancelActiveItem = {
  documentId: string | null;
  previewStatus?: string | null;
  previewFailureReason?: string | null;
  previewFailureCategory?: string | null;
  previewLastUpdated?: string | null;
  queueState: string | null;
  outcome: 'CANCELLED' | 'SKIPPED' | 'FAILED' | string;
  message: string | null;
};

export type PreviewQueueCancelActiveResult = {
  stateFilter: PreviewQueueDiagnosticsStateFilter | string;
  queryFilter: string | null;
  limit: number;
  requested: number;
  cancelled: number;
  skipped: number;
  failed: number;
  results: PreviewQueueCancelActiveItem[];
};

export type PreviewReasonBatchQueueRequest = {
  reason: string;
  category?: string;
  retryable?: boolean;
  maxDocuments?: number;
  days?: number;
  force?: boolean;
};

export type PreviewReasonBatchQueueResult = {
  reason: string;
  category: string;
  retryable: boolean | null;
  windowDays: number;
  maxDocuments: number;
  totalByReason: number;
  scanned: number;
  matched: number;
  truncated: boolean;
  queued: number;
  skipped: number;
  failed: number;
  results: PreviewQueueBatchItem[];
};

export type PreviewCadFailoverEndpointStats = {
  endpoint: string;
  successCount: number;
  failureCount: number;
  lastSuccessAt: string | null;
  lastFailureAt: string | null;
  lastFailureReason: string | null;
  consecutiveFailureCount: number;
  circuitState: 'OPEN' | 'HALF_OPEN' | 'CLOSED' | 'DISABLED' | string;
  circuitOpenUntil: string | null;
  lastCircuitOpenedAt: string | null;
  halfOpenInFlight: boolean;
};

export type PreviewCadFailoverDiagnostics = {
  cadPreviewEnabled: boolean;
  configured: boolean;
  circuitBreakerEnabled: boolean;
  circuitFailureThreshold: number;
  circuitOpenMs: number;
  halfOpenTrialTimeoutMs: number;
  endpoints: string[];
  endpointStats: PreviewCadFailoverEndpointStats[];
};

export type PreviewTransformTraceEvent = {
  at: string | null;
  stage: string;
  message: string | null;
};

export type PreviewTransformTrace = {
  requestId: string;
  documentId: string | null;
  mimeType: string | null;
  source: string | null;
  startedAt: string | null;
  finishedAt: string | null;
  status: string | null;
  retryNeeded: boolean;
  failureReason: string | null;
  latestMessage: string | null;
  events: PreviewTransformTraceEvent[];
};

export type PreviewFailurePolicy = {
  key: string;
  label: string;
  maxAttempts: number;
  retryDelayMs: number;
  backoffMultiplier: number;
  quietPeriodMs: number;
  builtIn: boolean;
};

export type PreviewFailurePolicyUpdateRequest = {
  maxAttempts?: number;
  retryDelayMs?: number;
  backoffMultiplier?: number;
  quietPeriodMs?: number;
};

export type PreviewRenditionBlockedItem = {
  documentId: string;
  name: string | null;
  path: string | null;
  mimeType: string | null;
  previewStatus: string | null;
  category: string | null;
  reason: string | null;
  blockedAt: string | null;
  lastHitAt: string | null;
  hitCount: number;
};

export type PreviewRenditionPreventionDiagnostics = {
  enabled: boolean;
  blockedCount: number;
  maxBlocked: number;
  autoBlockCategories: string[];
  limit: number;
  items: PreviewRenditionBlockedItem[];
};

export type PreviewRenditionPreventionAction = {
  documentId: string;
  unblocked: boolean;
  queued: boolean;
  message: string | null;
  previewStatus: string | null;
  attempts: number;
  nextAttemptAt: string | null;
};

export type PreviewRenditionPreventionBatchResult = {
  requested: number;
  deduplicated: number;
  unblocked: number;
  queued: number;
  failed: number;
  results: PreviewRenditionPreventionAction[];
};

export type PreviewDeadLetterItem = {
  entryKey?: string | null;
  documentId: string;
  renditionKey?: string | null;
  name: string | null;
  path: string | null;
  mimeType: string | null;
  previewStatus: string | null;
  reason: string | null;
  category: string | null;
  policyKey: string | null;
  sourceStage: string | null;
  failedAt: string | null;
  attempts: number;
  occurrences: number;
  lastReplayAt?: string | null;
  replayCount?: number;
};

export type PreviewDeadLetterDiagnostics = {
  enabled: boolean;
  backendMode: string;
  redisEnabled: boolean;
  ttlMs: number;
  itemCount: number;
  maxEntries: number;
  limit: number;
  items: PreviewDeadLetterItem[];
};

export type PreviewDeadLetterReplayBatchResult = {
  requested: number;
  deduplicated: number;
  queued: number;
  skipped: number;
  failed: number;
  results: PreviewQueueBatchItem[];
};

export type PreviewDeadLetterClearBatchItem = {
  documentId: string | null;
  entryKey: string | null;
  renditionKey: string | null;
  outcome: 'CLEARED' | 'SKIPPED' | 'FAILED' | string;
  message: string | null;
};

export type PreviewDeadLetterClearBatchResult = {
  requested: number;
  deduplicated: number;
  cleared: number;
  failed: number;
  results: PreviewDeadLetterClearBatchItem[];
};

class PreviewDiagnosticsService {
  private normalizeRenditionResource(item: PreviewRenditionResourceApiItem): PreviewRenditionResource {
    return {
      documentId: item.documentId ?? null,
      name: item.name ?? null,
      path: item.path ?? null,
      status: item.renditionStatus ?? item.status ?? item.previewStatus ?? null,
      mimeType: item.mimeType ?? null,
      reason: item.previewFailureReason ?? item.reason ?? null,
      category: item.previewFailureCategory ?? item.category ?? null,
      previewStatus: item.previewStatus ?? null,
      updatedAt: item.previewLastUpdated ?? item.updatedAt ?? null,
    };
  }

  private normalizeQueueDeclinedCategory(category?: string): string {
    return category?.trim() || 'ANY';
  }

  private normalizeQueueDeclinedQuery(query?: string): string | undefined {
    return query?.trim() || undefined;
  }

  private normalizeQueueDeclinedForceRequired(
    forceRequired?: PreviewQueueDeclinedForceRequiredFilter | string
  ): PreviewQueueDeclinedForceRequiredFilter {
    const normalized = forceRequired?.trim().toUpperCase();
    if (normalized === 'YES' || normalized === 'NO') {
      return normalized;
    }
    return 'ANY';
  }

  private normalizeQueueDeclinedWindowHours(windowHours?: number): number | undefined {
    const normalized = Number(windowHours);
    if (!Number.isFinite(normalized) || normalized <= 0) {
      return undefined;
    }
    return Math.floor(normalized);
  }

  private resolveQueueDeclinedForceRequiredAndQuery(
    forceRequiredOrQuery?: PreviewQueueDeclinedForceRequiredFilter | string,
    query?: string,
    preferForceRequired = false
  ): { forceRequired: PreviewQueueDeclinedForceRequiredFilter; query: string | undefined } {
    if (!preferForceRequired) {
      return {
        forceRequired: 'ANY',
        query: this.normalizeQueueDeclinedQuery(forceRequiredOrQuery),
      };
    }

    return {
      forceRequired: this.normalizeQueueDeclinedForceRequired(forceRequiredOrQuery),
      query: this.normalizeQueueDeclinedQuery(query),
    };
  }

  private resolveQueueDeclinedForceRequiredQueryAndForce(
    forceRequiredOrQuery?: PreviewQueueDeclinedForceRequiredFilter | string,
    queryOrForce?: string | boolean,
    force = true,
    preferForceRequired = false
  ): {
    forceRequired: PreviewQueueDeclinedForceRequiredFilter;
    query: string | undefined;
    force: boolean;
  } {
    if (typeof queryOrForce === 'boolean') {
      return {
        forceRequired: 'ANY',
        query: this.normalizeQueueDeclinedQuery(forceRequiredOrQuery),
        force: queryOrForce,
      };
    }

    if (!preferForceRequired) {
      return {
        forceRequired: 'ANY',
        query: this.normalizeQueueDeclinedQuery(forceRequiredOrQuery),
        force,
      };
    }

    const resolved = this.resolveQueueDeclinedForceRequiredAndQuery(
      forceRequiredOrQuery,
      queryOrForce,
      true
    );
    return {
      forceRequired: resolved.forceRequired,
      query: resolved.query,
      force,
    };
  }

  async listRecentFailures(limit = 50, days = 7): Promise<PreviewFailureSample[]> {
    return api.get<PreviewFailureSample[]>('/preview/diagnostics/failures', { params: { limit, days } });
  }

  async getFailureSummary(sampleLimit = 500, days = 7): Promise<PreviewFailureSummary> {
    return api.get<PreviewFailureSummary>('/preview/diagnostics/failures/summary', {
      params: { sampleLimit, days },
    });
  }

  async getFailureLedger(limit = 100, days = 30): Promise<PreviewFailureLedgerDiagnostics> {
    return api.get<PreviewFailureLedgerDiagnostics>('/preview/diagnostics/failures/ledger', {
      params: { limit, days },
    });
  }

  async resetFailureLedger(documentId: string): Promise<PreviewFailureLedgerResetItem> {
    return api.post<PreviewFailureLedgerResetItem>(
      `/preview/diagnostics/failures/ledger/${encodeURIComponent(documentId)}/reset`
    );
  }

  async resetFailureLedgerBatch(documentIds: string[]): Promise<PreviewFailureLedgerResetBatchResult> {
    return api.post<PreviewFailureLedgerResetBatchResult>(
      '/preview/diagnostics/failures/ledger/reset-batch',
      { documentIds }
    );
  }

  async resetFailureLedgerByFilter(
    request: PreviewFailureLedgerResetByFilterRequest
  ): Promise<PreviewFailureLedgerResetByFilterResult> {
    return api.post<PreviewFailureLedgerResetByFilterResult>(
      '/preview/diagnostics/failures/ledger/reset-by-filter',
      request
    );
  }

  async exportFailureLedgerCsv(days = 30, limit = 500): Promise<void> {
    const filename = `preview_failure_ledger_${new Date().toISOString().replace(/[:.]/g, '-')}.csv`;
    return api.downloadFile('/preview/diagnostics/failures/ledger/export', filename, {
      params: { days, limit },
    });
  }

  async getRenditionSummary(days = 7, sampleLimit = 500): Promise<PreviewRenditionSummary> {
    return api.get<PreviewRenditionSummary>('/preview/diagnostics/renditions/summary', {
      params: { days, sampleLimit },
    });
  }

  async getRenditionResources(days = 7, limit = 500): Promise<PreviewRenditionResource[]> {
    const payload = await api.get<PreviewRenditionResourcesDiagnostics | PreviewRenditionResourceApiItem[]>(
      '/preview/diagnostics/renditions/resources',
      {
        params: { days, limit },
      }
    );
    const items = Array.isArray(payload)
      ? payload
      : Array.isArray(payload?.items)
        ? payload.items
        : [];
    return items.map((item) => this.normalizeRenditionResource(item));
  }

  async exportRenditionResourcesCsv(days = 7, limit = 500): Promise<void> {
    const filename = `preview_rendition_resources_${new Date().toISOString().replace(/[:.]/g, '-')}.csv`;
    return api.downloadFile('/preview/diagnostics/renditions/resources/export', filename, {
      params: { days, limit },
    });
  }

  async startRenditionResourcesExportTask(
    days = 7,
    limit = 500
  ): Promise<PreviewRenditionResourcesExportTask> {
    return api.post<PreviewRenditionResourcesExportTask>(
      '/preview/diagnostics/renditions/resources/export-async',
      { days, limit }
    );
  }

  async listRenditionResourcesExportTasks(
    maxItems = 20,
    status?: PreviewRenditionResourcesExportTaskStatusFilter,
    skipCount = 0
  ): Promise<PreviewRenditionResourcesExportTaskList> {
    return api.get<PreviewRenditionResourcesExportTaskList>(
      '/preview/diagnostics/renditions/resources/export-async',
      {
        params: {
          maxItems,
          limit: maxItems,
          skipCount: Math.max(0, Math.floor(skipCount)),
          status: status || undefined,
        },
      }
    );
  }

  async getRenditionResourcesExportTaskSummary(
    status?: PreviewRenditionResourcesExportTaskStatusFilter
  ): Promise<PreviewRenditionResourcesExportTaskSummary> {
    return api.get<PreviewRenditionResourcesExportTaskSummary>(
      '/preview/diagnostics/renditions/resources/export-async/summary',
      {
        params: {
          status: status || undefined,
        },
      }
    );
  }

  async cleanupRenditionResourcesExportTasks(
    status?: PreviewRenditionResourcesExportTaskStatusFilter
  ): Promise<PreviewRenditionResourcesExportTaskCleanupResponse> {
    return api.post<PreviewRenditionResourcesExportTaskCleanupResponse>(
      '/preview/diagnostics/renditions/resources/export-async/cleanup',
      {},
      {
        params: {
          status: status || undefined,
        },
      }
    );
  }

  async cancelActiveRenditionResourcesExportTasks(
    status?: PreviewRenditionResourcesExportTaskActiveStatusFilter
  ): Promise<PreviewRenditionResourcesExportTaskCancelActiveResponse> {
    return api.post<PreviewRenditionResourcesExportTaskCancelActiveResponse>(
      '/preview/diagnostics/renditions/resources/export-async/cancel-active',
      {},
      {
        params: {
          status: status || undefined,
        },
      }
    );
  }

  async getRenditionResourcesExportTask(taskId: string): Promise<PreviewRenditionResourcesExportTask> {
    return api.get<PreviewRenditionResourcesExportTask>(
      `/preview/diagnostics/renditions/resources/export-async/${encodeURIComponent(taskId)}`
    );
  }

  async cancelRenditionResourcesExportTask(taskId: string): Promise<PreviewRenditionResourcesExportTask> {
    return api.post<PreviewRenditionResourcesExportTask>(
      `/preview/diagnostics/renditions/resources/export-async/${encodeURIComponent(taskId)}/cancel`
    );
  }

  async retryRenditionResourcesExportTask(taskId: string): Promise<PreviewRenditionResourcesExportTask> {
    return api.post<PreviewRenditionResourcesExportTask>(
      `/preview/diagnostics/renditions/resources/export-async/${encodeURIComponent(taskId)}/retry`
    );
  }

  async retryTerminalRenditionResourcesExportTasks(
    status?: PreviewRenditionResourcesExportTaskTerminalStatusFilter,
    limit = 20
  ): Promise<PreviewRenditionResourcesExportTaskRetryTerminalResponse> {
    return api.post<PreviewRenditionResourcesExportTaskRetryTerminalResponse>(
      '/preview/diagnostics/renditions/resources/export-async/retry-terminal',
      {},
      {
        params: {
          status: status || undefined,
          limit,
        },
      }
    );
  }

  async dryRunRetryTerminalRenditionResourcesExportTasks(
    status?: PreviewRenditionResourcesExportTaskTerminalStatusFilter,
    limit = 20
  ): Promise<PreviewRenditionResourcesExportTaskRetryTerminalDryRunResponse> {
    return api.post<PreviewRenditionResourcesExportTaskRetryTerminalDryRunResponse>(
      '/preview/diagnostics/renditions/resources/export-async/retry-terminal/dry-run',
      {},
      {
        params: {
          status: status || undefined,
          limit,
        },
      }
    );
  }

  async exportDryRunRetryTerminalRenditionResourcesExportTasks(
    status?: PreviewRenditionResourcesExportTaskTerminalStatusFilter,
    limit = 20
  ): Promise<void> {
    const filename = `preview_rendition_resources_async_retry_dry_run_${new Date().toISOString().replace(/[:.]/g, '-')}.csv`;
    return api.downloadFile('/preview/diagnostics/renditions/resources/export-async/retry-terminal/dry-run/export', filename, {
      params: {
        status: status || undefined,
        limit,
      },
    });
  }

  async retryTerminalRenditionResourcesExportTasksByTaskIds(
    sourceTaskIds: string[]
  ): Promise<PreviewRenditionResourcesExportTaskRetryTerminalResponse> {
    const payload: PreviewRenditionResourcesExportTaskRetryTerminalByTaskIdsRequest = {
      sourceTaskIds: Array.from(new Set((sourceTaskIds || [])
        .map((taskId) => String(taskId || '').trim())
        .filter((taskId) => taskId.length > 0))),
    };
    return api.post<PreviewRenditionResourcesExportTaskRetryTerminalResponse>(
      '/preview/diagnostics/renditions/resources/export-async/retry-terminal/by-task-ids',
      payload
    );
  }

  async downloadRenditionResourcesExportTask(taskId: string): Promise<Blob> {
    return api.getBlob(
      `/preview/diagnostics/renditions/resources/export-async/${encodeURIComponent(taskId)}/download`
    );
  }

  async queueFailuresBatch(documentIds: string[], force = false): Promise<PreviewQueueBatchResult> {
    return api.post<PreviewQueueBatchResult>('/preview/diagnostics/failures/queue-batch', {
      documentIds,
      force,
    });
  }

  async getQueueDiagnosticsSummary(
    limit = 20,
    state: PreviewQueueDiagnosticsStateFilter = 'ALL',
    query?: string
  ): Promise<PreviewQueueDiagnosticsSummary> {
    return api.get<PreviewQueueDiagnosticsSummary>('/preview/diagnostics/queue/summary', {
      params: { limit, state, query: query?.trim() || undefined },
    });
  }

  async exportQueueDiagnosticsCsv(
    limit = 200,
    state: PreviewQueueDiagnosticsStateFilter = 'ALL',
    query?: string
  ): Promise<void> {
    const filename = `preview_queue_diagnostics_${new Date().toISOString().replace(/[:.]/g, '-')}.csv`;
    return api.downloadFile('/preview/diagnostics/queue/summary/export', filename, {
      params: { limit, state, query: query?.trim() || undefined },
    });
  }

  async cancelQueueDiagnosticsActive(
    limit = 200,
    state: PreviewQueueDiagnosticsStateFilter = 'ALL',
    query?: string
  ): Promise<PreviewQueueCancelActiveResult> {
    return api.post<PreviewQueueCancelActiveResult>(
      '/preview/diagnostics/queue/cancel-active',
      undefined,
      {
        params: { limit, state, query: query?.trim() || undefined },
      }
    );
  }

  async getQueueDeclinedSummary(limit?: number, category?: string, query?: string): Promise<PreviewQueueDeclinedSummary>;
  async getQueueDeclinedSummary(
    limit?: number,
    category?: string,
    forceRequired?: PreviewQueueDeclinedForceRequiredFilter,
    query?: string,
    windowHours?: number
  ): Promise<PreviewQueueDeclinedSummary>;
  async getQueueDeclinedSummary(
    limit = 50,
    category = 'ANY',
    forceRequiredOrQuery?: PreviewQueueDeclinedForceRequiredFilter | string,
    query?: string,
    windowHours?: number
  ): Promise<PreviewQueueDeclinedSummary> {
    const resolved = this.resolveQueueDeclinedForceRequiredAndQuery(
      forceRequiredOrQuery,
      query,
      arguments.length >= 4
    );
    return api.get<PreviewQueueDeclinedSummary>('/preview/diagnostics/queue/declined', {
      params: {
        limit,
        category: this.normalizeQueueDeclinedCategory(category),
        forceRequired: resolved.forceRequired,
        windowHours: this.normalizeQueueDeclinedWindowHours(windowHours),
        query: resolved.query,
      },
    });
  }

  async exportQueueDeclinedCsv(limit?: number, category?: string, query?: string): Promise<void>;
  async exportQueueDeclinedCsv(
    limit?: number,
    category?: string,
    forceRequired?: PreviewQueueDeclinedForceRequiredFilter,
    query?: string,
    windowHours?: number
  ): Promise<void>;
  async exportQueueDeclinedCsv(
    limit = 500,
    category = 'ANY',
    forceRequiredOrQuery?: PreviewQueueDeclinedForceRequiredFilter | string,
    query?: string,
    windowHours?: number
  ): Promise<void> {
    const resolved = this.resolveQueueDeclinedForceRequiredAndQuery(
      forceRequiredOrQuery,
      query,
      arguments.length >= 4
    );
    const filename = `preview_queue_declined_${new Date().toISOString().replace(/[:.]/g, '-')}.csv`;
    return api.downloadFile('/preview/diagnostics/queue/declined/export', filename, {
      params: {
        limit,
        category: this.normalizeQueueDeclinedCategory(category),
        forceRequired: resolved.forceRequired,
        windowHours: this.normalizeQueueDeclinedWindowHours(windowHours),
        query: resolved.query,
      },
    });
  }

  async startQueueDeclinedExportTask(
    limit?: number,
    category?: string,
    query?: string
  ): Promise<PreviewQueueDeclinedExportTask>;
  async startQueueDeclinedExportTask(
    limit?: number,
    category?: string,
    forceRequired?: PreviewQueueDeclinedForceRequiredFilter,
    query?: string,
    windowHours?: number
  ): Promise<PreviewQueueDeclinedExportTask>;
  async startQueueDeclinedExportTask(
    limit = 500,
    category = 'ANY',
    forceRequiredOrQuery?: PreviewQueueDeclinedForceRequiredFilter | string,
    query?: string,
    windowHours?: number
  ): Promise<PreviewQueueDeclinedExportTask> {
    const resolved = this.resolveQueueDeclinedForceRequiredAndQuery(
      forceRequiredOrQuery,
      query,
      arguments.length >= 4
    );
    const payload = {
      limit,
      category: this.normalizeQueueDeclinedCategory(category),
      forceRequired: resolved.forceRequired,
      windowHours: this.normalizeQueueDeclinedWindowHours(windowHours),
      query: resolved.query,
    };
    return api.post<PreviewQueueDeclinedExportTask>(
      '/preview/diagnostics/queue/declined/export-async',
      payload
    );
  }

  async listQueueDeclinedExportTasks(
    maxItems = 20,
    status?: PreviewQueueDeclinedExportTaskStatusFilter,
    skipCount = 0
  ): Promise<PreviewQueueDeclinedExportTaskList> {
    return api.get<PreviewQueueDeclinedExportTaskList>(
      '/preview/diagnostics/queue/declined/export-async',
      {
        params: {
          maxItems,
          limit: maxItems,
          skipCount: Math.max(0, Math.floor(skipCount)),
          status: status || undefined,
        },
      }
    );
  }

  async getQueueDeclinedExportTaskSummary(
    status?: PreviewQueueDeclinedExportTaskStatusFilter
  ): Promise<PreviewQueueDeclinedExportTaskSummary> {
    return api.get<PreviewQueueDeclinedExportTaskSummary>(
      '/preview/diagnostics/queue/declined/export-async/summary',
      {
        params: {
          status: status || undefined,
        },
      }
    );
  }

  async cleanupQueueDeclinedExportTasks(
    status?: PreviewQueueDeclinedExportTaskStatusFilter
  ): Promise<PreviewQueueDeclinedExportTaskCleanupResponse> {
    return api.post<PreviewQueueDeclinedExportTaskCleanupResponse>(
      '/preview/diagnostics/queue/declined/export-async/cleanup',
      {},
      {
        params: {
          status: status || undefined,
        },
      }
    );
  }

  async cancelActiveQueueDeclinedExportTasks(
    status?: PreviewQueueDeclinedExportTaskActiveStatusFilter
  ): Promise<PreviewQueueDeclinedExportTaskCancelActiveResponse> {
    return api.post<PreviewQueueDeclinedExportTaskCancelActiveResponse>(
      '/preview/diagnostics/queue/declined/export-async/cancel-active',
      {},
      {
        params: {
          status: status || undefined,
        },
      }
    );
  }

  async getQueueDeclinedExportTask(taskId: string): Promise<PreviewQueueDeclinedExportTask> {
    return api.get<PreviewQueueDeclinedExportTask>(
      `/preview/diagnostics/queue/declined/export-async/${encodeURIComponent(taskId)}`
    );
  }

  async cancelQueueDeclinedExportTask(taskId: string): Promise<PreviewQueueDeclinedExportTask> {
    return api.post<PreviewQueueDeclinedExportTask>(
      `/preview/diagnostics/queue/declined/export-async/${encodeURIComponent(taskId)}/cancel`
    );
  }

  async retryQueueDeclinedExportTask(taskId: string): Promise<PreviewQueueDeclinedExportTask> {
    return api.post<PreviewQueueDeclinedExportTask>(
      `/preview/diagnostics/queue/declined/export-async/${encodeURIComponent(taskId)}/retry`
    );
  }

  async retryTerminalQueueDeclinedExportTasks(
    status?: PreviewQueueDeclinedExportTaskTerminalStatusFilter,
    limit = 20
  ): Promise<PreviewQueueDeclinedExportTaskRetryTerminalResponse> {
    return api.post<PreviewQueueDeclinedExportTaskRetryTerminalResponse>(
      '/preview/diagnostics/queue/declined/export-async/retry-terminal',
      {},
      {
        params: {
          status: status || undefined,
          limit,
        },
      }
    );
  }

  async dryRunRetryTerminalQueueDeclinedExportTasks(
    status?: PreviewQueueDeclinedExportTaskTerminalStatusFilter,
    limit = 20
  ): Promise<PreviewQueueDeclinedExportTaskRetryTerminalDryRunResponse> {
    return api.post<PreviewQueueDeclinedExportTaskRetryTerminalDryRunResponse>(
      '/preview/diagnostics/queue/declined/export-async/retry-terminal/dry-run',
      {},
      {
        params: {
          status: status || undefined,
          limit,
        },
      }
    );
  }

  async exportDryRunRetryTerminalQueueDeclinedExportTasks(
    status?: PreviewQueueDeclinedExportTaskTerminalStatusFilter,
    limit = 20
  ): Promise<void> {
    const filename = `preview_queue_declined_async_retry_dry_run_${new Date().toISOString().replace(/[:.]/g, '-')}.csv`;
    return api.downloadFile('/preview/diagnostics/queue/declined/export-async/retry-terminal/dry-run/export', filename, {
      params: {
        status: status || undefined,
        limit,
      },
    });
  }

  async retryTerminalQueueDeclinedExportTasksByTaskIds(
    sourceTaskIds: string[]
  ): Promise<PreviewQueueDeclinedExportTaskRetryTerminalResponse> {
    const payload: PreviewQueueDeclinedExportTaskRetryTerminalByTaskIdsRequest = {
      sourceTaskIds: Array.from(new Set((sourceTaskIds || [])
        .map((taskId) => String(taskId || '').trim())
        .filter((taskId) => taskId.length > 0))),
    };
    return api.post<PreviewQueueDeclinedExportTaskRetryTerminalResponse>(
      '/preview/diagnostics/queue/declined/export-async/retry-terminal/by-task-ids',
      payload
    );
  }

  async downloadQueueDeclinedExportTask(taskId: string): Promise<Blob> {
    return api.getBlob(
      `/preview/diagnostics/queue/declined/export-async/${encodeURIComponent(taskId)}/download`
    );
  }

  async requeueQueueDeclined(
    limit?: number,
    category?: string,
    query?: string,
    force?: boolean
  ): Promise<PreviewQueueDeclinedRequeueResult>;
  async requeueQueueDeclined(
    limit?: number,
    category?: string,
    forceRequired?: PreviewQueueDeclinedForceRequiredFilter,
    query?: string,
    force?: boolean,
    windowHours?: number
  ): Promise<PreviewQueueDeclinedRequeueResult>;
  async requeueQueueDeclined(
    limit = 200,
    category = 'ANY',
    forceRequiredOrQuery?: PreviewQueueDeclinedForceRequiredFilter | string,
    queryOrForce?: string | boolean,
    force = true,
    windowHours?: number
  ): Promise<PreviewQueueDeclinedRequeueResult> {
    const resolved = this.resolveQueueDeclinedForceRequiredQueryAndForce(
      forceRequiredOrQuery,
      queryOrForce,
      force,
      arguments.length >= 4 && typeof queryOrForce !== 'boolean'
    );
    return api.post<PreviewQueueDeclinedRequeueResult>(
      '/preview/diagnostics/queue/declined/requeue',
      undefined,
      {
        params: {
          limit,
          category: this.normalizeQueueDeclinedCategory(category),
          forceRequired: resolved.forceRequired,
          windowHours: this.normalizeQueueDeclinedWindowHours(windowHours),
          query: resolved.query,
          force: resolved.force,
        },
      }
    );
  }

  async dryRunQueueDeclinedRequeue(
    limit?: number,
    category?: string,
    query?: string,
    force?: boolean
  ): Promise<PreviewQueueDeclinedRequeueDryRunResult>;
  async dryRunQueueDeclinedRequeue(
    limit?: number,
    category?: string,
    forceRequired?: PreviewQueueDeclinedForceRequiredFilter,
    query?: string,
    force?: boolean,
    windowHours?: number
  ): Promise<PreviewQueueDeclinedRequeueDryRunResult>;
  async dryRunQueueDeclinedRequeue(
    limit = 200,
    category = 'ANY',
    forceRequiredOrQuery?: PreviewQueueDeclinedForceRequiredFilter | string,
    queryOrForce?: string | boolean,
    force = true,
    windowHours?: number
  ): Promise<PreviewQueueDeclinedRequeueDryRunResult> {
    const resolved = this.resolveQueueDeclinedForceRequiredQueryAndForce(
      forceRequiredOrQuery,
      queryOrForce,
      force,
      arguments.length >= 4 && typeof queryOrForce !== 'boolean'
    );
    return api.post<PreviewQueueDeclinedRequeueDryRunResult>(
      '/preview/diagnostics/queue/declined/requeue/dry-run',
      undefined,
      {
        params: {
          limit,
          category: this.normalizeQueueDeclinedCategory(category),
          forceRequired: resolved.forceRequired,
          windowHours: this.normalizeQueueDeclinedWindowHours(windowHours),
          query: resolved.query,
          force: resolved.force,
        },
      }
    );
  }

  async exportQueueDeclinedRequeueDryRunCsv(
    limit?: number,
    category?: string,
    query?: string,
    force?: boolean
  ): Promise<void>;
  async exportQueueDeclinedRequeueDryRunCsv(
    limit?: number,
    category?: string,
    forceRequired?: PreviewQueueDeclinedForceRequiredFilter,
    query?: string,
    force?: boolean,
    windowHours?: number
  ): Promise<void>;
  async exportQueueDeclinedRequeueDryRunCsv(
    limit = 200,
    category = 'ANY',
    forceRequiredOrQuery?: PreviewQueueDeclinedForceRequiredFilter | string,
    queryOrForce?: string | boolean,
    force = true,
    windowHours?: number
  ): Promise<void> {
    const resolved = this.resolveQueueDeclinedForceRequiredQueryAndForce(
      forceRequiredOrQuery,
      queryOrForce,
      force,
      arguments.length >= 4 && typeof queryOrForce !== 'boolean'
    );
    const filename = `preview_queue_declined_requeue_dry_run_${new Date().toISOString().replace(/[:.]/g, '-')}.csv`;
    return api.downloadFile('/preview/diagnostics/queue/declined/requeue/dry-run/export', filename, {
      params: {
        limit,
        category: this.normalizeQueueDeclinedCategory(category),
        forceRequired: resolved.forceRequired,
        windowHours: this.normalizeQueueDeclinedWindowHours(windowHours),
        query: resolved.query,
        force: resolved.force,
      },
    });
  }

  async startQueueDeclinedRequeueDryRunExportTask(
    limit?: number,
    category?: string,
    query?: string,
    force?: boolean
  ): Promise<PreviewQueueDeclinedRequeueDryRunExportTask>;
  async startQueueDeclinedRequeueDryRunExportTask(
    limit?: number,
    category?: string,
    forceRequired?: PreviewQueueDeclinedForceRequiredFilter,
    query?: string,
    force?: boolean,
    windowHours?: number
  ): Promise<PreviewQueueDeclinedRequeueDryRunExportTask>;
  async startQueueDeclinedRequeueDryRunExportTask(
    limit = 200,
    category = 'ANY',
    forceRequiredOrQuery?: PreviewQueueDeclinedForceRequiredFilter | string,
    queryOrForce?: string | boolean,
    force = true,
    windowHours?: number
  ): Promise<PreviewQueueDeclinedRequeueDryRunExportTask> {
    const resolved = this.resolveQueueDeclinedForceRequiredQueryAndForce(
      forceRequiredOrQuery,
      queryOrForce,
      force,
      arguments.length >= 4 && typeof queryOrForce !== 'boolean'
    );
    const payload = {
      limit,
      category: this.normalizeQueueDeclinedCategory(category),
      forceRequired: resolved.forceRequired,
      windowHours: this.normalizeQueueDeclinedWindowHours(windowHours),
      query: resolved.query,
      force: resolved.force,
    };
    return api.post<PreviewQueueDeclinedRequeueDryRunExportTask>(
      '/preview/diagnostics/queue/declined/requeue/dry-run/export-async',
      payload
    );
  }

  async listQueueDeclinedRequeueDryRunExportTasks(
    maxItems = 20,
    status?: PreviewQueueDeclinedRequeueDryRunExportTaskStatusFilter,
    skipCount = 0
  ): Promise<PreviewQueueDeclinedRequeueDryRunExportTaskList> {
    return api.get<PreviewQueueDeclinedRequeueDryRunExportTaskList>(
      '/preview/diagnostics/queue/declined/requeue/dry-run/export-async',
      {
        params: {
          maxItems,
          limit: maxItems,
          skipCount: Math.max(0, Math.floor(skipCount)),
          status: status || undefined,
        },
      }
    );
  }

  async getQueueDeclinedRequeueDryRunExportTaskSummary(
    status?: PreviewQueueDeclinedRequeueDryRunExportTaskStatusFilter
  ): Promise<PreviewQueueDeclinedRequeueDryRunExportTaskSummary> {
    return api.get<PreviewQueueDeclinedRequeueDryRunExportTaskSummary>(
      '/preview/diagnostics/queue/declined/requeue/dry-run/export-async/summary',
      {
        params: {
          status: status || undefined,
        },
      }
    );
  }

  async cleanupQueueDeclinedRequeueDryRunExportTasks(
    status?: PreviewQueueDeclinedRequeueDryRunExportTaskStatusFilter
  ): Promise<PreviewQueueDeclinedRequeueDryRunExportTaskCleanupResponse> {
    return api.post<PreviewQueueDeclinedRequeueDryRunExportTaskCleanupResponse>(
      '/preview/diagnostics/queue/declined/requeue/dry-run/export-async/cleanup',
      {},
      {
        params: {
          status: status || undefined,
        },
      }
    );
  }

  async cancelActiveQueueDeclinedRequeueDryRunExportTasks(
    status?: PreviewQueueDeclinedRequeueDryRunExportTaskActiveStatusFilter
  ): Promise<PreviewQueueDeclinedRequeueDryRunExportTaskCancelActiveResponse> {
    return api.post<PreviewQueueDeclinedRequeueDryRunExportTaskCancelActiveResponse>(
      '/preview/diagnostics/queue/declined/requeue/dry-run/export-async/cancel-active',
      {},
      {
        params: {
          status: status || undefined,
        },
      }
    );
  }

  async getQueueDeclinedRequeueDryRunExportTask(
    taskId: string
  ): Promise<PreviewQueueDeclinedRequeueDryRunExportTask> {
    return api.get<PreviewQueueDeclinedRequeueDryRunExportTask>(
      `/preview/diagnostics/queue/declined/requeue/dry-run/export-async/${encodeURIComponent(taskId)}`
    );
  }

  async cancelQueueDeclinedRequeueDryRunExportTask(
    taskId: string
  ): Promise<PreviewQueueDeclinedRequeueDryRunExportTask> {
    return api.post<PreviewQueueDeclinedRequeueDryRunExportTask>(
      `/preview/diagnostics/queue/declined/requeue/dry-run/export-async/${encodeURIComponent(taskId)}/cancel`
    );
  }

  async retryQueueDeclinedRequeueDryRunExportTask(
    taskId: string
  ): Promise<PreviewQueueDeclinedRequeueDryRunExportTask> {
    return api.post<PreviewQueueDeclinedRequeueDryRunExportTask>(
      `/preview/diagnostics/queue/declined/requeue/dry-run/export-async/${encodeURIComponent(taskId)}/retry`
    );
  }

  async retryTerminalQueueDeclinedRequeueDryRunExportTasks(
    status?: PreviewQueueDeclinedRequeueDryRunExportTaskTerminalStatusFilter,
    limit = 20
  ): Promise<PreviewQueueDeclinedRequeueDryRunExportTaskRetryTerminalResponse> {
    return api.post<PreviewQueueDeclinedRequeueDryRunExportTaskRetryTerminalResponse>(
      '/preview/diagnostics/queue/declined/requeue/dry-run/export-async/retry-terminal',
      {},
      {
        params: {
          status: status || undefined,
          limit,
        },
      }
    );
  }

  async dryRunRetryTerminalQueueDeclinedRequeueDryRunExportTasks(
    status?: PreviewQueueDeclinedRequeueDryRunExportTaskTerminalStatusFilter,
    limit = 20
  ): Promise<PreviewQueueDeclinedRequeueDryRunExportTaskRetryTerminalDryRunResponse> {
    return api.post<PreviewQueueDeclinedRequeueDryRunExportTaskRetryTerminalDryRunResponse>(
      '/preview/diagnostics/queue/declined/requeue/dry-run/export-async/retry-terminal/dry-run',
      {},
      {
        params: {
          status: status || undefined,
          limit,
        },
      }
    );
  }

  async exportDryRunRetryTerminalQueueDeclinedRequeueDryRunExportTasks(
    status?: PreviewQueueDeclinedRequeueDryRunExportTaskTerminalStatusFilter,
    limit = 20
  ): Promise<void> {
    const filename = `preview_queue_declined_requeue_dry_run_async_retry_dry_run_${new Date().toISOString().replace(/[:.]/g, '-')}.csv`;
    return api.downloadFile('/preview/diagnostics/queue/declined/requeue/dry-run/export-async/retry-terminal/dry-run/export', filename, {
      params: {
        status: status || undefined,
        limit,
      },
    });
  }

  async retryTerminalQueueDeclinedRequeueDryRunExportTasksByTaskIds(
    sourceTaskIds: string[]
  ): Promise<PreviewQueueDeclinedRequeueDryRunExportTaskRetryTerminalResponse> {
    const payload: PreviewQueueDeclinedRequeueDryRunExportTaskRetryTerminalByTaskIdsRequest = {
      sourceTaskIds: Array.from(new Set((sourceTaskIds || [])
        .map((taskId) => String(taskId || '').trim())
        .filter((taskId) => taskId.length > 0))),
    };
    return api.post<PreviewQueueDeclinedRequeueDryRunExportTaskRetryTerminalResponse>(
      '/preview/diagnostics/queue/declined/requeue/dry-run/export-async/retry-terminal/by-task-ids',
      payload
    );
  }

  async downloadQueueDeclinedRequeueDryRunExportTask(taskId: string): Promise<Blob> {
    return api.getBlob(
      `/preview/diagnostics/queue/declined/requeue/dry-run/export-async/${encodeURIComponent(taskId)}/download`
    );
  }

  async clearQueueDeclined(limit?: number, category?: string, query?: string): Promise<PreviewQueueDeclinedClearResult>;
  async clearQueueDeclined(
    limit?: number,
    category?: string,
    forceRequired?: PreviewQueueDeclinedForceRequiredFilter,
    query?: string,
    windowHours?: number
  ): Promise<PreviewQueueDeclinedClearResult>;
  async clearQueueDeclined(
    limit = 200,
    category = 'ANY',
    forceRequiredOrQuery?: PreviewQueueDeclinedForceRequiredFilter | string,
    query?: string,
    windowHours?: number
  ): Promise<PreviewQueueDeclinedClearResult> {
    const resolved = this.resolveQueueDeclinedForceRequiredAndQuery(
      forceRequiredOrQuery,
      query,
      arguments.length >= 4
    );
    return api.post<PreviewQueueDeclinedClearResult>(
      '/preview/diagnostics/queue/declined/clear',
      undefined,
      {
        params: {
          limit,
          category: this.normalizeQueueDeclinedCategory(category),
          forceRequired: resolved.forceRequired,
          windowHours: this.normalizeQueueDeclinedWindowHours(windowHours),
          query: resolved.query,
        },
      }
    );
  }

  async queueFailuresByReason(payload: PreviewReasonBatchQueueRequest): Promise<PreviewReasonBatchQueueResult> {
    return api.post<PreviewReasonBatchQueueResult>('/preview/diagnostics/failures/queue-by-reason', payload);
  }

  async getCadFailoverDiagnostics(): Promise<PreviewCadFailoverDiagnostics> {
    return api.get<PreviewCadFailoverDiagnostics>('/preview/diagnostics/cad-failover');
  }

  async getTransformTraces(limit = 20, requestId?: string): Promise<PreviewTransformTrace[]> {
    return api.get<PreviewTransformTrace[]>('/preview/diagnostics/traces', {
      params: { limit, requestId: requestId || undefined },
    });
  }

  async getFailurePolicies(): Promise<PreviewFailurePolicy[]> {
    return api.get<PreviewFailurePolicy[]>('/preview/diagnostics/policies');
  }

  async updateFailurePolicy(
    profileKey: string,
    payload: PreviewFailurePolicyUpdateRequest
  ): Promise<PreviewFailurePolicy> {
    return api.put<PreviewFailurePolicy>(`/preview/diagnostics/policies/${encodeURIComponent(profileKey)}`, payload);
  }

  async getRenditionPreventionBlocked(limit = 50): Promise<PreviewRenditionPreventionDiagnostics> {
    return api.get<PreviewRenditionPreventionDiagnostics>('/preview/diagnostics/prevention/blocked', {
      params: { limit },
    });
  }

  async unblockRenditionPrevention(documentId: string): Promise<PreviewRenditionPreventionAction> {
    return api.post<PreviewRenditionPreventionAction>(
      `/preview/diagnostics/prevention/${encodeURIComponent(documentId)}/unblock`
    );
  }

  async unblockAndRequeueRendition(
    documentId: string,
    force = true
  ): Promise<PreviewRenditionPreventionAction> {
    return api.post<PreviewRenditionPreventionAction>(
      `/preview/diagnostics/prevention/${encodeURIComponent(documentId)}/unblock-requeue`,
      undefined,
      {
        params: { force },
      }
    );
  }

  async unblockRenditionPreventionBatch(
    documentIds: string[]
  ): Promise<PreviewRenditionPreventionBatchResult> {
    return api.post<PreviewRenditionPreventionBatchResult>(
      '/preview/diagnostics/prevention/unblock-batch',
      { documentIds }
    );
  }

  async unblockAndRequeueRenditionBatch(
    documentIds: string[],
    force = true
  ): Promise<PreviewRenditionPreventionBatchResult> {
    return api.post<PreviewRenditionPreventionBatchResult>(
      '/preview/diagnostics/prevention/unblock-requeue-batch',
      { documentIds, force }
    );
  }

  async getDeadLetter(limit = 50): Promise<PreviewDeadLetterDiagnostics> {
    return api.get<PreviewDeadLetterDiagnostics>('/preview/diagnostics/dead-letter', {
      params: { limit },
    });
  }

  async replayDeadLetterBatch(
    payload: { documentIds?: string[]; entryKeys?: string[]; force?: boolean },
    force = true
  ): Promise<PreviewDeadLetterReplayBatchResult> {
    const documentIds = payload?.documentIds || [];
    const entryKeys = payload?.entryKeys || [];
    return api.post<PreviewDeadLetterReplayBatchResult>(
      '/preview/diagnostics/dead-letter/replay-batch',
      { documentIds, entryKeys, force: payload?.force ?? force }
    );
  }

  async clearDeadLetterBatch(
    payload: { documentIds?: string[]; entryKeys?: string[] }
  ): Promise<PreviewDeadLetterClearBatchResult> {
    const documentIds = payload?.documentIds || [];
    const entryKeys = payload?.entryKeys || [];
    return api.post<PreviewDeadLetterClearBatchResult>(
      '/preview/diagnostics/dead-letter/clear-batch',
      { documentIds, entryKeys }
    );
  }

  async exportDeadLetterCsv(limit = 500): Promise<void> {
    const filename = `preview_dead_letter_${new Date().toISOString().replace(/[:.]/g, '-')}.csv`;
    return api.downloadFile('/preview/diagnostics/dead-letter/export', filename, {
      params: { limit },
    });
  }
}

const previewDiagnosticsService = new PreviewDiagnosticsService();
export default previewDiagnosticsService;
