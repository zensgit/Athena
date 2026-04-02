import api from './api';

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

class OpsRecoveryService {
  async queueByReason(payload: QueueByReasonRequest): Promise<RecoveryBatchResult> {
    return api.post<RecoveryBatchResult>('/ops/recovery/queue-by-reason', payload);
  }

  async queueByWindow(payload: QueueByWindowRequest): Promise<RecoveryBatchResult> {
    return api.post<RecoveryBatchResult>('/ops/recovery/queue-by-window', payload);
  }

  async replayBatch(payload: ReplayBatchRequest): Promise<RecoveryBatchResult> {
    return api.post<RecoveryBatchResult>('/ops/recovery/replay-batch', payload);
  }

  async clearBatch(payload: ClearBatchRequest): Promise<RecoveryBatchResult> {
    return api.post<RecoveryBatchResult>('/ops/recovery/clear-batch', payload);
  }

  async clearByFilter(payload: ClearByFilterRequest): Promise<RecoveryBatchResult> {
    return api.post<RecoveryBatchResult>('/ops/recovery/clear-by-filter', payload);
  }

  async replayByFilter(payload: ReplayByFilterRequest): Promise<RecoveryBatchResult> {
    return api.post<RecoveryBatchResult>('/ops/recovery/replay-by-filter', payload);
  }

  async dryRun(payload: RecoveryDryRunRequest): Promise<RecoveryDryRunResult> {
    return api.post<RecoveryDryRunResult>('/ops/recovery/dry-run', payload);
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
    return api.get<RecoveryHistoryResult>('/ops/recovery/history', {
      params: {
        limit,
        days,
        page,
        mode: mode || undefined,
        actor: actor || undefined,
        eventType: eventType || undefined,
      },
    });
  }

  async getHistorySummary(
    days = 7,
    mode?: RecoveryHistoryMode,
    actor?: string,
    eventType?: RecoveryHistoryEventType
  ): Promise<RecoveryHistorySummaryResult> {
    return api.get<RecoveryHistorySummaryResult>('/ops/recovery/history/summary', {
      params: {
        days,
        mode: mode || undefined,
        actor: actor || undefined,
        eventType: eventType || undefined,
      },
    });
  }

  async getHistorySummaryTrend(
    days = 7,
    mode?: RecoveryHistoryMode,
    actor?: string,
    eventType?: RecoveryHistoryEventType
  ): Promise<RecoveryHistoryTrendResult> {
    return api.get<RecoveryHistoryTrendResult>('/ops/recovery/history/summary/trend', {
      params: {
        days,
        mode: mode || undefined,
        actor: actor || undefined,
        eventType: eventType || undefined,
      },
    });
  }

  async getHistorySummaryCompare(
    days = 7,
    mode?: RecoveryHistoryMode,
    actor?: string,
    eventType?: RecoveryHistoryEventType
  ): Promise<RecoveryHistorySummaryCompareResult> {
    return api.get<RecoveryHistorySummaryCompareResult>('/ops/recovery/history/summary/compare', {
      params: {
        days,
        mode: mode || undefined,
        actor: actor || undefined,
        eventType: eventType || undefined,
      },
    });
  }

  async getHistorySummaryCompareBreakdown(
    days = 7,
    mode?: RecoveryHistoryMode,
    actor?: string,
    eventType?: RecoveryHistoryEventType,
    limit = 10,
    sort: RecoveryHistoryCompareBreakdownSort = 'DELTA_ABS_DESC'
  ): Promise<RecoveryHistorySummaryCompareBreakdownResult> {
    return api.get<RecoveryHistorySummaryCompareBreakdownResult>('/ops/recovery/history/summary/compare/breakdown', {
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

  async getHistorySummaryCompareActors(
    days = 7,
    mode?: RecoveryHistoryMode,
    actor?: string,
    eventType?: RecoveryHistoryEventType,
    limit = 10,
    sort: RecoveryHistoryCompareActorSort = 'DELTA_ABS_DESC'
  ): Promise<RecoveryHistorySummaryCompareActorsResult> {
    return api.get<RecoveryHistorySummaryCompareActorsResult>('/ops/recovery/history/summary/compare/actors', {
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
    return api.post<RecoveryHistoryExportAsyncCreateResponse>('/ops/recovery/history/export-async', payload);
  }

  async listHistoryExportAsyncTasks(
    maxItems = 20,
    exportType?: RecoveryHistoryExportAsyncType,
    status?: RecoveryHistoryExportAsyncStatusFilter,
    skipCount = 0
  ): Promise<RecoveryHistoryExportAsyncTaskList> {
    return api.get<RecoveryHistoryExportAsyncTaskList>('/ops/recovery/history/export-async', {
      params: {
        maxItems,
        limit: maxItems,
        skipCount: Math.max(0, Math.floor(skipCount)),
        exportType: exportType || undefined,
        status: status || undefined,
      },
    });
  }

  async getHistoryExportAsyncTask(taskId: string): Promise<RecoveryHistoryExportAsyncTaskStatus> {
    return api.get<RecoveryHistoryExportAsyncTaskStatus>(
      `/ops/recovery/history/export-async/${encodeURIComponent(taskId)}`
    );
  }

  async getHistoryExportAsyncTaskSummary(): Promise<RecoveryHistoryExportAsyncTaskSummary> {
    return api.get<RecoveryHistoryExportAsyncTaskSummary>('/ops/recovery/history/export-async/summary');
  }

  async getHistoryExportAsyncTaskSummaryFiltered(
    exportType?: RecoveryHistoryExportAsyncType,
    status?: RecoveryHistoryExportAsyncStatusFilter
  ): Promise<RecoveryHistoryExportAsyncTaskSummary> {
    return api.get<RecoveryHistoryExportAsyncTaskSummary>('/ops/recovery/history/export-async/summary', {
      params: {
        exportType: exportType || undefined,
        status: status || undefined,
      },
    });
  }

  async cancelHistoryExportAsyncTask(taskId: string): Promise<RecoveryHistoryExportAsyncTaskStatus> {
    return api.post<RecoveryHistoryExportAsyncTaskStatus>(
      `/ops/recovery/history/export-async/${encodeURIComponent(taskId)}/cancel`
    );
  }

  async retryHistoryExportAsyncTask(taskId: string): Promise<RecoveryHistoryExportAsyncCreateResponse> {
    return api.post<RecoveryHistoryExportAsyncCreateResponse>(
      `/ops/recovery/history/export-async/${encodeURIComponent(taskId)}/retry`
    );
  }

  async retryTerminalHistoryExportAsyncTasks(
    exportType?: RecoveryHistoryExportAsyncType,
    status?: RecoveryHistoryExportAsyncTerminalStatusFilter,
    limit = 20
  ): Promise<RecoveryHistoryExportAsyncRetryTerminalResponse> {
    return api.post<RecoveryHistoryExportAsyncRetryTerminalResponse>(
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
  }

  async dryRunRetryTerminalHistoryExportAsyncTasks(
    exportType?: RecoveryHistoryExportAsyncType,
    status?: RecoveryHistoryExportAsyncTerminalStatusFilter,
    limit = 20
  ): Promise<RecoveryHistoryExportAsyncRetryTerminalDryRunResponse> {
    return api.post<RecoveryHistoryExportAsyncRetryTerminalDryRunResponse>(
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
    return api.post<RecoveryHistoryExportAsyncRetryTerminalResponse>(
      '/ops/recovery/history/export-async/retry-terminal/by-task-ids',
      payload,
      {
        params: {
          exportType: exportType || undefined,
        },
      }
    );
  }

  async cancelActiveHistoryExportAsyncTasks(
    exportType?: RecoveryHistoryExportAsyncType,
    status?: RecoveryHistoryExportAsyncActiveStatusFilter
  ): Promise<RecoveryHistoryExportAsyncTaskCancelActiveResponse> {
    return api.post<RecoveryHistoryExportAsyncTaskCancelActiveResponse>(
      '/ops/recovery/history/export-async/cancel-active',
      {},
      {
        params: {
          exportType: exportType || undefined,
          status: status || undefined,
        },
      }
    );
  }

  async cleanupHistoryExportAsyncTasks(
    exportType?: RecoveryHistoryExportAsyncType,
    status?: RecoveryHistoryExportAsyncStatusFilter
  ): Promise<RecoveryHistoryExportAsyncTaskCleanupResponse> {
    return api.post<RecoveryHistoryExportAsyncTaskCleanupResponse>(
      '/ops/recovery/history/export-async/cleanup',
      {},
      {
        params: {
          exportType: exportType || undefined,
          status: status || undefined,
        },
      }
    );
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
