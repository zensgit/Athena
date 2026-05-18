import api from './api';
import previewDiagnosticsService, {
  PREVIEW_DIAGNOSTICS_UNEXPECTED_RESPONSE_MESSAGE,
  PreviewCadFailoverDiagnostics,
  PreviewDeadLetterClearBatchResult,
  PreviewDeadLetterDiagnostics,
  PreviewDeadLetterReplayBatchResult,
  PreviewFailureLedgerDiagnostics,
  PreviewFailureLedgerResetBatchResult,
  PreviewFailureLedgerResetByFilterResult,
  PreviewFailureLedgerResetItem,
  PreviewFailurePolicy,
  PreviewFailureSample,
  PreviewFailureSummary,
  PreviewQueueBatchResult,
  PreviewQueueDeclinedClearResult,
  PreviewQueueDeclinedRequeueDryRunResult,
  PreviewQueueDeclinedRequeueResult,
  PreviewQueueDeclinedSummary,
  PreviewQueueDiagnosticsSummary,
  PreviewReasonBatchQueueResult,
  PreviewRenditionPreventionAction,
  PreviewRenditionPreventionBatchResult,
  PreviewRenditionPreventionDiagnostics,
  PreviewRenditionSummary,
  PreviewTransformTrace,
} from './previewDiagnosticsService';

jest.mock('./api', () => ({
  __esModule: true,
  default: {
    get: jest.fn(),
    post: jest.fn(),
    put: jest.fn(),
  },
}));

const mockedApi = api as jest.Mocked<typeof api>;

const failureSample: PreviewFailureSample = {
  id: 'doc-1',
  name: 'file.pdf',
  path: '/Sites/preview/file.pdf',
  mimeType: 'application/pdf',
  previewStatus: 'FAILED',
  previewFailureReason: 'parser-timeout',
  previewFailureCategory: 'TEMPORARY',
  previewLastUpdated: '2026-05-17T08:00:00Z',
};

const failureSummary: PreviewFailureSummary = {
  totalFailures: 12,
  sampledFailures: 12,
  sampleLimit: 500,
  windowDays: 7,
  windowStart: '2026-05-10T00:00:00Z',
  sampleTruncated: false,
  confidenceLevel: 'HIGH',
  confidenceReason: 'sample_complete',
  statusCounts: [{ status: 'FAILED', count: 12 }],
  categoryCounts: [{ category: 'TEMPORARY', retryable: true, count: 12 }],
  topReasons: [
    { reason: 'parser-timeout', category: 'TEMPORARY', retryable: true, count: 12 },
  ],
};

const ledger: PreviewFailureLedgerDiagnostics = {
  totalEntries: 1,
  sampledEntries: 1,
  limit: 100,
  windowDays: 30,
  windowStart: '2026-04-17T00:00:00Z',
  sampleTruncated: false,
  items: [
    {
      documentId: 'doc-1',
      name: 'file.pdf',
      path: '/Sites/preview/file.pdf',
      mimeType: 'application/pdf',
      previewStatus: 'FAILED',
      failureCount: 3,
      failedAt: '2026-05-17T08:00:00Z',
      lastReason: 'parser-timeout',
      category: 'TEMPORARY',
      retryable: true,
      previewLastUpdated: '2026-05-17T08:00:00Z',
      failureContentHash: 'abc',
      currentContentHash: 'def',
      staleByContentChange: true,
    },
  ],
};

const resetItem: PreviewFailureLedgerResetItem = {
  documentId: 'doc-1',
  name: 'file.pdf',
  previousFailureCount: 3,
  previousFailedAt: '2026-05-17T08:00:00Z',
  previousReason: 'parser-timeout',
  outcome: 'RESET',
  message: null,
};

const resetBatch: PreviewFailureLedgerResetBatchResult = {
  requested: 1,
  deduplicated: 1,
  reset: 1,
  failed: 0,
  results: [resetItem],
};

const resetByFilter: PreviewFailureLedgerResetByFilterResult = {
  reason: 'parser-timeout',
  category: 'TEMPORARY',
  retryable: true,
  windowDays: 7,
  maxDocuments: 100,
  totalCandidates: 1,
  scanned: 1,
  matched: 1,
  truncated: false,
  reset: 1,
  skipped: 0,
  failed: 0,
  results: [resetItem],
};

const renditionSummary: PreviewRenditionSummary = {
  totalResources: 5,
  sampledResources: 5,
  sampleLimit: 500,
  windowDays: 7,
  windowStart: '2026-05-10T00:00:00Z',
  sampleTruncated: false,
  statusCounts: [{ status: 'FAILED', count: 5 }],
  topReasons: [{ reason: 'parser-timeout', count: 5 }],
};

const renditionResourcesWrapped = {
  totalResources: 1,
  sampledResources: 1,
  limit: 500,
  windowDays: 7,
  windowStart: '2026-05-10T00:00:00Z',
  sampleTruncated: false,
  items: [
    {
      documentId: 'doc-1',
      name: 'file.pdf',
      path: '/Sites/preview/file.pdf',
      mimeType: 'application/pdf',
      previewStatus: 'FAILED',
      renditionStatus: 'FAILED',
      previewFailureReason: 'parser-timeout',
      previewFailureCategory: 'TEMPORARY',
      previewLastUpdated: '2026-05-17T08:00:00Z',
    },
  ],
};

const queueBatch: PreviewQueueBatchResult = {
  requested: 1,
  deduplicated: 1,
  queued: 1,
  skipped: 0,
  failed: 0,
  results: [
    {
      documentId: 'doc-1',
      outcome: 'QUEUED',
      message: null,
      previewStatus: 'PENDING',
      previewFailureReason: null,
      previewFailureCategory: null,
      previewLastUpdated: null,
      attempts: 1,
      nextAttemptAt: '2026-05-17T08:05:00Z',
    },
  ],
};

const queueDiagnosticsSummary: PreviewQueueDiagnosticsSummary = {
  backend: 'jdbc',
  queueEnabled: true,
  scheduledCount: 1,
  governanceCount: 0,
  runningCount: 0,
  runningCountAccurate: true,
  cancellationRequestedCount: 0,
  sampleLimit: 20,
  sampleTruncated: false,
  stateFilter: 'ALL',
  queryFilter: null,
  totalSampledItems: 1,
  filteredSampledItems: 1,
  items: [
    {
      documentId: 'doc-1',
      name: 'file.pdf',
      path: '/Sites/preview/file.pdf',
      mimeType: 'application/pdf',
      previewStatus: 'PENDING',
      previewFailureReason: null,
      previewFailureCategory: null,
      previewLastUpdated: null,
      queueState: 'QUEUED',
      governanceKey: null,
      attempts: 1,
      nextAttemptAt: '2026-05-17T08:05:00Z',
      running: false,
      cancelRequested: false,
    },
  ],
};

const queueDeclinedSummary: PreviewQueueDeclinedSummary = {
  queueEnabled: true,
  totalDeclined: 1,
  sampleLimit: 50,
  sampleTruncated: false,
  categoryFilter: 'ANY',
  forceRequiredFilter: 'ANY',
  windowHoursFilter: null,
  queryFilter: null,
  forceRequiredCount: 0,
  categoryCounts: [{ category: 'TEMPORARY', count: 1, forceRequiredCount: 0 }],
  totalSampledItems: 1,
  filteredSampledItems: 1,
  items: [
    {
      documentId: 'doc-1',
      name: 'file.pdf',
      path: '/Sites/preview/file.pdf',
      mimeType: 'application/pdf',
      previewStatus: 'DECLINED',
      previewFailureReason: 'parser-timeout',
      previewFailureCategory: 'TEMPORARY',
      previewLastUpdated: '2026-05-17T08:00:00Z',
      reason: 'parser-timeout',
      category: 'TEMPORARY',
      governanceKey: null,
      declinedAt: '2026-05-17T08:00:00Z',
      nextEligibleAt: null,
      forceRequired: false,
    },
  ],
};

const requeueResult: PreviewQueueDeclinedRequeueResult = {
  categoryFilter: 'ANY',
  forceRequiredFilter: 'ANY',
  windowHoursFilter: null,
  queryFilter: null,
  limit: 200,
  force: true,
  requested: 1,
  queued: 1,
  skipped: 0,
  failed: 0,
  results: [
    {
      documentId: 'doc-1',
      category: 'TEMPORARY',
      outcome: 'QUEUED',
      message: null,
      previewStatus: 'PENDING',
      previewFailureReason: null,
      previewFailureCategory: null,
      previewLastUpdated: null,
    },
  ],
};

const requeueDryRun: PreviewQueueDeclinedRequeueDryRunResult = {
  categoryFilter: 'ANY',
  forceRequiredFilter: 'ANY',
  windowHoursFilter: null,
  queryFilter: null,
  limit: 200,
  force: true,
  requested: 1,
  estimatedQueued: 1,
  estimatedSkipped: 0,
  estimatedFailed: 0,
  results: [
    {
      documentId: 'doc-1',
      category: 'TEMPORARY',
      outcome: 'QUEUED',
      reasonCode: 'eligible',
      message: null,
      previewStatus: 'PENDING',
      previewFailureReason: null,
      previewFailureCategory: null,
      previewLastUpdated: null,
      nextAttemptAt: null,
      preflightStatus: null,
      preflightSkipReason: null,
      preflightRoute: null,
      preflightPolicyProfile: null,
      preflightPipeline: null,
    },
  ],
  reasonBreakdown: [{ reasonCode: 'eligible', count: 1 }],
};

const clearResult: PreviewQueueDeclinedClearResult = {
  categoryFilter: 'ANY',
  forceRequiredFilter: 'ANY',
  windowHoursFilter: null,
  queryFilter: null,
  limit: 200,
  requested: 1,
  cleared: 1,
  skipped: 0,
  failed: 0,
  results: [
    {
      documentId: 'doc-1',
      category: 'TEMPORARY',
      outcome: 'CLEARED',
      message: null,
    },
  ],
};

const reasonBatch: PreviewReasonBatchQueueResult = {
  reason: 'parser-timeout',
  category: 'TEMPORARY',
  retryable: true,
  windowDays: 7,
  maxDocuments: 100,
  totalByReason: 1,
  scanned: 1,
  matched: 1,
  truncated: false,
  queued: 1,
  skipped: 0,
  failed: 0,
  results: queueBatch.results,
};

const cadFailover: PreviewCadFailoverDiagnostics = {
  cadPreviewEnabled: true,
  configured: true,
  circuitBreakerEnabled: true,
  circuitFailureThreshold: 3,
  circuitOpenMs: 5000,
  halfOpenTrialTimeoutMs: 1000,
  endpoints: ['https://cad.example.com'],
  endpointStats: [
    {
      endpoint: 'https://cad.example.com',
      successCount: 10,
      failureCount: 1,
      lastSuccessAt: '2026-05-17T08:00:00Z',
      lastFailureAt: '2026-05-17T07:00:00Z',
      lastFailureReason: 'timeout',
      consecutiveFailureCount: 0,
      circuitState: 'CLOSED',
      circuitOpenUntil: null,
      lastCircuitOpenedAt: null,
      halfOpenInFlight: false,
    },
  ],
};

const transformTrace: PreviewTransformTrace = {
  requestId: 'req-1',
  documentId: 'doc-1',
  mimeType: 'application/pdf',
  source: 'pdf-pipeline',
  startedAt: '2026-05-17T08:00:00Z',
  finishedAt: '2026-05-17T08:00:05Z',
  status: 'OK',
  retryNeeded: false,
  failureReason: null,
  latestMessage: 'done',
  events: [
    { at: '2026-05-17T08:00:00Z', stage: 'START', message: null },
    { at: '2026-05-17T08:00:05Z', stage: 'END', message: 'ok' },
  ],
};

const policy: PreviewFailurePolicy = {
  key: 'default',
  label: 'Default policy',
  maxAttempts: 5,
  retryDelayMs: 1000,
  backoffMultiplier: 2,
  quietPeriodMs: 60000,
  builtIn: true,
};

const preventionDiagnostics: PreviewRenditionPreventionDiagnostics = {
  enabled: true,
  blockedCount: 1,
  maxBlocked: 1000,
  autoBlockCategories: ['UNSUPPORTED'],
  limit: 50,
  items: [
    {
      documentId: 'doc-1',
      name: 'file.dwg',
      path: '/Sites/preview/file.dwg',
      mimeType: 'application/x-dwg',
      previewStatus: 'BLOCKED',
      category: 'UNSUPPORTED',
      reason: 'no-converter',
      blockedAt: '2026-05-17T08:00:00Z',
      lastHitAt: '2026-05-17T08:00:05Z',
      hitCount: 3,
    },
  ],
};

const preventionAction: PreviewRenditionPreventionAction = {
  documentId: 'doc-1',
  unblocked: true,
  queued: true,
  message: null,
  previewStatus: 'PENDING',
  attempts: 1,
  nextAttemptAt: '2026-05-17T08:05:00Z',
};

const preventionBatch: PreviewRenditionPreventionBatchResult = {
  requested: 1,
  deduplicated: 1,
  unblocked: 1,
  queued: 1,
  failed: 0,
  results: [preventionAction],
};

const deadLetter: PreviewDeadLetterDiagnostics = {
  enabled: true,
  backendMode: 'jdbc',
  redisEnabled: false,
  ttlMs: 86400000,
  itemCount: 1,
  maxEntries: 1000,
  limit: 50,
  items: [
    {
      entryKey: 'entry-1',
      documentId: 'doc-1',
      renditionKey: 'pdf',
      name: 'file.pdf',
      path: '/Sites/preview/file.pdf',
      mimeType: 'application/pdf',
      previewStatus: 'FAILED',
      reason: 'parser-timeout',
      category: 'TEMPORARY',
      policyKey: 'default',
      sourceStage: 'transform',
      failedAt: '2026-05-17T08:00:00Z',
      attempts: 3,
      occurrences: 3,
      lastReplayAt: null,
      replayCount: 0,
    },
  ],
};

const replayBatch: PreviewDeadLetterReplayBatchResult = {
  requested: 1,
  deduplicated: 1,
  queued: 1,
  skipped: 0,
  failed: 0,
  results: queueBatch.results,
};

const clearBatch: PreviewDeadLetterClearBatchResult = {
  requested: 1,
  deduplicated: 1,
  cleared: 1,
  failed: 0,
  results: [
    {
      documentId: 'doc-1',
      entryKey: 'entry-1',
      renditionKey: 'pdf',
      outcome: 'CLEARED',
      message: null,
    },
  ],
};

describe('previewDiagnosticsService core response guards', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  describe('listRecentFailures', () => {
    it('forwards default params and guards the response', async () => {
      mockedApi.get.mockResolvedValueOnce([failureSample]);

      await expect(previewDiagnosticsService.listRecentFailures()).resolves.toEqual([failureSample]);

      expect(mockedApi.get).toHaveBeenCalledWith('/preview/diagnostics/failures', {
        params: { limit: 50, days: 7 },
      });
    });

    it('rejects HTML fallback', async () => {
      mockedApi.get.mockResolvedValueOnce('<!doctype html><html></html>');

      await expect(previewDiagnosticsService.listRecentFailures()).rejects.toThrow(
        PREVIEW_DIAGNOSTICS_UNEXPECTED_RESPONSE_MESSAGE
      );
    });

    it('rejects a malformed item field', async () => {
      mockedApi.get.mockResolvedValueOnce([{ ...failureSample, mimeType: 42 }]);

      await expect(previewDiagnosticsService.listRecentFailures()).rejects.toThrow(
        PREVIEW_DIAGNOSTICS_UNEXPECTED_RESPONSE_MESSAGE
      );
    });
  });

  describe('getFailureSummary', () => {
    it('forwards and guards the response', async () => {
      mockedApi.get.mockResolvedValueOnce(failureSummary);

      await expect(previewDiagnosticsService.getFailureSummary(100, 14)).resolves.toEqual(
        failureSummary
      );
      expect(mockedApi.get).toHaveBeenCalledWith('/preview/diagnostics/failures/summary', {
        params: { sampleLimit: 100, days: 14 },
      });
    });

    it('rejects malformed nested status counts', async () => {
      mockedApi.get.mockResolvedValueOnce({
        ...failureSummary,
        statusCounts: [{ status: 'FAILED', count: Number.NaN }],
      });

      await expect(previewDiagnosticsService.getFailureSummary()).rejects.toThrow(
        PREVIEW_DIAGNOSTICS_UNEXPECTED_RESPONSE_MESSAGE
      );
    });
  });

  describe('getFailureLedger', () => {
    it('forwards and guards the response', async () => {
      mockedApi.get.mockResolvedValueOnce(ledger);

      await expect(previewDiagnosticsService.getFailureLedger()).resolves.toEqual(ledger);
      expect(mockedApi.get).toHaveBeenCalledWith('/preview/diagnostics/failures/ledger', {
        params: { limit: 100, days: 30 },
      });
    });

    it('rejects malformed ledger item booleans', async () => {
      mockedApi.get.mockResolvedValueOnce({
        ...ledger,
        items: [{ ...ledger.items[0], staleByContentChange: 'true' }],
      });

      await expect(previewDiagnosticsService.getFailureLedger()).rejects.toThrow(
        PREVIEW_DIAGNOSTICS_UNEXPECTED_RESPONSE_MESSAGE
      );
    });
  });

  describe('resetFailureLedger', () => {
    it('posts to the encoded path and guards the response', async () => {
      mockedApi.post.mockResolvedValueOnce(resetItem);

      await expect(previewDiagnosticsService.resetFailureLedger('doc/1')).resolves.toEqual(
        resetItem
      );
      expect(mockedApi.post).toHaveBeenCalledWith(
        '/preview/diagnostics/failures/ledger/doc%2F1/reset'
      );
    });

    it('rejects missing outcome field', async () => {
      const { outcome: _outcome, ...rest } = resetItem;
      mockedApi.post.mockResolvedValueOnce(rest);

      await expect(previewDiagnosticsService.resetFailureLedger('doc-1')).rejects.toThrow(
        PREVIEW_DIAGNOSTICS_UNEXPECTED_RESPONSE_MESSAGE
      );
    });
  });

  describe('resetFailureLedgerBatch', () => {
    it('posts the documentIds payload and guards the response', async () => {
      mockedApi.post.mockResolvedValueOnce(resetBatch);

      await expect(
        previewDiagnosticsService.resetFailureLedgerBatch(['doc-1'])
      ).resolves.toEqual(resetBatch);
      expect(mockedApi.post).toHaveBeenCalledWith(
        '/preview/diagnostics/failures/ledger/reset-batch',
        { documentIds: ['doc-1'] }
      );
    });

    it('rejects malformed nested reset items', async () => {
      mockedApi.post.mockResolvedValueOnce({
        ...resetBatch,
        results: [{ ...resetItem, previousFailureCount: 'three' }],
      });

      await expect(
        previewDiagnosticsService.resetFailureLedgerBatch(['doc-1'])
      ).rejects.toThrow(PREVIEW_DIAGNOSTICS_UNEXPECTED_RESPONSE_MESSAGE);
    });
  });

  describe('resetFailureLedgerByFilter', () => {
    it('forwards the filter payload and guards the response', async () => {
      mockedApi.post.mockResolvedValueOnce(resetByFilter);

      const payload = { reason: 'parser-timeout', retryable: true };
      await expect(
        previewDiagnosticsService.resetFailureLedgerByFilter(payload)
      ).resolves.toEqual(resetByFilter);
      expect(mockedApi.post).toHaveBeenCalledWith(
        '/preview/diagnostics/failures/ledger/reset-by-filter',
        payload
      );
    });

    it('allows nullable retryable on the wire', async () => {
      mockedApi.post.mockResolvedValueOnce({ ...resetByFilter, retryable: null });

      await expect(
        previewDiagnosticsService.resetFailureLedgerByFilter({ reason: 'parser-timeout' })
      ).resolves.toMatchObject({ retryable: null });
    });

    it('rejects non-boolean truncated flag', async () => {
      mockedApi.post.mockResolvedValueOnce({ ...resetByFilter, truncated: 'no' });

      await expect(
        previewDiagnosticsService.resetFailureLedgerByFilter({ reason: 'parser-timeout' })
      ).rejects.toThrow(PREVIEW_DIAGNOSTICS_UNEXPECTED_RESPONSE_MESSAGE);
    });
  });

  describe('getRenditionSummary', () => {
    it('forwards and guards the response', async () => {
      mockedApi.get.mockResolvedValueOnce(renditionSummary);

      await expect(previewDiagnosticsService.getRenditionSummary()).resolves.toEqual(
        renditionSummary
      );
      expect(mockedApi.get).toHaveBeenCalledWith('/preview/diagnostics/renditions/summary', {
        params: { days: 7, sampleLimit: 500 },
      });
    });

    it('rejects malformed top reason count', async () => {
      mockedApi.get.mockResolvedValueOnce({
        ...renditionSummary,
        topReasons: [{ reason: 'parser-timeout', count: 'lots' }],
      });

      await expect(previewDiagnosticsService.getRenditionSummary()).rejects.toThrow(
        PREVIEW_DIAGNOSTICS_UNEXPECTED_RESPONSE_MESSAGE
      );
    });
  });

  describe('getRenditionResources', () => {
    it('accepts the wrapped diagnostics shape and normalizes items', async () => {
      mockedApi.get.mockResolvedValueOnce(renditionResourcesWrapped);

      const result = await previewDiagnosticsService.getRenditionResources();
      expect(result).toHaveLength(1);
      expect(result[0]).toMatchObject({
        documentId: 'doc-1',
        status: 'FAILED',
        reason: 'parser-timeout',
        category: 'TEMPORARY',
      });
    });

    it('accepts a plain array shape', async () => {
      mockedApi.get.mockResolvedValueOnce(renditionResourcesWrapped.items);

      const result = await previewDiagnosticsService.getRenditionResources();
      expect(result).toHaveLength(1);
      expect(result[0].documentId).toBe('doc-1');
    });

    it('rejects HTML fallback', async () => {
      mockedApi.get.mockResolvedValueOnce('<!doctype html><html></html>');

      await expect(previewDiagnosticsService.getRenditionResources()).rejects.toThrow(
        PREVIEW_DIAGNOSTICS_UNEXPECTED_RESPONSE_MESSAGE
      );
    });

    it('rejects malformed nested item fields', async () => {
      mockedApi.get.mockResolvedValueOnce({
        ...renditionResourcesWrapped,
        items: [{ ...renditionResourcesWrapped.items[0], previewStatus: 42 }],
      });

      await expect(previewDiagnosticsService.getRenditionResources()).rejects.toThrow(
        PREVIEW_DIAGNOSTICS_UNEXPECTED_RESPONSE_MESSAGE
      );
    });
  });

  describe('queueFailuresBatch', () => {
    it('forwards the payload and guards the response', async () => {
      mockedApi.post.mockResolvedValueOnce(queueBatch);

      await expect(
        previewDiagnosticsService.queueFailuresBatch(['doc-1'], true)
      ).resolves.toEqual(queueBatch);
      expect(mockedApi.post).toHaveBeenCalledWith('/preview/diagnostics/failures/queue-batch', {
        documentIds: ['doc-1'],
        force: true,
      });
    });

    it('rejects malformed queue batch item (numeric field as string)', async () => {
      mockedApi.post.mockResolvedValueOnce({
        ...queueBatch,
        results: [{ ...queueBatch.results[0], attempts: '2' }],
      });

      await expect(
        previewDiagnosticsService.queueFailuresBatch(['doc-1'])
      ).rejects.toThrow(PREVIEW_DIAGNOSTICS_UNEXPECTED_RESPONSE_MESSAGE);
    });
  });

  describe('getQueueDiagnosticsSummary', () => {
    it('forwards default params and guards the response', async () => {
      mockedApi.get.mockResolvedValueOnce(queueDiagnosticsSummary);

      await expect(
        previewDiagnosticsService.getQueueDiagnosticsSummary()
      ).resolves.toEqual(queueDiagnosticsSummary);
      expect(mockedApi.get).toHaveBeenCalledWith('/preview/diagnostics/queue/summary', {
        params: { limit: 20, state: 'ALL', query: undefined },
      });
    });

    it('trims query parameter', async () => {
      mockedApi.get.mockResolvedValueOnce(queueDiagnosticsSummary);

      await previewDiagnosticsService.getQueueDiagnosticsSummary(50, 'QUEUED', '  doc-1  ');
      expect(mockedApi.get).toHaveBeenCalledWith('/preview/diagnostics/queue/summary', {
        params: { limit: 50, state: 'QUEUED', query: 'doc-1' },
      });
    });

    it('rejects malformed item boolean', async () => {
      mockedApi.get.mockResolvedValueOnce({
        ...queueDiagnosticsSummary,
        items: [{ ...queueDiagnosticsSummary.items[0], running: 'yes' }],
      });

      await expect(
        previewDiagnosticsService.getQueueDiagnosticsSummary()
      ).rejects.toThrow(PREVIEW_DIAGNOSTICS_UNEXPECTED_RESPONSE_MESSAGE);
    });
  });

  describe('getQueueDeclinedSummary', () => {
    it('forwards default params and guards the response (3-arg overload)', async () => {
      mockedApi.get.mockResolvedValueOnce(queueDeclinedSummary);

      await expect(
        previewDiagnosticsService.getQueueDeclinedSummary(25, 'ANY', 'doc')
      ).resolves.toEqual(queueDeclinedSummary);
      expect(mockedApi.get).toHaveBeenCalledWith('/preview/diagnostics/queue/declined', {
        params: {
          limit: 25,
          category: 'ANY',
          forceRequired: 'ANY',
          windowHours: undefined,
          query: 'doc',
        },
      });
    });

    it('forwards the 5-arg overload with forceRequired and windowHours', async () => {
      mockedApi.get.mockResolvedValueOnce(queueDeclinedSummary);

      await previewDiagnosticsService.getQueueDeclinedSummary(50, 'TEMPORARY', 'YES', 'q', 24);
      expect(mockedApi.get).toHaveBeenCalledWith('/preview/diagnostics/queue/declined', {
        params: {
          limit: 50,
          category: 'TEMPORARY',
          forceRequired: 'YES',
          windowHours: 24,
          query: 'q',
        },
      });
    });

    it('rejects malformed category count', async () => {
      mockedApi.get.mockResolvedValueOnce({
        ...queueDeclinedSummary,
        categoryCounts: [{ category: 'TEMPORARY', count: 1, forceRequiredCount: 'zero' }],
      });

      await expect(
        previewDiagnosticsService.getQueueDeclinedSummary()
      ).rejects.toThrow(PREVIEW_DIAGNOSTICS_UNEXPECTED_RESPONSE_MESSAGE);
    });
  });

  describe('requeueQueueDeclined', () => {
    it('forwards default params and guards the response', async () => {
      mockedApi.post.mockResolvedValueOnce(requeueResult);

      await expect(previewDiagnosticsService.requeueQueueDeclined()).resolves.toEqual(
        requeueResult
      );
      expect(mockedApi.post).toHaveBeenCalledWith(
        '/preview/diagnostics/queue/declined/requeue',
        undefined,
        {
          params: {
            limit: 200,
            category: 'ANY',
            forceRequired: 'ANY',
            windowHours: undefined,
            query: undefined,
            force: true,
          },
        }
      );
    });

    it('rejects malformed requeue item', async () => {
      mockedApi.post.mockResolvedValueOnce({
        ...requeueResult,
        results: [{ ...requeueResult.results[0], outcome: 42 }],
      });

      await expect(previewDiagnosticsService.requeueQueueDeclined()).rejects.toThrow(
        PREVIEW_DIAGNOSTICS_UNEXPECTED_RESPONSE_MESSAGE
      );
    });
  });

  describe('dryRunQueueDeclinedRequeue', () => {
    it('forwards default params and guards the response', async () => {
      mockedApi.post.mockResolvedValueOnce(requeueDryRun);

      await expect(
        previewDiagnosticsService.dryRunQueueDeclinedRequeue()
      ).resolves.toEqual(requeueDryRun);
      expect(mockedApi.post).toHaveBeenCalledWith(
        '/preview/diagnostics/queue/declined/requeue/dry-run',
        undefined,
        expect.objectContaining({
          params: expect.objectContaining({
            limit: 200,
            force: true,
          }),
        })
      );
    });

    it('rejects malformed reason breakdown', async () => {
      mockedApi.post.mockResolvedValueOnce({
        ...requeueDryRun,
        reasonBreakdown: [{ reasonCode: 'eligible', count: 'one' }],
      });

      await expect(
        previewDiagnosticsService.dryRunQueueDeclinedRequeue()
      ).rejects.toThrow(PREVIEW_DIAGNOSTICS_UNEXPECTED_RESPONSE_MESSAGE);
    });
  });

  describe('clearQueueDeclined', () => {
    it('forwards default params and guards the response', async () => {
      mockedApi.post.mockResolvedValueOnce(clearResult);

      await expect(previewDiagnosticsService.clearQueueDeclined()).resolves.toEqual(clearResult);
      expect(mockedApi.post).toHaveBeenCalledWith(
        '/preview/diagnostics/queue/declined/clear',
        undefined,
        expect.objectContaining({
          params: expect.objectContaining({
            limit: 200,
            category: 'ANY',
            forceRequired: 'ANY',
          }),
        })
      );
    });

    it('rejects malformed cleared count', async () => {
      mockedApi.post.mockResolvedValueOnce({ ...clearResult, cleared: 'one' });

      await expect(previewDiagnosticsService.clearQueueDeclined()).rejects.toThrow(
        PREVIEW_DIAGNOSTICS_UNEXPECTED_RESPONSE_MESSAGE
      );
    });
  });

  describe('queueFailuresByReason', () => {
    it('forwards the payload and guards the response', async () => {
      mockedApi.post.mockResolvedValueOnce(reasonBatch);

      const payload = { reason: 'parser-timeout' };
      await expect(
        previewDiagnosticsService.queueFailuresByReason(payload)
      ).resolves.toEqual(reasonBatch);
      expect(mockedApi.post).toHaveBeenCalledWith(
        '/preview/diagnostics/failures/queue-by-reason',
        payload
      );
    });

    it('rejects malformed nullable retryable', async () => {
      mockedApi.post.mockResolvedValueOnce({ ...reasonBatch, retryable: 'maybe' });

      await expect(
        previewDiagnosticsService.queueFailuresByReason({ reason: 'parser-timeout' })
      ).rejects.toThrow(PREVIEW_DIAGNOSTICS_UNEXPECTED_RESPONSE_MESSAGE);
    });
  });

  describe('getCadFailoverDiagnostics', () => {
    it('forwards and guards the response', async () => {
      mockedApi.get.mockResolvedValueOnce(cadFailover);

      await expect(previewDiagnosticsService.getCadFailoverDiagnostics()).resolves.toEqual(
        cadFailover
      );
      expect(mockedApi.get).toHaveBeenCalledWith('/preview/diagnostics/cad-failover');
    });

    it('rejects non-string entry in endpoints array', async () => {
      mockedApi.get.mockResolvedValueOnce({ ...cadFailover, endpoints: ['ok', 42] });

      await expect(previewDiagnosticsService.getCadFailoverDiagnostics()).rejects.toThrow(
        PREVIEW_DIAGNOSTICS_UNEXPECTED_RESPONSE_MESSAGE
      );
    });
  });

  describe('getTransformTraces', () => {
    it('forwards params and guards the response', async () => {
      mockedApi.get.mockResolvedValueOnce([transformTrace]);

      await expect(previewDiagnosticsService.getTransformTraces(10, 'req-1')).resolves.toEqual([
        transformTrace,
      ]);
      expect(mockedApi.get).toHaveBeenCalledWith('/preview/diagnostics/traces', {
        params: { limit: 10, requestId: 'req-1' },
      });
    });

    it('rejects malformed nested event', async () => {
      mockedApi.get.mockResolvedValueOnce([
        { ...transformTrace, events: [{ at: '2026-05-17T08:00:00Z', stage: 42, message: null }] },
      ]);

      await expect(previewDiagnosticsService.getTransformTraces()).rejects.toThrow(
        PREVIEW_DIAGNOSTICS_UNEXPECTED_RESPONSE_MESSAGE
      );
    });
  });

  describe('getFailurePolicies', () => {
    it('forwards and guards the response', async () => {
      mockedApi.get.mockResolvedValueOnce([policy]);

      await expect(previewDiagnosticsService.getFailurePolicies()).resolves.toEqual([policy]);
      expect(mockedApi.get).toHaveBeenCalledWith('/preview/diagnostics/policies');
    });

    it('rejects malformed builtIn flag', async () => {
      mockedApi.get.mockResolvedValueOnce([{ ...policy, builtIn: 'true' }]);

      await expect(previewDiagnosticsService.getFailurePolicies()).rejects.toThrow(
        PREVIEW_DIAGNOSTICS_UNEXPECTED_RESPONSE_MESSAGE
      );
    });
  });

  describe('updateFailurePolicy', () => {
    it('puts to the encoded path and guards the response', async () => {
      mockedApi.put.mockResolvedValueOnce(policy);

      await expect(
        previewDiagnosticsService.updateFailurePolicy('my/profile', { maxAttempts: 7 })
      ).resolves.toEqual(policy);
      expect(mockedApi.put).toHaveBeenCalledWith(
        '/preview/diagnostics/policies/my%2Fprofile',
        { maxAttempts: 7 }
      );
    });

    it('rejects malformed policy response (null required number)', async () => {
      mockedApi.put.mockResolvedValueOnce({ ...policy, maxAttempts: null });

      await expect(
        previewDiagnosticsService.updateFailurePolicy('default', { maxAttempts: 5 })
      ).rejects.toThrow(PREVIEW_DIAGNOSTICS_UNEXPECTED_RESPONSE_MESSAGE);
    });

    it('rejects HTML fallback from updateFailurePolicy (shape that previously slipped through mocked CI)', async () => {
      mockedApi.put.mockResolvedValueOnce('<!doctype html><html></html>');

      await expect(
        previewDiagnosticsService.updateFailurePolicy('default', {})
      ).rejects.toThrow(PREVIEW_DIAGNOSTICS_UNEXPECTED_RESPONSE_MESSAGE);
    });
  });

  describe('getRenditionPreventionBlocked', () => {
    it('forwards and guards the response', async () => {
      mockedApi.get.mockResolvedValueOnce(preventionDiagnostics);

      await expect(
        previewDiagnosticsService.getRenditionPreventionBlocked(75)
      ).resolves.toEqual(preventionDiagnostics);
      expect(mockedApi.get).toHaveBeenCalledWith('/preview/diagnostics/prevention/blocked', {
        params: { limit: 75 },
      });
    });

    it('rejects non-string auto-block category', async () => {
      mockedApi.get.mockResolvedValueOnce({
        ...preventionDiagnostics,
        autoBlockCategories: ['UNSUPPORTED', 42],
      });

      await expect(
        previewDiagnosticsService.getRenditionPreventionBlocked()
      ).rejects.toThrow(PREVIEW_DIAGNOSTICS_UNEXPECTED_RESPONSE_MESSAGE);
    });
  });

  describe('unblockRenditionPrevention', () => {
    it('posts to the encoded path and guards the response', async () => {
      mockedApi.post.mockResolvedValueOnce(preventionAction);

      await expect(
        previewDiagnosticsService.unblockRenditionPrevention('doc/1')
      ).resolves.toEqual(preventionAction);
      expect(mockedApi.post).toHaveBeenCalledWith('/preview/diagnostics/prevention/doc%2F1/unblock');
    });

    it('rejects malformed unblocked boolean (string slipping through mock)', async () => {
      mockedApi.post.mockResolvedValueOnce({ ...preventionAction, unblocked: 'true' });

      await expect(
        previewDiagnosticsService.unblockRenditionPrevention('doc-1')
      ).rejects.toThrow(PREVIEW_DIAGNOSTICS_UNEXPECTED_RESPONSE_MESSAGE);
    });
  });

  describe('unblockAndRequeueRendition', () => {
    it('posts with force param and guards the response', async () => {
      mockedApi.post.mockResolvedValueOnce(preventionAction);

      await expect(
        previewDiagnosticsService.unblockAndRequeueRendition('doc-1', false)
      ).resolves.toEqual(preventionAction);
      expect(mockedApi.post).toHaveBeenCalledWith(
        '/preview/diagnostics/prevention/doc-1/unblock-requeue',
        undefined,
        { params: { force: false } }
      );
    });
  });

  describe('unblockRenditionPreventionBatch', () => {
    it('posts payload and guards the response', async () => {
      mockedApi.post.mockResolvedValueOnce(preventionBatch);

      await expect(
        previewDiagnosticsService.unblockRenditionPreventionBatch(['doc-1'])
      ).resolves.toEqual(preventionBatch);
      expect(mockedApi.post).toHaveBeenCalledWith(
        '/preview/diagnostics/prevention/unblock-batch',
        { documentIds: ['doc-1'] }
      );
    });

    it('rejects malformed batch deduplicated count', async () => {
      mockedApi.post.mockResolvedValueOnce({ ...preventionBatch, deduplicated: Number.NaN });

      await expect(
        previewDiagnosticsService.unblockRenditionPreventionBatch(['doc-1'])
      ).rejects.toThrow(PREVIEW_DIAGNOSTICS_UNEXPECTED_RESPONSE_MESSAGE);
    });
  });

  describe('unblockAndRequeueRenditionBatch', () => {
    it('posts payload with default force=true and guards the response', async () => {
      mockedApi.post.mockResolvedValueOnce(preventionBatch);

      await expect(
        previewDiagnosticsService.unblockAndRequeueRenditionBatch(['doc-1'])
      ).resolves.toEqual(preventionBatch);
      expect(mockedApi.post).toHaveBeenCalledWith(
        '/preview/diagnostics/prevention/unblock-requeue-batch',
        { documentIds: ['doc-1'], force: true }
      );
    });
  });

  describe('getDeadLetter', () => {
    it('forwards default limit and guards the response', async () => {
      mockedApi.get.mockResolvedValueOnce(deadLetter);

      await expect(previewDiagnosticsService.getDeadLetter()).resolves.toEqual(deadLetter);
      expect(mockedApi.get).toHaveBeenCalledWith('/preview/diagnostics/dead-letter', {
        params: { limit: 50 },
      });
    });

    it('rejects malformed nested dead-letter item attempts', async () => {
      mockedApi.get.mockResolvedValueOnce({
        ...deadLetter,
        items: [{ ...deadLetter.items[0], attempts: 'three' }],
      });

      await expect(previewDiagnosticsService.getDeadLetter()).rejects.toThrow(
        PREVIEW_DIAGNOSTICS_UNEXPECTED_RESPONSE_MESSAGE
      );
    });
  });

  describe('replayDeadLetterBatch', () => {
    it('merges defaults into payload and guards the response', async () => {
      mockedApi.post.mockResolvedValueOnce(replayBatch);

      await expect(
        previewDiagnosticsService.replayDeadLetterBatch({ documentIds: ['doc-1'] })
      ).resolves.toEqual(replayBatch);
      expect(mockedApi.post).toHaveBeenCalledWith(
        '/preview/diagnostics/dead-letter/replay-batch',
        { documentIds: ['doc-1'], entryKeys: [], force: true }
      );
    });

    it('honors payload.force over default', async () => {
      mockedApi.post.mockResolvedValueOnce(replayBatch);

      await previewDiagnosticsService.replayDeadLetterBatch({
        entryKeys: ['key-1'],
        force: false,
      });
      expect(mockedApi.post).toHaveBeenCalledWith(
        '/preview/diagnostics/dead-letter/replay-batch',
        { documentIds: [], entryKeys: ['key-1'], force: false }
      );
    });

    it('rejects malformed replay result item', async () => {
      mockedApi.post.mockResolvedValueOnce({
        ...replayBatch,
        results: [{ ...replayBatch.results[0], outcome: 0 }],
      });

      await expect(
        previewDiagnosticsService.replayDeadLetterBatch({ documentIds: ['doc-1'] })
      ).rejects.toThrow(PREVIEW_DIAGNOSTICS_UNEXPECTED_RESPONSE_MESSAGE);
    });
  });

  describe('clearDeadLetterBatch', () => {
    it('merges defaults into payload and guards the response', async () => {
      mockedApi.post.mockResolvedValueOnce(clearBatch);

      await expect(
        previewDiagnosticsService.clearDeadLetterBatch({ entryKeys: ['key-1'] })
      ).resolves.toEqual(clearBatch);
      expect(mockedApi.post).toHaveBeenCalledWith(
        '/preview/diagnostics/dead-letter/clear-batch',
        { documentIds: [], entryKeys: ['key-1'] }
      );
    });

    it('rejects malformed cleared count', async () => {
      mockedApi.post.mockResolvedValueOnce({ ...clearBatch, cleared: Number.POSITIVE_INFINITY });

      await expect(
        previewDiagnosticsService.clearDeadLetterBatch({ entryKeys: ['key-1'] })
      ).rejects.toThrow(PREVIEW_DIAGNOSTICS_UNEXPECTED_RESPONSE_MESSAGE);
    });
  });
});
