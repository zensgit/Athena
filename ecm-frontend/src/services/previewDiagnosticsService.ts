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

class PreviewDiagnosticsService {
  async listRecentFailures(limit = 50, days = 7): Promise<PreviewFailureSample[]> {
    return api.get<PreviewFailureSample[]>('/preview/diagnostics/failures', { params: { limit, days } });
  }

  async getFailureSummary(sampleLimit = 500, days = 7): Promise<PreviewFailureSummary> {
    return api.get<PreviewFailureSummary>('/preview/diagnostics/failures/summary', {
      params: { sampleLimit, days },
    });
  }
}

const previewDiagnosticsService = new PreviewDiagnosticsService();
export default previewDiagnosticsService;
