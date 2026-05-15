import api from './api';
import mlService, {
  ClassificationResult,
  MLHealthStatus,
  ML_UNEXPECTED_RESPONSE_MESSAGE,
} from './mlService';

jest.mock('./api', () => ({
  __esModule: true,
  default: {
    get: jest.fn(),
    post: jest.fn(),
  },
}));

const mockedApi = api as jest.Mocked<typeof api>;

const healthStatus: MLHealthStatus = {
  available: true,
  modelLoaded: true,
  modelVersion: '2026.05',
  status: 'healthy',
};

const classification: ClassificationResult = {
  suggestedCategory: 'Contracts',
  confidence: 0.92,
  alternatives: [
    { category: 'Invoices', confidence: 0.31 },
  ],
  success: true,
  errorMessage: null,
};

describe('mlService response guards', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('returns guarded health status and keeps endpoint path', async () => {
    mockedApi.get.mockResolvedValueOnce(healthStatus);

    await expect(mlService.health()).resolves.toEqual(healthStatus);

    expect(mockedApi.get).toHaveBeenCalledWith('/ml/health');
  });

  it('rejects HTML fallback for health status', async () => {
    mockedApi.get.mockResolvedValueOnce('<!doctype html><html></html>');

    await expect(mlService.health()).rejects.toThrow(ML_UNEXPECTED_RESPONSE_MESSAGE);
  });

  it('rejects malformed health status', async () => {
    mockedApi.get.mockResolvedValueOnce({
      ...healthStatus,
      modelLoaded: 'yes',
    });

    await expect(mlService.health()).rejects.toThrow(ML_UNEXPECTED_RESPONSE_MESSAGE);
  });

  it('returns guarded document classification and keeps endpoint path', async () => {
    mockedApi.post.mockResolvedValueOnce(classification);

    await expect(mlService.classifyDocument('doc-1')).resolves.toEqual(classification);

    expect(mockedApi.post).toHaveBeenCalledWith('/ml/classify/doc-1');
  });

  it('returns guarded text classification and forwards candidates', async () => {
    mockedApi.post.mockResolvedValueOnce(classification);

    await expect(
      mlService.classifyText('A sufficiently long document body for classification', ['Contracts']),
    ).resolves.toEqual(classification);

    expect(mockedApi.post).toHaveBeenCalledWith('/ml/classify', {
      text: 'A sufficiently long document body for classification',
      candidates: ['Contracts'],
    });
  });

  it('accepts failed classification with nullable optional fields', async () => {
    const failed: ClassificationResult = {
      suggestedCategory: null,
      confidence: null,
      alternatives: null,
      success: false,
      errorMessage: 'Document has insufficient text content',
    };
    mockedApi.post.mockResolvedValueOnce(failed);

    await expect(mlService.classifyDocument('doc-2')).resolves.toEqual(failed);
  });

  it('rejects malformed classification success flag', async () => {
    mockedApi.post.mockResolvedValueOnce({
      ...classification,
      success: 'true',
    });

    await expect(mlService.classifyDocument('doc-1')).rejects.toThrow(
      ML_UNEXPECTED_RESPONSE_MESSAGE,
    );
  });

  it('rejects malformed classification alternatives', async () => {
    mockedApi.post.mockResolvedValueOnce({
      ...classification,
      alternatives: [{ category: 'Contracts', confidence: '0.9' }],
    });

    await expect(mlService.classifyDocument('doc-1')).rejects.toThrow(
      ML_UNEXPECTED_RESPONSE_MESSAGE,
    );
  });

  it('returns guarded document tag suggestions and forwards maxTags', async () => {
    mockedApi.get.mockResolvedValueOnce(['finance', 'contract']);

    await expect(mlService.suggestTagsForDocument('doc-1', 3)).resolves.toEqual([
      'finance',
      'contract',
    ]);

    expect(mockedApi.get).toHaveBeenCalledWith('/ml/suggest-tags/doc-1', {
      params: { maxTags: 3 },
    });
  });

  it('returns guarded text tag suggestions and forwards payload', async () => {
    mockedApi.post.mockResolvedValueOnce(['finance']);

    await expect(mlService.suggestTags('invoice text', 1)).resolves.toEqual(['finance']);

    expect(mockedApi.post).toHaveBeenCalledWith('/ml/suggest-tags', {
      text: 'invoice text',
      maxTags: 1,
    });
  });

  it('rejects HTML fallback for tag suggestions', async () => {
    mockedApi.post.mockResolvedValueOnce('<!doctype html><html></html>');

    await expect(mlService.suggestTags('invoice text')).rejects.toThrow(
      ML_UNEXPECTED_RESPONSE_MESSAGE,
    );
  });

  it('rejects malformed tag suggestion entries', async () => {
    mockedApi.get.mockResolvedValueOnce(['finance', 42]);

    await expect(mlService.suggestTagsForDocument('doc-1')).rejects.toThrow(
      ML_UNEXPECTED_RESPONSE_MESSAGE,
    );
  });
});
