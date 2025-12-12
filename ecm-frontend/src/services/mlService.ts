import api from './api';

export interface ClassificationResult {
  suggestedCategory?: string;
  confidence?: number;
  alternatives?: Array<{ category: string; confidence: number }>;
  success: boolean;
  errorMessage?: string;
}

class MLService {
  async health(): Promise<Record<string, any>> {
    return api.get<Record<string, any>>('/ml/health');
  }

  async classifyDocument(documentId: string): Promise<ClassificationResult> {
    return api.post<ClassificationResult>(`/ml/classify/${documentId}`);
  }

  async classifyText(text: string, candidates?: string[]): Promise<ClassificationResult> {
    return api.post<ClassificationResult>('/ml/classify', { text, candidates });
  }

  async suggestTagsForDocument(documentId: string, maxTags = 5): Promise<string[]> {
    return api.get<string[]>(`/ml/suggest-tags/${documentId}`, {
      params: { maxTags },
    });
  }

  async suggestTags(text: string, maxTags = 5): Promise<string[]> {
    return api.post<string[]>('/ml/suggest-tags', { text, maxTags });
  }
}

const mlService = new MLService();
export default mlService;

