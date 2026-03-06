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

export type PreviewQueueBatchItem = {
  documentId: string;
  outcome: 'QUEUED' | 'SKIPPED' | 'FAILED' | string;
  message: string | null;
  previewStatus: string | null;
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

class PreviewDiagnosticsService {
  async listRecentFailures(limit = 50, days = 7): Promise<PreviewFailureSample[]> {
    return api.get<PreviewFailureSample[]>('/preview/diagnostics/failures', { params: { limit, days } });
  }

  async getFailureSummary(sampleLimit = 500, days = 7): Promise<PreviewFailureSummary> {
    return api.get<PreviewFailureSummary>('/preview/diagnostics/failures/summary', {
      params: { sampleLimit, days },
    });
  }

  async queueFailuresBatch(documentIds: string[], force = false): Promise<PreviewQueueBatchResult> {
    return api.post<PreviewQueueBatchResult>('/preview/diagnostics/failures/queue-batch', {
      documentIds,
      force,
    });
  }
}

const previewDiagnosticsService = new PreviewDiagnosticsService();
export default previewDiagnosticsService;
