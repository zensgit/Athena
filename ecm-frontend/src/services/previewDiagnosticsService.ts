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

class PreviewDiagnosticsService {
  async listRecentFailures(limit = 50): Promise<PreviewFailureSample[]> {
    return api.get<PreviewFailureSample[]>('/preview/diagnostics/failures', { params: { limit } });
  }
}

const previewDiagnosticsService = new PreviewDiagnosticsService();
export default previewDiagnosticsService;

