import api from './api';

export const ML_UNEXPECTED_RESPONSE_MESSAGE =
  'ML endpoint returned an unexpected response. Mocked CI gate may not cover it; backend route may be missing.';

export interface MLHealthStatus {
  available: boolean;
  modelLoaded: boolean;
  modelVersion: string;
  status: string;
}

export interface ClassificationResult {
  suggestedCategory?: string | null;
  confidence?: number | null;
  alternatives?: Array<{ category: string; confidence: number }> | null;
  success: boolean;
  errorMessage?: string | null;
}

const isObject = (value: unknown): value is Record<string, unknown> => (
  value !== null && typeof value === 'object' && !Array.isArray(value)
);

const isStringOrNullish = (value: unknown): value is string | null | undefined => (
  value === null || value === undefined || typeof value === 'string'
);

const isNumberOrNullish = (value: unknown): value is number | null | undefined => (
  value === null || value === undefined || (typeof value === 'number' && Number.isFinite(value))
);

const assertUnexpectedResponse = (): never => {
  throw new Error(ML_UNEXPECTED_RESPONSE_MESSAGE);
};

const isMLHealthStatus = (value: unknown): value is MLHealthStatus => (
  isObject(value)
    && typeof value.available === 'boolean'
    && typeof value.modelLoaded === 'boolean'
    && typeof value.modelVersion === 'string'
    && typeof value.status === 'string'
);

const assertMLHealthStatus = (value: unknown): MLHealthStatus => (
  isMLHealthStatus(value) ? value : assertUnexpectedResponse()
);

const isAlternativeCategory = (value: unknown): value is { category: string; confidence: number } => (
  isObject(value)
    && typeof value.category === 'string'
    && typeof value.confidence === 'number'
    && Number.isFinite(value.confidence)
);

const isAlternativeCategoryListOrNullish = (
  value: unknown
): value is Array<{ category: string; confidence: number }> | null | undefined => (
  value === null
    || value === undefined
    || (Array.isArray(value) && value.every(isAlternativeCategory))
);

const isClassificationResult = (value: unknown): value is ClassificationResult => (
  isObject(value)
    && typeof value.success === 'boolean'
    && isStringOrNullish(value.suggestedCategory)
    && isNumberOrNullish(value.confidence)
    && isAlternativeCategoryListOrNullish(value.alternatives)
    && isStringOrNullish(value.errorMessage)
);

const assertClassificationResult = (value: unknown): ClassificationResult => (
  isClassificationResult(value) ? value : assertUnexpectedResponse()
);

const assertStringArray = (value: unknown): string[] => {
  if (!Array.isArray(value) || !value.every((item) => typeof item === 'string')) {
    return assertUnexpectedResponse();
  }
  return value;
};

class MLService {
  async health(): Promise<Record<string, any>> {
    const result = await api.get<unknown>('/ml/health');
    return assertMLHealthStatus(result);
  }

  async classifyDocument(documentId: string): Promise<ClassificationResult> {
    const result = await api.post<unknown>(`/ml/classify/${documentId}`);
    return assertClassificationResult(result);
  }

  async classifyText(text: string, candidates?: string[]): Promise<ClassificationResult> {
    const result = await api.post<unknown>('/ml/classify', { text, candidates });
    return assertClassificationResult(result);
  }

  async suggestTagsForDocument(documentId: string, maxTags = 5): Promise<string[]> {
    const result = await api.get<unknown>(`/ml/suggest-tags/${documentId}`, {
      params: { maxTags },
    });
    return assertStringArray(result);
  }

  async suggestTags(text: string, maxTags = 5): Promise<string[]> {
    const result = await api.post<unknown>('/ml/suggest-tags', { text, maxTags });
    return assertStringArray(result);
  }
}

const mlService = new MLService();
export default mlService;
